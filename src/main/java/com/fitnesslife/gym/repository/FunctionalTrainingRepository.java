package com.fitnesslife.gym.repository;

import com.fitnesslife.gym.model.FunctionalTraining;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FunctionalTrainingRepository extends MongoRepository<FunctionalTraining, String> {

    Optional<FunctionalTraining> findByIdFunctionalTraining(String id);

    Optional<FunctionalTraining> findByIdFunctionalTraining(int idFunctionalTraining);

    void deleteByIdFunctionalTraining(int idFunctionalTraining);

    boolean existsByIdFunctionalTraining(int idFunctionalTraining);

    List<FunctionalTraining> findByStatusOrderByDatetimeAsc(String status);

    @Query("{ 'datetime': { $gte: ?0, $lte: ?1 } }")
    List<FunctionalTraining> findByDatetimeBetween(LocalDateTime startDate, LocalDateTime endDate);

    @Query("{ 'datetime': { $gte: ?0, $lte: ?1 }, 'status': ?2 }")
    List<FunctionalTraining> findByDatetimeBetweenAndStatus(LocalDateTime startDate, LocalDateTime endDate,
            String status);

    List<FunctionalTraining> findByDatetimeBetweenOrderByDatetimeAsc(LocalDateTime startDate, LocalDateTime endDate);

    Page<FunctionalTraining> findByDatetimeBetweenOrderByDatetimeAsc(LocalDateTime startDate, LocalDateTime endDate,
            Pageable pageable);

    List<FunctionalTraining> findByInstructorIgnoreCase(String instructor);

    @Query(value = "{ 'status': 'ACTIVE' }", sort = "{ 'userIds': -1 }")
    List<FunctionalTraining> findTopClasses(Pageable pageable);

    boolean existsByUserIdsContaining(Long identification);

    @Query(value = "{}", fields = "{ 'userIds': 1 }")
    List<FunctionalTraining> findAllUserIdProjections();

        // Spring Data MongoDB's Between is exclusive on both bounds.
    List<FunctionalTraining> findByInstructorIdAndDatetimeBetween(
            String instructorId, LocalDateTime from, LocalDateTime to);

    Page<FunctionalTraining> findByInstructorIdAndDatetimeBetween(
            String instructorId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<FunctionalTraining> findByInstructorIdAndDatetimeGreaterThanEqual(
            String instructorId, LocalDateTime from, Pageable pageable);

    Page<FunctionalTraining> findByInstructorIdAndDatetimeLessThan(
            String instructorId, LocalDateTime to, Pageable pageable);

    long countByInstructorIdAndDatetimeBetween(
            String instructorId, LocalDateTime from, LocalDateTime to);

    Optional<FunctionalTraining> findFirstByInstructorIdAndDatetimeGreaterThanEqualOrderByDatetimeAsc(
            String instructorId, LocalDateTime from);

    List<FunctionalTraining> findByInstructorIdIsNull();
}