package com.fitnesslife.gym.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.fitnesslife.gym.enums.RetentionCampaignStatus;
import com.fitnesslife.gym.enums.RetentionDispatchStatus;
import com.fitnesslife.gym.model.RetentionCampaign;

@Repository
public interface RetentionCampaignRepository extends MongoRepository<RetentionCampaign, String> {

    List<RetentionCampaign> findByStatus(RetentionCampaignStatus status, Pageable pageable);

    List<RetentionCampaign> findByDispatchStatus(RetentionDispatchStatus dispatchStatus);

    long deleteByStatusAndCreatedAtBefore(RetentionCampaignStatus status, LocalDateTime before);

    long deleteByStatusAndCreatedBy(RetentionCampaignStatus status, String createdBy);
}
