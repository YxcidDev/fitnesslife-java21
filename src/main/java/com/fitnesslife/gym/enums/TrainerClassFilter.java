package com.fitnesslife.gym.enums;

import java.util.Locale;

public enum TrainerClassFilter {
    TODAY,
    WEEK,
    MONTH,
    UPCOMING,
    HISTORY;

    public static TrainerClassFilter fromParam(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return TODAY;
        }
        try {
            return valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TODAY;
        }
    }
}