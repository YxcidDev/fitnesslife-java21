package com.fitnesslife.gym.repository;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.fitnesslife.gym.model.KnowledgeDocument;
import com.fitnesslife.gym.enums.KnowledgeStatus;

@Repository
public interface KnowledgeDocumentRepository extends MongoRepository<KnowledgeDocument, String> {

    Optional<KnowledgeDocument> findBySourceKey(String sourceKey);

    long countByStatus(KnowledgeStatus status);
}