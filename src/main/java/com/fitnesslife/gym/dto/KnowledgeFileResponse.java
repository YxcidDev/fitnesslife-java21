package com.fitnesslife.gym.dto;

import java.time.Instant;
import com.fitnesslife.gym.enums.KnowledgeAudience;
import com.fitnesslife.gym.enums.KnowledgeFileStatus;

public record KnowledgeFileResponse(
        String fileName,
        KnowledgeAudience audience,
        long sizeBytes,
        Instant modifiedAt,
        KnowledgeFileStatus status,
        String title,
        int chunkCount,
        Instant indexedAt,
        String lastError) {
}