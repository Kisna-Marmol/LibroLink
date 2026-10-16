package com.librolink.dao;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.sql.Date;

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

    static void setLongNullable(PreparedStatement ps, int indice, Long valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.BIGINT);
        } else {
            ps.setLong(indice, valor);
        }
    }

    static void setIntNullable(PreparedStatement ps, int indice, Integer valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.INTEGER);
        } else {
            ps.setInt(indice, valor);
        }
    }

    static void setDateNullable(PreparedStatement ps, int indice, LocalDate valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.DATE);
        } else {
            ps.setDate(indice, Date.valueOf(valor));
        }
    }
}