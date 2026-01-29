package com.fitnesslife.gym.repository;

import com.fitnesslife.gym.enums.AccessResult;
import com.fitnesslife.gym.model.Access;
import com.fitnesslife.gym.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AccessRepository extends MongoRepository<Access, String> {

       List<Access> findByUser(User user);

       List<Access> findByUserOrderByAccessedAtDesc(User user);

       List<Access> findByResult(AccessResult result);

       List<Access> findByAccessedAtBetween(LocalDateTime start, LocalDateTime end);

       @Query(value = "{ 'accessedAt': { $gte: ?0, $lte: ?1 } }", count = true)
       long countByAccessedAtBetween(LocalDateTime start, LocalDateTime end);

       @Query(value = "{}", sort = "{ 'accessedAt': -1 }")
       List<Access> findTopNByOrderByAccessedAtDesc(int limit);

       Page<Access> findAll(Pageable pageable);

       Page<Access> findByResult(AccessResult result, Pageable pageable);

       @Query("{ $or: [ " +
                     "{ 'userName': { $regex: ?0, $options: 'i' } }, " +
                     "{ 'userEmail': { $regex: ?0, $options: 'i' } }, " +
                     "{ 'qrCode': { $regex: ?0, $options: 'i' } } " +
                     "] }")
       Page<Access> searchAccesses(String searchTerm, Pageable pageable);

       @Query("{ 'result': ?0, $or: [ " +
                     "{ 'userName': { $regex: ?1, $options: 'i' } }, " +
                     "{ 'userEmail': { $regex: ?1, $options: 'i' } }, " +
                     "{ 'qrCode': { $regex: ?1, $options: 'i' } } " +
                     "] }")
       Page<Access> searchAccessesByResult(AccessResult result, String searchTerm, Pageable pageable);

       @Query(value = "{ 'result': 'ALLOWED', 'accessedAt': { $gte: ?0, $lte: ?1 } }", count = true)
       long countAllowedAccessesBetween(LocalDateTime start, LocalDateTime end);

       @Query(value = "{ 'result': 'ALLOWED', 'accessedAt': { $gte: ?0, $lte: ?1 } }")
       List<Access> findAllowedAccessesBetween(LocalDateTime start, LocalDateTime end);

       @Query(value = "{ 'result': 'ALLOWED', 'accessedAt': { $gte: ?0, $lte: ?1 } }")
       List<Access> findAllowedAccessesForPeakHours(LocalDateTime start, LocalDateTime end);
}
