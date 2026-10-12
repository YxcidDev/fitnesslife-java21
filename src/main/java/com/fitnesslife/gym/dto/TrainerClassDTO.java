package com.fitnesslife.gym.dto;

import java.time.LocalDateTime;
import java.util.List;
import com.fitnesslife.gym.enums.ClassPhase;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TrainerClassDTO {
    String id;
    String name;
    String room;
    LocalDateTime startsAt;
    LocalDateTime endsAt;
    int durationMinutes;
    int enrolledCount;
    int capacity;
    int occupancyPercent;
    ClassPhase phase;
    List<String> attendeeNames;
}