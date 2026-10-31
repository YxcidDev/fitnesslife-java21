package com.fitnesslife.gym.service;

import java.util.regex.Pattern;

public final class SearchTermSanitizer {

    private static final int MAX_SEARCH_LENGTH = 100;

    private SearchTermSanitizer() {
    }

    public static String sanitize(String rawSearchTerm) {
        if (rawSearchTerm == null) {
            return null;
        }

        String trimmed = rawSearchTerm.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        String truncated = trimmed.length() > MAX_SEARCH_LENGTH
                ? trimmed.substring(0, MAX_SEARCH_LENGTH)
                : trimmed;

        return Pattern.quote(truncated);
    }
}