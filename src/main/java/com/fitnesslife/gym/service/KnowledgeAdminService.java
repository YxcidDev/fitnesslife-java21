package com.fitnesslife.gym.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import com.fitnesslife.gym.config.ChatbotProperties;
import com.fitnesslife.gym.dto.KnowledgeSearchHitResponse;
import com.fitnesslife.gym.dto.KnowledgeStatusResponse;
import com.fitnesslife.gym.dto.KnowledgeStatusResponse.VectorIndex;
import com.fitnesslife.gym.enums.KnowledgeAudience;
import com.fitnesslife.gym.enums.KnowledgeStatus;
import com.fitnesslife.gym.enums.Role;
import com.fitnesslife.gym.repository.KnowledgeDocumentRepository;
import com.mongodb.MongoException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KnowledgeAdminService {

    private final MongoTemplate mongo;
    private final ChatbotProperties properties;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeRetriever retriever;
    private final RedisLockService lockService;
    private final String vectorCollection;
    private final String indexName;
    private final String embeddingModelId;

    public KnowledgeAdminService(MongoTemplate mongo, ChatbotProperties properties,
            KnowledgeDocumentRepository documentRepository, KnowledgeRetriever retriever,
            RedisLockService lockService,
            @Value("${spring.ai.vectorstore.mongodb.collection-name}") String vectorCollection,
            @Value("${spring.ai.vectorstore.mongodb.index-name}") String indexName,
            @Value("${spring.ai.openai.embedding.options.model}") String embeddingModelId) {
        this.mongo = mongo;
        this.properties = properties;
        this.documentRepository = documentRepository;
        this.retriever = retriever;
        this.lockService = lockService;
        this.vectorCollection = vectorCollection;
        this.indexName = indexName;
        this.embeddingModelId = embeddingModelId;
    }

    public KnowledgeStatusResponse status() {
        long chunks = mongo.getCollection(vectorCollection).estimatedDocumentCount();
        return new KnowledgeStatusResponse(documentRepository.countByStatus(KnowledgeStatus.ACTIVE), chunks,
                embeddingModelId, properties.rag().dimensions(), vectorIndexStatus(),
                lockService.isHeld(KnowledgeIngestionService.LOCK_KEY));
    }

    public List<KnowledgeSearchHitResponse> search(String query) {
        return retriever.retrieve(query, KnowledgeAudience.visibleTo(Role.ADMIN), 0.0).stream()
                .map(hit -> new KnowledgeSearchHitResponse(hit.title(), hit.section(), hit.score(),
                        hit.content().length() > 160 ? hit.content().substring(0, 160) + "…" : hit.content()))
                .toList();
    }

    private VectorIndex vectorIndexStatus() {
        try {
            List<org.bson.Document> indexes = mongo.getCollection(vectorCollection)
                    .listSearchIndexes().into(new ArrayList<>());
            for (org.bson.Document index : indexes) {
                if (indexName.equals(index.getString("name"))) {
                    return new VectorIndex(indexName, true, index.getString("status"),
                            Boolean.TRUE.equals(index.getBoolean("queryable")), null);
                }
            }
            return new VectorIndex(indexName, false, null, false, "No existe un índice de búsqueda con ese nombre");
        } catch (MongoException e) {
            log.warn("No se pudo consultar los índices de búsqueda de Atlas (código {})", e.getCode());
            return new VectorIndex(indexName, false, null, false,
                    "No se pudo consultar los índices de búsqueda (¿el clúster es de Atlas?)");
        }
    }
}