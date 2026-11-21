package com.fitnesslife.gym.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.fitnesslife.gym.enums.PendingActionStatus;
import com.fitnesslife.gym.model.PendingAction;

@Repository
public interface PendingActionRepository extends MongoRepository<PendingAction, String> {

    List<PendingAction> findByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtAsc(String userId,
                                                                                  PendingActionStatus status, Instant now);

    long countByUserIdAndStatusAndExpiresAtAfter(String userId, PendingActionStatus status, Instant now);

    Optional<PendingAction> findByIdAndUserId(String id, String userId);

    List<PendingAction> findByIdInAndUserIdAndStatus(Collection<String> ids, String userId,
                                                     PendingActionStatus status);

    long deleteByUserId(String userId);

    long deleteByCreatedAtBefore(Instant cutoff);
}