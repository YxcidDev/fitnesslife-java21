package com.fitnesslife.gym.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

public record RetentionLaunchRequestDTO(

        @Min(value = 1, message = "Debes indicar la cantidad de correos que aprobaste")
        int expectedRecipients,

        @AssertTrue(message = "Debes confirmar que entiendes que el envío no se puede deshacer")
        boolean acknowledged) {
}