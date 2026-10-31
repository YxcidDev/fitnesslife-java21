package com.fitnesslife.gym.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import com.fitnesslife.gym.config.ChatbotProperties;
import com.fitnesslife.gym.dto.KnowledgeIngestionResult;
import com.fitnesslife.gym.dto.KnowledgeIngestionResult.Item;
import com.fitnesslife.gym.enums.KnowledgeStatus;
import com.fitnesslife.gym.exception.ChatException;
import com.fitnesslife.gym.exception.InvalidRequestException;
import com.fitnesslife.gym.model.KnowledgeDocument;
import com.fitnesslife.gym.model.KnowledgeFile;
import com.fitnesslife.gym.repository.KnowledgeDocumentRepository;
import com.fitnesslife.gym.repository.KnowledgeFileRepository;
import com.fitnesslife.gym.service.KnowledgeDocumentParser.ParsedDocument;
import com.fitnesslife.gym.service.KnowledgeDocumentParser.Section;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestionService {

    private record SourceFile(String key, String id, String fileName, String audience, String sha256) {
    }

    static final String LOCK_KEY = "knowledge:ingestion:lock";
    private static final String FILE_PREFIX = "file:";

    private final ChatbotProperties properties;
    private final KnowledgeDocumentParser parser;
    private final TokenTextSplitter splitter;
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final MongoTemplate mongo;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeFileRepository fileRepository;
    private final RedisLockService lockService;

    @Value("${spring.ai.openai.embedding.options.model}")
    private String embeddingModelId;

    @Value("${spring.ai.vectorstore.mongodb.collection-name}")
    private String vectorCollection;

    private Long lastRequestNanos;
    private volatile boolean dimensionsVerified;

    public KnowledgeIngestionResult ingest(boolean force) {
        String token = lockService.tryAcquire(LOCK_KEY, Duration.ofMinutes(60))
                .orElseThrow(ChatException::ingestionInProgress);
        try {
            assertDimensions();
            return run(force);
        } finally {
            lockService.release(LOCK_KEY, token);
        }
    }

    private void assertDimensions() {
        if (dimensionsVerified) {
            return;
        }
        int actual;
        try {
            awaitRequestSlot();
            actual = embeddingModel.dimensions();
        } catch (TransientAiException e) {
            log.warn("El proveedor de embeddings limitó las solicitudes al verificar la dimensión");
            throw ChatException.providerBusy();
        } catch (RuntimeException e) {
            log.error("No se pudo consultar la dimensión del modelo de embeddings ({})", e.getClass().getSimpleName());
            throw ChatException.providerFailure();
        }
        int expected = properties.rag().dimensions();
        if (actual != expected) {
            throw new InvalidRequestException("El modelo de embeddings produce vectores de " + actual
                    + " dimensiones, pero chatbot.rag.dimensions y el índice vectorial esperan " + expected);
        }
        dimensionsVerified = true;
    }

    private KnowledgeIngestionResult run(boolean force) {
        List<Item> items = new ArrayList<>();
        Set<String> discoveredKeys = new HashSet<>();

        List<SourceFile> sources = collectSources(items, discoveredKeys);
        log.info("Ingesta iniciada: {} fuentes encontradas (force={})", sources.size(), force);

        boolean stopped = false;
        for (SourceFile source : sources) {
            if (stopped) {
                items.add(new Item(source.key(), "PENDING",
                        "Pendiente: la ingesta se detuvo por el límite de solicitudes del proveedor. Vuelve a ejecutarla."));
                continue;
            }
            Item item = processSource(source, force);
            items.add(item);
            if ("RATE_LIMITED".equals(item.outcome())) {
                stopped = true;
                log.warn("Ingesta detenida: el proveedor de embeddings sigue limitando las solicitudes");
            }
        }
        retireMissing(discoveredKeys, items);

        KnowledgeIngestionResult result = new KnowledgeIngestionResult(
                count(items, "INGESTED"),
                count(items, "SKIPPED_UNCHANGED") + count(items, "SKIPPED_IGNORED"),
                count(items, "FAILED") + count(items, "RATE_LIMITED") + count(items, "PENDING"),
                count(items, "RETIRED"),
                items);
        log.info("Ingesta terminada: {} procesados, {} omitidos, {} con error o pendientes, {} retirados",
                result.processed(), result.skipped(), result.failed(), result.retired());
        return result;
    }

    /** Lists the stored files without their content; each one is loaded only when it is processed. */
    private List<SourceFile> collectSources(List<Item> items, Set<String> discoveredKeys) {
        List<SourceFile> sources = new ArrayList<>();
        int max = properties.ingestion().maxDocumentsPerRun();

        List<KnowledgeFile> files = fileRepository.findAllWithoutContent().stream()
                .sorted(Comparator.comparing(KnowledgeFile::getId)).toList();
        for (KnowledgeFile file : files) {
            String key = FILE_PREFIX + file.getId();
            discoveredKeys.add(key);
            if (sources.size() >= max) {
                items.add(new Item(key, "SKIPPED_IGNORED", "Se alcanzó el máximo de documentos por ejecución"));
                continue;
            }
            sources.add(new SourceFile(key, file.getId(), file.getFileName(), file.getAudience(), file.getSha256()));
        }
        return sources;
    }

    private Item processSource(SourceFile source, boolean force) {
        try {
            KnowledgeFile file = fileRepository.findById(source.id()).orElse(null);
            if (file == null || file.getContent() == null) {
                return new Item(source.key(), "FAILED", "El archivo ya no existe en el almacenamiento");
            }

            ParsedDocument parsed = parser.parse(source.fileName(), file.getContent());
            String hash = fingerprint(parsed, source.audience());
            KnowledgeDocument existing = documentRepository.findBySourceKey(source.key()).orElse(null);

            if (!force && existing != null && existing.getStatus() == KnowledgeStatus.ACTIVE
                    && hash.equals(existing.getContentHash())) {
                markUpToDate(existing, source.sha256());
                return new Item(source.key(), "SKIPPED_UNCHANGED", "Sin cambios");
            }

            String ingestionId = UUID.randomUUID().toString();
            List<Document> sectionDocuments = new ArrayList<>();
            for (Section section : parsed.sections()) {
                sectionDocuments.add(Document.builder()
                        .text(section.text())
                        .metadata(metadata(parsed.title(), section.heading(), source, ingestionId))
                        .build());
            }

            List<Document> chunks = splitter.apply(sectionDocuments).stream()
                    .map(chunk -> withContextHeader(chunk, parsed.title()))
                    .toList();
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("No se extrajo texto (¿PDF escaneado? el OCR no está soportado)");
            }
            if (chunks.size() > properties.ingestion().maxChunksPerDocument()) {
                throw new IllegalArgumentException("El documento supera el máximo de fragmentos permitido");
            }

            try {
                addInBatches(chunks);
            } catch (RuntimeException e) {
                removeByIngestion(ingestionId);
                throw e;
            }
            removeStaleChunks(source.key(), ingestionId);

            Instant now = Instant.now();
            KnowledgeDocument document = existing != null ? existing
                    : KnowledgeDocument.builder().sourceKey(source.key()).build();
            document.setTitle(parsed.title());
            document.setAudience(source.audience());
            document.setSourceType(KnowledgeDocumentParser.extensionOf(source.fileName()));
            document.setContentHash(hash);
            document.setSourceSha256(source.sha256());
            document.setChunkCount(chunks.size());
            document.setStatus(KnowledgeStatus.ACTIVE);
            document.setLastError(null);
            document.setIngestedAt(now);
            document.setUpdatedAt(now);
            documentRepository.save(document);

            return new Item(source.key(), "INGESTED", chunks.size() + " fragmentos");
        } catch (IllegalArgumentException | ChatException e) {
            return recordFailure(source, "FAILED", e.getMessage());
        } catch (TransientAiException e) {
            return recordFailure(source, "RATE_LIMITED",
                    "El proveedor de embeddings limitó las solicitudes (HTTP 429). Vuelve a ejecutar la ingesta: "
                            + "los documentos ya procesados se omiten.");
        } catch (NonTransientAiException e) {
            log.error("El proveedor de embeddings rechazó la solicitud para {} ({})", source.key(),
                    e.getClass().getSimpleName());
            return recordFailure(source, "FAILED",
                    "El proveedor de embeddings rechazó la solicitud. Revisa la clave y el modelo configurados.");
        } catch (RuntimeException e) {
            log.error("Error inesperado procesando el documento {} ({})", source.key(), e.getClass().getSimpleName());
            return recordFailure(source, "FAILED", "Error inesperado al procesar el documento");
        }
    }

    /** The indexed content already matches this file: only record which file version it was verified against. */
    private void markUpToDate(KnowledgeDocument document, String sha256) {
        if (sha256.equals(document.getSourceSha256()) && document.getLastError() == null) {
            return;
        }
        document.setSourceSha256(sha256);
        document.setLastError(null);
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);
    }

    /** Sends the chunks in small batches, so a rate-limited provider never receives one huge request. */
    private void addInBatches(List<Document> chunks) {
        int batchSize = properties.ingestion().embeddingBatchSize();
        for (int from = 0; from < chunks.size(); from += batchSize) {
            addWithRateLimitRetry(chunks.subList(from, Math.min(from + batchSize, chunks.size())));
        }
    }

    private void addWithRateLimitRetry(List<Document> batch) {
        ChatbotProperties.Ingestion config = properties.ingestion();
        int attempt = 0;
        while (true) {
            awaitRequestSlot();
            try {
                vectorStore.add(batch);
                return;
            } catch (TransientAiException e) {
                if (attempt >= config.rateLimitRetries()) {
                    throw e;
                }
                attempt++;
                log.warn("El proveedor de embeddings limitó las solicitudes; espero {} s y reintento ({} de {})",
                        config.rateLimitRetryWait().toSeconds(), attempt, config.rateLimitRetries());
                pause(config.rateLimitRetryWait());
            }
        }
    }

    /** Keeps a minimum time between embedding requests (0 = no pacing). */
    private void awaitRequestSlot() {
        long interval = properties.ingestion().minIntervalBetweenRequests().toNanos();
        if (interval <= 0) {
            return;
        }
        if (lastRequestNanos != null) {
            long remaining = interval - (System.nanoTime() - lastRequestNanos);
            if (remaining > 0) {
                pause(Duration.ofNanos(remaining));
            }
        }
        lastRequestNanos = System.nanoTime();
    }

    private void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ChatException.unavailable();
        }
    }

    /** The title and section are embedded with the text so a chunk keeps its context. */
    private Document withContextHeader(Document chunk, String title) {
        String section = String.valueOf(chunk.getMetadata().getOrDefault("section", ""));
        String header = title + (section.isBlank() ? "" : " · " + section) + "\n";
        return Document.builder().text(header + chunk.getText()).metadata(chunk.getMetadata()).build();
    }

    private Map<String, Object> metadata(String title, String section, SourceFile source, String ingestionId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", title);
        metadata.put("section", section == null ? "" : section);
        metadata.put("sourceKey", source.key());
        metadata.put("ingestionId", ingestionId);
        metadata.put("audience", source.audience());
        metadata.put("embeddingModel", embeddingModelId);
        return metadata;
    }

    private void removeStaleChunks(String sourceKey, String currentIngestionId) {
        mongo.remove(new Query(Criteria.where("metadata.sourceKey").is(sourceKey)
                .and("metadata.ingestionId").ne(currentIngestionId)), vectorCollection);
    }

    private void removeByIngestion(String ingestionId) {
        mongo.remove(new Query(Criteria.where("metadata.ingestionId").is(ingestionId)), vectorCollection);
    }

    private void removeBySource(String sourceKey) {
        mongo.remove(new Query(Criteria.where("metadata.sourceKey").is(sourceKey)), vectorCollection);
    }

    private Item recordFailure(SourceFile source, String outcome, String message) {
        log.warn("No se pudo ingerir el documento {}: {}", source.key(), message);
        KnowledgeDocument document = documentRepository.findBySourceKey(source.key())
                .orElseGet(() -> KnowledgeDocument.builder().sourceKey(source.key()).title(source.fileName())
                        .audience(source.audience()).chunkCount(0).status(KnowledgeStatus.FAILED).build());
        document.setLastError(message);
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);
        return new Item(source.key(), outcome, message);
    }

    /**
     * Retires indexed documents whose stored file no longer exists. If there are no stored files at all but
     * documents are still indexed, nothing is retired: that almost always means the one-time import of the old
     * knowledge folder has not been run yet. To remove documents on purpose, delete them from the admin screen.
     */
    private void retireMissing(Set<String> discoveredKeys, List<Item> items) {
        boolean suspiciousEmptyScan = discoveredKeys.isEmpty()
                && documentRepository.countByStatus(KnowledgeStatus.ACTIVE) > 0;
        if (suspiciousEmptyScan) {
            log.warn("No hay archivos almacenados pero hay documentos indexados: no se retira ninguno. "
                    + "¿Falta importar la carpeta knowledge/ con /admin/knowledge/import-legacy?");
        }

        Instant now = Instant.now();
        for (KnowledgeDocument document : documentRepository.findAll()) {
            String key = document.getSourceKey();
            if (document.getStatus() == KnowledgeStatus.RETIRED || discoveredKeys.contains(key)) {
                continue;
            }
            boolean legacySource = !key.startsWith(FILE_PREFIX);
            if (!legacySource && suspiciousEmptyScan) {
                continue;
            }
            removeBySource(key);
            document.setStatus(KnowledgeStatus.RETIRED);
            document.setChunkCount(0);
            document.setUpdatedAt(now);
            documentRepository.save(document);
            items.add(new Item(key, "RETIRED", legacySource
                    ? "Fuente retirada: ahora solo se ingieren los documentos de la base de conocimiento"
                    : "Ya no existe en las fuentes"));
        }
    }

    private String fingerprint(ParsedDocument parsed, String audience) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ChatbotProperties.Ingestion ingestion = properties.ingestion();
            for (String part : List.of(parsed.title(), audience, embeddingModelId,
                    String.valueOf(properties.rag().dimensions()),
                    String.valueOf(ingestion.chunkSizeTokens()), String.valueOf(ingestion.minChunkSizeChars()))) {
                digest.update(part.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            for (Section section : parsed.sections()) {
                digest.update((section.heading() == null ? "" : section.heading()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(section.text().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private int count(List<Item> items, String outcome) {
        return (int) items.stream().filter(item -> outcome.equals(item.outcome())).count();
    }
}