package com.librolink.dao;

import com.librolink.config.AppConfig;
import com.librolink.config.Database;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integración de solo lectura: comprueba que las consultas del panel (incluida la vista) se ejecutan. */
class DashboardDaoTest {

    private static AppConfig config;

    @BeforeAll
    static void cargar() throws IOException {
        assumeTrue(Files.isRegularFile(AppConfig.DEFAULT_PATH),
                "Sin config/db.properties: prueba de integración omitida");
        config = AppConfig.load();
    }

    @Test
    void lasConsultasDelPanelSeEjecutan() throws Exception {
        DashboardDao dao = new DashboardDao();
        try (Database db = new Database(config); Connection cn = db.getConnection()) {
            assertTrue(dao.titulosActivos(cn) >= 0);
            assertTrue(dao.ejemplaresDisponibles(cn) >= 0);
            assertTrue(dao.prestamosActivos(cn) >= 0);
            assertTrue(dao.prestamosAtrasados(cn) >= 0);
            assertTrue(dao.actividadReciente(cn, 8).size() <= 8);
        }
    }
}