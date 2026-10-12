package com.fitnesslife.gym.dto;

import com.fitnesslife.gym.enums.BloodType;
import com.fitnesslife.gym.enums.Role;
import com.fitnesslife.gym.enums.Sex;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserResponseDTO(
        String id,
        Long identification,
        String name,
        String lastname,
        Sex sex,
        LocalDate birthDate,
        BloodType bloodType,
        String email,
        String phone,
        String photoProfile,
        Role role,
        String plan,
        boolean active,
        String qrCodePath,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastLogin) {
}