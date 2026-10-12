package com.fitnesslife.gym.service;

import java.util.regex.Pattern;

/**
 * Sanea términos de búsqueda antes de usarlos en una consulta $regex de MongoDB.
 *
 * Sin esto, un término como "a.*b" no se busca de forma literal — se interpreta
 * como patrón de expresión regular, lo que permite tanto fugas de información
 * (coincidencias no intencionadas, o inyección de regex a ciegas comparando
 * "0 resultados" contra "hay resultados") como ataques de denegación de servicio
 * mediante patrones costosos de evaluar (ReDoS).
 *
 * Pattern.quote() envuelve el texto en \Q...\E, sintaxis que también reconoce el
 * motor PCRE que usa MongoDB para $regex — por eso esta utilidad de Java protege
 * igual de bien las consultas de Mongo, no solo las de Java.
 */
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