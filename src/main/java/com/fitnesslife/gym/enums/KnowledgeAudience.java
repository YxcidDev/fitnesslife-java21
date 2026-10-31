package com.fitnesslife.gym.enums;

import java.util.List;
import java.util.Locale;

public enum KnowledgeAudience {
    ALL,
    TRAINER,
    ADMIN;

    public static List<String> visibleTo(Role role) {
        return switch (role) {
            case ADMIN -> List.of("ALL", "TRAINER", "ADMIN");
            case TRAINER -> List.of("ALL", "TRAINER");
            default -> List.of("ALL");
        };
    }

    public static KnowledgeAudience fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("La audiencia es obligatoria");
        }
        return valueOf(raw.strip().toUpperCase(Locale.ROOT));
    }

    public String folderName() {
        return name().toLowerCase(Locale.ROOT);
    }
}