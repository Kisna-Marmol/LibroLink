package com.librolink.dao;

import com.librolink.config.AppConfig;
import com.librolink.config.Database;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integración: usa una transacción y hace rollback, así que no deja registros en la base. */
class ConfiguracionDaoTest {

    private static AppConfig config;

    @BeforeAll
    static void cargar() throws IOException {
        assumeTrue(Files.isRegularFile(AppConfig.DEFAULT_PATH),
                "Sin config/db.properties: prueba de integración omitida");
        config = AppConfig.load();
    }

    @Test
    void leeBloqueaYGuardaConUpsert() throws Exception {
        try (Database db = new Database(config); Connection cn = db.getConnection()) {
            cn.setAutoCommit(false);
            try {
                ConfiguracionDao dao = new ConfiguracionDao();
                assertTrue(dao.todas(cn).containsKey("dias_prestamo"));
                dao.bloquearTodas(cn);

                String clave = "clave_prueba_" + System.nanoTime();
                dao.guardar(cn, clave, "1", "descripción de prueba");
                assertEquals("1", dao.todas(cn).get(clave));
                dao.guardar(cn, clave, "2", "otra descripción");
                assertEquals("2", dao.todas(cn).get(clave));
            } finally {
                cn.rollback();
            }
        }
    }
}