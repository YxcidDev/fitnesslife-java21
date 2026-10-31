package com.fitnesslife.gym.dto;

import java.util.List;

public record KnowledgeIngestionResult(int processed, int skipped, int failed, int retired, List<Item> items) {

    public record Item(String sourceKey, String outcome, String detail) {
    }
}