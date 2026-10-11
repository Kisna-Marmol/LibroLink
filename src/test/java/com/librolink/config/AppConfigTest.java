package com.librolink.config;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @Test
    void cargaPropiedadesValidas() {
        Properties p = new Properties();
        p.setProperty("db.url", "jdbc:mysql://localhost:3306/bibliolink_db");
        p.setProperty("db.user", "root");
        p.setProperty("db.password", "secreto");

        AppConfig cfg = AppConfig.fromProperties(p);

        assertEquals("root", cfg.dbUser());
        assertFalse(cfg.toString().contains("secreto"));
    }

    @Test
    void fallaSiFaltaUrl() {
        Properties p = new Properties();
        p.setProperty("db.user", "root");
        assertThrows(IllegalArgumentException.class, () -> AppConfig.fromProperties(p));
    }
}