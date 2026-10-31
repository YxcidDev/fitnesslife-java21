package com.fitnesslife.gym.dto;

import java.util.List;
import com.fitnesslife.gym.dto.KnowledgeIngestionResult.Item;

public record KnowledgeImportResult(int imported, int skipped, int failed, List<Item> items) {
}