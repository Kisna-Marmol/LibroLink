package com.librolink.dao;

final class Sql {

    private Sql() { }

    /** Patrón LIKE "contiene" con los comodines del usuario escapados. Devuelve null si no hay texto. */
    static String patronContiene(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String t = texto.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + t + "%";
    }
}