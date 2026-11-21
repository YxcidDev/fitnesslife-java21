package com.fitnesslife.gym.repository;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import com.fitnesslife.gym.enums.ClassesStatus;
import com.fitnesslife.gym.model.FunctionalTraining;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class FunctionalTrainingEnrollmentStore {

    private final MongoTemplate mongo;

    public boolean tryAddUser(int classId, Long identification, Instant now) {
        Document roomLeft = new Document("$lt", List.of(
                new Document("$size", new Document("$ifNull", List.of("$userIds", List.of()))),
                "$maximumCapacity"));

        Document filter = new Document("idFunctionalTraining", classId)
                .append("status", ClassesStatus.ACTIVE.name())
                .append("datetime", new Document("$gt", Date.from(now)))
                .append("userIds", new Document("$ne", identification))
                .append("$expr", roomLeft);

        return mongo.updateFirst(new BasicQuery(filter), new Update().addToSet("userIds", identification),
                FunctionalTraining.class).getModifiedCount() == 1;
    }

    public boolean tryRemoveUser(int classId, Long identification, Instant now) {
        Document filter = new Document("idFunctionalTraining", classId)
                .append("datetime", new Document("$gt", Date.from(now)))
                .append("userIds", identification);

        return mongo.updateFirst(new BasicQuery(filter), new Update().pull("userIds", identification),
                FunctionalTraining.class).getModifiedCount() == 1;
    }

    public List<FunctionalTraining> findEnrolledStartingBetween(Long identification, Instant from, Instant to) {
        Query query = new Query(Criteria.where("userIds").is(identification)
                .and("datetime").gte(Date.from(from)).lt(Date.from(to)));
        return mongo.find(query, FunctionalTraining.class);
    }
}