package com.fitnesslife.gym.controller;

import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.fitnesslife.gym.dto.KnowledgeFileResponse;
import com.fitnesslife.gym.dto.KnowledgeIngestionResult;
import com.fitnesslife.gym.dto.KnowledgeSearchHitResponse;
import com.fitnesslife.gym.dto.KnowledgeStatusResponse;
import com.fitnesslife.gym.enums.KnowledgeAudience;
import com.fitnesslife.gym.exception.InvalidRequestException;
import com.fitnesslife.gym.service.KnowledgeAdminService;
import com.fitnesslife.gym.service.KnowledgeFileService;
import com.fitnesslife.gym.service.KnowledgeIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fitnesslife.gym.dto.KnowledgeImportResult;

@Slf4j
@RestController
@RequestMapping("/admin/knowledge")
@RequiredArgsConstructor
public class AdminKnowledgeController {

    private static final int MAX_QUERY_LENGTH = 300;

    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeAdminService adminService;
    private final KnowledgeFileService fileService;

    @PostMapping("/ingest")
    public KnowledgeIngestionResult ingest(@RequestParam(defaultValue = "false") boolean force, Principal principal) {
        log.info("Indexación solicitada por {} (reindexar todo: {})", principal.getName(), force);
        return ingestionService.ingest(force);
    }

    @GetMapping("/documents")
    public List<KnowledgeFileResponse> documents() {
        return fileService.list();
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeFileResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("audience") String audience,
            @RequestParam(defaultValue = "false") boolean overwrite,
            Principal principal) {
        return fileService.upload(file, parseAudience(audience), overwrite, principal.getName());
    }

    @DeleteMapping("/documents")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam String audience, @RequestParam String fileName, Principal principal) {
        fileService.delete(parseAudience(audience), fileName, principal.getName());
    }

    @GetMapping("/status")
    public KnowledgeStatusResponse status() {
        return adminService.status();
    }

    @GetMapping("/search")
    public List<KnowledgeSearchHitResponse> search(@RequestParam String query) {
        if (query == null || query.isBlank() || query.length() > MAX_QUERY_LENGTH) {
            throw new InvalidRequestException("La consulta debe tener entre 1 y " + MAX_QUERY_LENGTH + " caracteres");
        }
        return adminService.search(query.strip());
    }

    private KnowledgeAudience parseAudience(String raw) {
        try {
            return KnowledgeAudience.fromParam(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("La audiencia no es válida");
        }
    }

    @PostMapping("/import-legacy")
    public KnowledgeImportResult importLegacy(Principal principal) {
        log.info("Importación de la carpeta de conocimiento solicitada por {}", principal.getName());
        return fileService.importFromDirectory(principal.getName());
    }
}