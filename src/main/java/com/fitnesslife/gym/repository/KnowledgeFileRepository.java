package com.fitnesslife.gym.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import com.fitnesslife.gym.model.KnowledgeFile;

@Repository
public interface KnowledgeFileRepository extends MongoRepository<KnowledgeFile, String> {

    @Query(value = "{}", fields = "{ 'content': 0 }")
    List<KnowledgeFile> findAllWithoutContent();

    @Query(value = "{ '_id': ?0 }", fields = "{ 'content': 0 }")
    Optional<KnowledgeFile> findSummaryById(String id);
}