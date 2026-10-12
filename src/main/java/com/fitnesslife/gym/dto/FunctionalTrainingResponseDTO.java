package com.fitnesslife.gym.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fitnesslife.gym.enums.ClassesStatus;
import java.util.Date;
import java.util.List;

public record FunctionalTrainingResponseDTO(
        String id,
        int idFunctionalTraining,
        String nameTraining,
        String instructor,
        String description,
        int maximumCapacity,
        String duration,
        ClassesStatus status,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Date createdAt,
        Date datetime,
        String room,
        List<Long> userIds) {
}