package com.fitnesslife.gym.dto;

public record KnowledgeStatusResponse(
        long documents,
        long chunks,
        String embeddingModel,
        int dimensions,
        VectorIndex vectorIndex,
        boolean ingestionRunning) {

    public record VectorIndex(String name, boolean found, String status, boolean queryable, String note) {
    }
}