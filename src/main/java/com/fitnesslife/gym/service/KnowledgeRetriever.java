package com.fitnesslife.gym.service;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fitnesslife.gym.config.ChatbotProperties;
import com.fitnesslife.gym.exception.ChatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.TransientAiException;

@Slf4j
@Service
public class KnowledgeRetriever {

    public record Hit(String title, String section, String content, double score) {
    }

    private final VectorStore vectorStore;
    private final ChatbotProperties properties;
    private final String embeddingModelId;
    private final String indexName;

    public KnowledgeRetriever(VectorStore vectorStore, ChatbotProperties properties,
            @Value("${spring.ai.openai.embedding.options.model}") String embeddingModelId,
            @Value("${spring.ai.vectorstore.mongodb.index-name}") String indexName) {
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.embeddingModelId = embeddingModelId;
        this.indexName = indexName;
    }

    public List<Hit> retrieve(String query, List<String> audiences) {
        return retrieve(query, audiences, properties.rag().minScore());
    }

    public List<Hit> retrieve(String query, List<String> audiences, double minScore) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression filter = builder.and(
                builder.in("audience", audiences.toArray()),
                builder.eq("embeddingModel", embeddingModelId)).build();

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(properties.rag().topK())
                .similarityThreshold(minScore)
                .filterExpression(filter)
                .build();

        try {
            List<Hit> hits = vectorStore.similaritySearch(request).stream()
                    .map(this::toHit)
                    .toList();
            log.debug("Búsqueda vectorial: {} fragmentos sobre el umbral {}", hits.size(), minScore);
            return hits;
        } catch (RuntimeException e) {
            log.error("Falló la búsqueda vectorial ({}). ¿Existe el índice '{}' y está listo?",
                    e.getClass().getSimpleName(), indexName);
            throw e instanceof TransientAiException ? ChatException.providerBusy() : ChatException.providerFailure();
        }
    }

    private Hit toHit(Document document) {
        return new Hit(
                String.valueOf(document.getMetadata().getOrDefault("title", "")),
                String.valueOf(document.getMetadata().getOrDefault("section", "")),
                document.getText() == null ? "" : document.getText(),
                document.getScore() == null ? 0.0 : document.getScore());
    }
}