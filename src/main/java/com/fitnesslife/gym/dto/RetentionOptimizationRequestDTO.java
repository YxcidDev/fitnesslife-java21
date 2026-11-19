package com.fitnesslife.gym.dto;

import java.util.List;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RetentionOptimizationRequestDTO(

        @NotNull(message = "La confianza mínima es obligatoria")
        @DecimalMin(value = "0", message = "La confianza mínima debe estar entre 0 y 100")
        @DecimalMax(value = "100", message = "La confianza mínima debe estar entre 0 y 100")
        Double minConfidence,

        @NotNull(message = "Los meses esperados son obligatorios")
        @Size(min = 3, max = 3, message = "Se requieren 3 valores de meses esperados (bajo, medio, alto)")
        List<@NotNull @DecimalMin(value = "0", message = "Los meses esperados no pueden ser negativos") Double> expectedMonths,

        @NotNull(message = "La matriz p_ij es obligatoria")
        @Size(min = 3, max = 3, message = "La matriz p_ij debe tener 3 filas")
        List<@NotNull @Size(min = 3, max = 3, message = "Cada fila de p_ij debe tener 3 columnas")
                List<@NotNull @DecimalMin(value = "0", message = "p_ij debe estar entre 0 y 1")
                @DecimalMax(value = "1", message = "p_ij debe estar entre 0 y 1") Double>> retentionLift,

        @NotNull(message = "El costo del recordatorio es obligatorio")
        @DecimalMin(value = "0", message = "El costo no puede ser negativo")
        Double reminderCost,

        @NotNull(message = "El porcentaje de descuento es obligatorio")
        @DecimalMin(value = "0", message = "El descuento debe estar entre 0 y 1")
        @DecimalMax(value = "1", message = "El descuento debe estar entre 0 y 1")
        Double discountRate,

        @NotNull(message = "El costo del cupo de clase es obligatorio")
        @DecimalMin(value = "0", message = "El costo no puede ser negativo")
        Double classCost,

        @NotNull(message = "El presupuesto es obligatorio")
        @DecimalMin(value = "0", message = "El presupuesto no puede ser negativo")
        Double budget,

        @NotNull(message = "Los cupos de clases son obligatorios")
        @Min(value = 0, message = "Los cupos no pueden ser negativos")
        Integer classSlots,

        @NotNull(message = "El máximo de promociones es obligatorio")
        @Min(value = 0, message = "El máximo de promociones no puede ser negativo")
        Integer maxPromotions,

        @NotNull(message = "El máximo de recordatorios es obligatorio")
        @Min(value = 0, message = "El máximo de recordatorios no puede ser negativo")
        Integer maxReminders,

        @NotNull(message = "La cobertura mínima de riesgo alto es obligatoria")
        @DecimalMin(value = "0", message = "La cobertura debe estar entre 0 y 1")
        @DecimalMax(value = "1", message = "La cobertura debe estar entre 0 y 1")
        Double minHighRiskCoverage,

        Boolean integer) {
}
