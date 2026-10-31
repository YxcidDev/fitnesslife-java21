package com.fitnesslife.gym.dto;

import java.time.Instant;

public record KnowledgeDocumentResponse(
        String sourceKey,
        String title,
        String audience,
        String status,
        int chunkCount,
        Instant ingestedAt,
        String lastError) {
}