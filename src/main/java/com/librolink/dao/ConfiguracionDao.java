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
}