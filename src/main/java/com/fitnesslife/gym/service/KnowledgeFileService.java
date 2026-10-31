package com.fitnesslife.gym.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.fitnesslife.gym.config.ChatbotProperties;
import com.fitnesslife.gym.dto.KnowledgeFileResponse;
import com.fitnesslife.gym.dto.KnowledgeImportResult;
import com.fitnesslife.gym.dto.KnowledgeIngestionResult.Item;
import com.fitnesslife.gym.enums.KnowledgeAudience;
import com.fitnesslife.gym.enums.KnowledgeFileStatus;
import com.fitnesslife.gym.enums.KnowledgeStatus;
import com.fitnesslife.gym.exception.ChatException;
import com.fitnesslife.gym.exception.InvalidRequestException;
import com.fitnesslife.gym.model.KnowledgeDocument;
import com.fitnesslife.gym.model.KnowledgeFile;
import com.fitnesslife.gym.repository.KnowledgeDocumentRepository;
import com.fitnesslife.gym.repository.KnowledgeFileRepository;
import com.fitnesslife.gym.service.KnowledgeDocumentParser.ParsedDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeFileService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("md", "txt", "html", "pdf");
    private static final Set<String> AUDIENCE_FOLDERS = Arrays.stream(KnowledgeAudience.values())
            .map(KnowledgeAudience::folderName).collect(Collectors.toUnmodifiableSet());
    private static final Pattern STORED_NAME = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,119}\\.(pdf|md|txt|html)$");
    private static final String FILE_PREFIX = "file:";
    private static final int MAX_BASE_NAME_LENGTH = 80;
    private static final int TEXT_SAMPLE_BYTES = 8192;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final ChatbotProperties properties;
    private final KnowledgeDocumentParser parser;
    private final KnowledgeFileRepository fileRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final MongoTemplate mongo;
    private final RedisLockService lockService;

    @Value("${spring.ai.vectorstore.mongodb.collection-name}")
    private String vectorCollection;

    public List<KnowledgeFileResponse> list() {
        Map<String, KnowledgeDocument> manifest = documentRepository.findAll().stream()
                .collect(Collectors.toMap(KnowledgeDocument::getSourceKey, document -> document, (a, b) -> a));
        List<KnowledgeFileResponse> result = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (KnowledgeFile file : fileRepository.findAllWithoutContent()) {
            String key = FILE_PREFIX + file.getId();
            seenKeys.add(key);
            result.add(describe(file, manifest.get(key)));
        }

        manifest.values().stream()
                .filter(document -> document.getSourceKey().startsWith(FILE_PREFIX)
                        && document.getStatus() == KnowledgeStatus.ACTIVE
                        && !seenKeys.contains(document.getSourceKey()))
                .forEach(document -> {
                    String relative = document.getSourceKey().substring(FILE_PREFIX.length());
                    int slash = relative.indexOf('/');
                    if (slash < 0 || !AUDIENCE_FOLDERS.contains(relative.substring(0, slash))) {
                        return;
                    }
                    result.add(new KnowledgeFileResponse(relative.substring(slash + 1),
                            KnowledgeAudience.valueOf(relative.substring(0, slash).toUpperCase(Locale.ROOT)),
                            0, null, KnowledgeFileStatus.MISSING, document.getTitle(), document.getChunkCount(),
                            document.getIngestedAt(), document.getLastError()));
                });

        result.sort(Comparator.comparingInt((KnowledgeFileResponse file) -> file.audience().ordinal())
                .thenComparing(file -> file.fileName().toLowerCase(Locale.ROOT)));
        return result;
    }

    public KnowledgeFileResponse upload(MultipartFile file, KnowledgeAudience audience, boolean overwrite,
            String adminEmail) {
        long maxBytes = properties.ingestion().maxFileBytes();
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Selecciona un archivo");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidRequestException(
                    "El archivo supera el tamaño máximo permitido (" + (maxBytes / (1024 * 1024)) + " MB)");
        }

        String storedName = storedNameFor(file.getOriginalFilename());
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            log.error("No se pudo leer el archivo recibido ({})", e.getClass().getSimpleName());
            throw new UncheckedIOException(e);
        }
        validateContent(content, storedName);

        String id = audience.folderName() + "/" + storedName;
        String lockToken = lockService.tryAcquire(KnowledgeIngestionService.LOCK_KEY, Duration.ofMinutes(5))
                .orElseThrow(ChatException::ingestionInProgress);
        try {
            Instant now = Instant.now();
            KnowledgeFile previous = overwrite ? fileRepository.findSummaryById(id).orElse(null) : null;
            KnowledgeFile entity = KnowledgeFile.builder()
                    .id(id).audience(audience.name()).fileName(storedName)
                    .sizeBytes(content.length).sha256(sha256(content)).content(content).uploadedBy(adminEmail)
                    .createdAt(previous != null && previous.getCreatedAt() != null ? previous.getCreatedAt() : now)
                    .updatedAt(now).build();

            if (overwrite) {
                fileRepository.save(entity);
            } else {
                try {
                    fileRepository.insert(entity);
                } catch (DuplicateKeyException e) {
                    throw ChatException.documentExists();
                }
            }

            log.info("Documento '{}' guardado en la audiencia {} por {} (reemplazo: {})",
                    storedName, audience, adminEmail, overwrite);
            return describe(entity, documentRepository.findBySourceKey(FILE_PREFIX + id).orElse(null));
        } finally {
            lockService.release(KnowledgeIngestionService.LOCK_KEY, lockToken);
        }
    }

    public void delete(KnowledgeAudience audience, String fileName, String adminEmail) {
        if (!StringUtils.hasText(fileName) || fileName.length() > 255
                || fileName.contains("/") || fileName.contains("\\")) {
            throw new InvalidRequestException("El nombre del archivo no es válido");
        }
        String id = audience.folderName() + "/" + fileName;
        String key = FILE_PREFIX + id;

        String lockToken = lockService.tryAcquire(KnowledgeIngestionService.LOCK_KEY, Duration.ofMinutes(5))
                .orElseThrow(ChatException::ingestionInProgress);
        try {
            boolean existed = fileRepository.existsById(id);
            fileRepository.deleteById(id);
            mongo.remove(new Query(Criteria.where("metadata.sourceKey").is(key)), vectorCollection);

            KnowledgeDocument document = documentRepository.findBySourceKey(key).orElse(null);
            if (document != null) {
                document.setStatus(KnowledgeStatus.RETIRED);
                document.setChunkCount(0);
                document.setLastError(null);
                document.setUpdatedAt(Instant.now());
                documentRepository.save(document);
            }
            if (!existed && document == null) {
                throw ChatException.documentNotFound();
            }
            log.info("Documento '{}' eliminado de la audiencia {} por {}", fileName, audience, adminEmail);
        } finally {
            lockService.release(KnowledgeIngestionService.LOCK_KEY, lockToken);
        }
    }

    public KnowledgeImportResult importFromDirectory(String adminEmail) {
        String directory = properties.ingestion().directory();
        Path root = Path.of(directory).toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new InvalidRequestException("La carpeta '" + directory + "' no existe en el servidor");
        }

        String lockToken = lockService.tryAcquire(KnowledgeIngestionService.LOCK_KEY, Duration.ofMinutes(10))
                .orElseThrow(ChatException::ingestionInProgress);
        try {
            List<Item> items = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(root, 4)) {
                walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).sorted()
                        .forEach(path -> items.add(importOne(root, path, adminEmail)));
            } catch (IOException e) {
                log.error("No se pudo recorrer la carpeta de conocimiento", e);
                throw new UncheckedIOException(e);
            }
            KnowledgeImportResult result = new KnowledgeImportResult(count(items, "IMPORTED"),
                    count(items, "SKIPPED"), count(items, "FAILED"), items);
            log.info("Importación de la carpeta: {} importados, {} omitidos, {} con error",
                    result.imported(), result.skipped(), result.failed());
            return result;
        } finally {
            lockService.release(KnowledgeIngestionService.LOCK_KEY, lockToken);
        }
    }

    private Item importOne(Path root, Path path, String adminEmail) {
        Path relative = root.relativize(path);
        String shown = FILE_PREFIX + relative.toString().replace('\\', '/');

        if (relative.getNameCount() != 2
                || !AUDIENCE_FOLDERS.contains(relative.getName(0).toString().toLowerCase(Locale.ROOT))) {
            return new Item(shown, "SKIPPED", "Debe estar directamente dentro de all/, trainer/ o admin/");
        }
        String folder = relative.getName(0).toString().toLowerCase(Locale.ROOT);
        String fileName = path.getFileName().toString();
        if (!SUPPORTED_EXTENSIONS.contains(extensionOf(fileName))) {
            return new Item(shown, "SKIPPED", "Formato no soportado");
        }
        if (!STORED_NAME.matcher(fileName).matches()) {
            return new Item(shown, "SKIPPED",
                    "El nombre debe usar solo minúsculas, números, guion y guion bajo. Renómbralo o súbelo desde la pantalla");
        }

        String id = folder + "/" + fileName;
        try {
            if (Files.size(path) > properties.ingestion().maxFileBytes()) {
                return new Item(shown, "FAILED", "El archivo supera el tamaño máximo permitido");
            }
            if (fileRepository.existsById(id)) {
                return new Item(shown, "SKIPPED", "Ya existe en la base de datos");
            }
            byte[] content = Files.readAllBytes(path);
            validateContent(content, fileName);

            Instant now = Instant.now();
            fileRepository.insert(KnowledgeFile.builder()
                    .id(id).audience(folder.toUpperCase(Locale.ROOT)).fileName(fileName)
                    .sizeBytes(content.length).sha256(sha256(content)).content(content).uploadedBy(adminEmail)
                    .createdAt(now).updatedAt(now).build());
            log.info("Documento '{}' importado a la audiencia {} por {}", fileName, folder, adminEmail);
            return new Item(shown, "IMPORTED", "Importado");
        } catch (InvalidRequestException e) {
            return new Item(shown, "FAILED", e.getMessage());
        } catch (DuplicateKeyException e) {
            return new Item(shown, "SKIPPED", "Ya existe en la base de datos");
        } catch (IOException e) {
            return new Item(shown, "FAILED", "No se pudo leer el archivo");
        }
    }

    private KnowledgeFileResponse describe(KnowledgeFile file, KnowledgeDocument document) {
        KnowledgeFileStatus status;
        if (document != null && StringUtils.hasText(document.getLastError())) {
            status = KnowledgeFileStatus.FAILED;
        } else if (document == null || document.getStatus() != KnowledgeStatus.ACTIVE
                || !file.getSha256().equals(document.getSourceSha256())) {
            status = KnowledgeFileStatus.PENDING;
        } else {
            status = KnowledgeFileStatus.INDEXED;
        }

        return new KnowledgeFileResponse(file.getFileName(), KnowledgeAudience.valueOf(file.getAudience()),
                file.getSizeBytes(), file.getUpdatedAt(), status,
                document == null ? null : document.getTitle(),
                document == null ? 0 : document.getChunkCount(),
                document == null ? null : document.getIngestedAt(),
                document == null ? null : document.getLastError());
    }

    private String storedNameFor(String originalName) {
        String name = originalName == null ? "" : originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();

        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new InvalidRequestException("Formato no permitido. Usa PDF, Markdown (.md), texto (.txt) o HTML");
        }

        String base = dot < 0 ? name : name.substring(0, dot);
        String slug = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[-_]+|[-_]+$", "");
        if (slug.length() > MAX_BASE_NAME_LENGTH) {
            slug = slug.substring(0, MAX_BASE_NAME_LENGTH).replaceAll("[-_]+$", "");
        }
        return (slug.isEmpty() ? "documento" : slug) + "." + extension;
    }

    private void validateContent(byte[] content, String storedName) {
        if ("pdf".equals(extensionOf(storedName))) {
            if (content.length < PDF_MAGIC.length
                    || !Arrays.equals(Arrays.copyOf(content, PDF_MAGIC.length), PDF_MAGIC)) {
                throw new InvalidRequestException("El archivo no es un PDF válido");
            }
        } else {
            int limit = Math.min(content.length, TEXT_SAMPLE_BYTES);
            for (int i = 0; i < limit; i++) {
                if (content[i] == 0) {
                    throw new InvalidRequestException("El archivo no es un documento de texto válido");
                }
            }
        }

        ParsedDocument parsed;
        try {
            parsed = parser.parse(storedName, content);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(e.getMessage());
        }
        if (parsed.sections().stream().noneMatch(section -> StringUtils.hasText(section.text()))) {
            throw new InvalidRequestException(
                    "No se pudo extraer texto del documento (¿PDF escaneado? El OCR no está soportado)");
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private String extensionOf(String fileName) {
        return KnowledgeDocumentParser.extensionOf(fileName);
    }

    private int count(List<Item> items, String outcome) {
        return (int) items.stream().filter(item -> outcome.equals(item.outcome())).count();
    }
}