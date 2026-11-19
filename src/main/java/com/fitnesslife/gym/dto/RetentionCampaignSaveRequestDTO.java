package com.fitnesslife.gym.dto;

import jakarta.validation.constraints.Size;

public record RetentionCampaignSaveRequestDTO(

        @Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
        String name,

        @Size(max = 500, message = "Las notas no pueden superar los 500 caracteres")
        String notes) {
}
