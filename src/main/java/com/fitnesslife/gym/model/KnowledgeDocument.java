package com.fitnesslife.gym.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fitnesslife.gym.enums.KnowledgeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "knowledgeDocuments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sourceKey;

    private String title;
    private String audience;
    private String sourceType;
    private String contentHash;
    private int chunkCount;
    private String sourceSha256;
    private KnowledgeStatus status;
    private String lastError;
    private Instant ingestedAt;
    private Instant updatedAt;
}