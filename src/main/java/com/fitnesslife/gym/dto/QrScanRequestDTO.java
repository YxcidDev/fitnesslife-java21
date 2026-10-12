package com.fitnesslife.gym.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QrScanRequestDTO(
        @NotBlank(message = "El código QR es obligatorio")
        @Size(max = 200, message = "El código QR es demasiado largo")
        String qrCode) {
}