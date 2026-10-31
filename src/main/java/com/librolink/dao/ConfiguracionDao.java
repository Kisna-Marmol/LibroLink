package com.librolink.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class ConfiguracionDao {

    public Map<String, String> todas(Connection cn) throws SQLException {
        Map<String, String> valores = new HashMap<>();
        try (PreparedStatement ps = cn.prepareStatement("SELECT clave, valor FROM configuracion");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                valores.put(rs.getString(1), rs.getString(2));
            }
        }
        return valores;
    }

    /** Bloquea las filas de configuración hasta el fin de la transacción: dos cambios simultáneos no se pisan. */
    public void bloquearTodas(Connection cn) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("SELECT clave FROM configuracion FOR UPDATE");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                // solo se necesita tomar el bloqueo
            }
        }
    }

    /** Crea la clave si no existe o actualiza su valor (funciona en MariaDB y en MySQL). */
    public int guardar(Connection cn, String clave, String valor, String descripcion) throws SQLException {
        String sql = """
                INSERT INTO configuracion (clave, valor, descripcion) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE valor = VALUES(valor)
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, clave);
            ps.setString(2, valor);
            ps.setString(3, descripcion);
            return ps.executeUpdate();
        }
    }
}