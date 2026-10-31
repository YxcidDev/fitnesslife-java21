package com.fitnesslife.gym.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Document(collection = "knowledgeFiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeFile {

    @Id
    private String id;

    private String audience;
    private String fileName;
    private long sizeBytes;
    private String sha256;
    private String uploadedBy;
    private Instant createdAt;
    private Instant updatedAt;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private byte[] content;
}