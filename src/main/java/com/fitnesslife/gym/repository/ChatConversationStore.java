package com.fitnesslife.gym.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import com.fitnesslife.gym.model.ChatConversation;
import com.fitnesslife.gym.model.ChatMessage;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ChatConversationStore {

    private final MongoTemplate mongo;

    public String newId() {
        return new ObjectId().toHexString();
    }

    public void insert(ChatConversation conversation) {
        mongo.insert(conversation);
    }

    public Optional<ChatConversation> findOwned(String id, String userId, int lastMessages) {
        if (!ObjectId.isValid(id)) {
            return Optional.empty();
        }
        Query query = new Query(Criteria.where("id").is(id).and("userId").is(userId));
        if (lastMessages > 0) {
            query.fields().slice("messages", -lastMessages);
        } else {
            query.fields().exclude("messages");
        }
        return Optional.ofNullable(mongo.findOne(query, ChatConversation.class));
    }

    public List<ChatConversation> listOwned(String userId, int page, int size) {
        Query query = new Query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "updatedAt"))
                .skip((long) page * size)
                .limit(size);
        query.fields().exclude("messages");
        return mongo.find(query, ChatConversation.class);
    }

    public long countOwned(String userId) {
        return mongo.count(new Query(Criteria.where("userId").is(userId)), ChatConversation.class);
    }

    public boolean appendExchange(String id, String userId, List<ChatMessage> messages, String newTitle,
            int maxMessages, Instant now) {
        Query query = new Query(Criteria.where("id").is(id).and("userId").is(userId)
                .and("messageCount").lte(maxMessages - messages.size()));
        Update update = new Update()
                .push("messages").each(messages.toArray())
                .set("updatedAt", now)
                .inc("messageCount", messages.size());
        if (newTitle != null) {
            update.set("title", newTitle);
        }
        return mongo.updateFirst(query, update, ChatConversation.class).getModifiedCount() > 0;
    }

    public boolean delete(String id, String userId) {
        if (!ObjectId.isValid(id)) {
            return false;
        }
        Query query = new Query(Criteria.where("id").is(id).and("userId").is(userId));
        return mongo.remove(query, ChatConversation.class).getDeletedCount() > 0;
    }

    public long deleteAllByUser(String userId) {
        return mongo.remove(new Query(Criteria.where("userId").is(userId)), ChatConversation.class)
                .getDeletedCount();
    }

    public long deleteInactiveSince(Instant cutoff) {
        return mongo.remove(new Query(Criteria.where("updatedAt").lt(cutoff)), ChatConversation.class)
                .getDeletedCount();
    }
}