package com.fitnesslife.gym.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fitnesslife.gym.enums.AccessResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "accesses")
@CompoundIndexes({
        @CompoundIndex(name = "result_accessed_idx", def = "{'result': 1, 'accessedAt': -1}"),
        @CompoundIndex(name = "user_accessed_idx", def = "{'user': 1, 'accessedAt': -1}")
})
public class Access {

    @Id
    private String id;

    @DBRef(lazy = true)
    private User user;

    @Indexed
    private String qrCode;

    @CreatedDate
    @Indexed
    private LocalDateTime accessedAt;

    @Indexed
    private AccessResult result;

    private String userName;
    private String userEmail;
}
