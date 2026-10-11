package com.librolink.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Pool de conexiones JDBC (HikariCP). Una instancia por aplicación. */
public final class Database implements AutoCloseable {

    private final HikariDataSource dataSource;

    public Database(AppConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.dbUrl());
        hc.setUsername(config.dbUser());
        hc.setPassword(config.dbPassword());
        hc.setPoolName("librolink-pool");
        hc.setMaximumPoolSize(5);
        hc.setMinimumIdle(1);
        hc.setConnectionTimeout(5_000);
        this.dataSource = new HikariDataSource(hc); // falla aquí si no hay conexión
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /** Prueba mínima: cuenta los roles cargados por el esquema (esperado: 3). */
    public int contarRoles() throws SQLException {
        String sql = "SELECT COUNT(*) FROM roles";
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}