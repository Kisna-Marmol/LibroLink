package com.librolink.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Prueba de integración: requiere MySQL local y config/db.properties. Se omite si no existe. */
class DatabaseTest {

    private static AppConfig config;

    @BeforeAll
    static void cargar() throws IOException {
        assumeTrue(Files.isRegularFile(AppConfig.DEFAULT_PATH),
                "Sin config/db.properties: prueba de integración omitida");
        config = AppConfig.load();
    }

    @Test
    void conectaYEncuentraLosRolesIniciales() throws Exception {
        try (Database db = new Database(config)) {
            assertEquals(3, db.contarRoles());

            Set<String> nombres = new HashSet<>();
            try (Connection cn = db.getConnection();
                 Statement st = cn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT nombre FROM roles")) {
                while (rs.next()) {
                    nombres.add(rs.getString(1));
                }
            }
            assertTrue(nombres.containsAll(Set.of("ADMINISTRADOR", "BIBLIOTECARIO", "LECTOR")));
        }
    }
}