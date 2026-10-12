package com.fitnesslife.gym.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "plans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    @Id
    private String id;

    @NotBlank(message = "El nombre del plan es obligatorio")
    @Size(min = 3, max = 40, message = "El nombre del plan debe tener entre 3 y 40 caracteres")
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ0-9\\s]+$",
            message = "El nombre del plan solo admite letras, números y espacios")
    private String planName;

    @NotNull(message = "El precio es obligatorio")
    @Positive(message = "El precio debe ser mayor que cero")
    private Double price;

    @NotBlank(message = "La moneda es obligatoria")
    @Pattern(regexp = "COP|USD|EUR", message = "Moneda no soportada")
    private String currency;

    @NotNull(message = "La duración es obligatoria")
    @Min(value = 1, message = "La duración debe ser de al menos 1 día")
    @Max(value = 365, message = "La duración no puede superar los 365 días")
    private Integer durationDays;

    @NotBlank(message = "El badge es obligatorio")
    private String badge;

    @NotEmpty(message = "Debe incluir al menos un beneficio")
    private List<String> benefits;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}