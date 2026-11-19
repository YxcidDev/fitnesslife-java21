package com.fitnesslife.gym.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.fitnesslife.gym.model.RetentionAssignment;

@Repository
public interface RetentionAssignmentRepository extends MongoRepository<RetentionAssignment, String> {
}
