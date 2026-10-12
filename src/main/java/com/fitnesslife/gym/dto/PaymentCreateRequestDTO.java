package com.fitnesslife.gym.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentCreateRequestDTO(
        @NotBlank(message = "El plan es obligatorio")
        String planId,

        @NotBlank(message = "La factura externa es obligatoria")
        @Size(max = 64, message = "La factura externa no puede superar los 64 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$",
                message = "La factura externa solo admite letras, números, guiones y guion bajo")
        String externalInvoice) {
}