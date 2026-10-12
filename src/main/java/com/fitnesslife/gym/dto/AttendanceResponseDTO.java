package com.fitnesslife.gym.dto;

import com.fitnesslife.gym.enums.AccessResult;
import java.time.LocalDateTime;

public record AttendanceResponseDTO(
        String id,
        String userId,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        AccessResult result,
        String userName,
        String userEmail,
        String userPhoto,
        String userPlan) {
}