package com.fitnesslife.gym.repository;

import java.time.Instant;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.fitnesslife.gym.model.ToolAuditLog;

@Repository
public interface ToolAuditRepository extends MongoRepository<ToolAuditLog, String> {

    long deleteByCreatedAtBefore(Instant cutoff);

    long deleteByUserId(String userId);
}