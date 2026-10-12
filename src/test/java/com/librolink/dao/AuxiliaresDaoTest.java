package com.librolink.dao;

import com.librolink.config.AppConfig;
import com.librolink.config.Database;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integración: usa una transacción y hace rollback, así que no deja registros en la base. */
class AuxiliaresDaoTest {

    private static AppConfig config;

    @BeforeAll
    static void cargar() throws IOException {
        assumeTrue(Files.isRegularFile(AppConfig.DEFAULT_PATH),
                "Sin config/db.properties: prueba de integración omitida");
        config = AppConfig.load();
    }

    @Test
    void cicloCompletoDeLosTresCatalogos() throws Exception {
        try (Database db = new Database(config); Connection cn = db.getConnection()) {
            cn.setAutoCommit(false);
            try {
                String clave = "Prueba" + System.nanoTime();

                // categorías (nombre único)
                CategoriaDao cd = new CategoriaDao();
                long idC = cd.insertar(cn, clave, "desc");
                assertEquals(1, cd.listar(cn, clave, false).size());
                assertEquals(1, cd.actualizar(cn, idC, clave, "otra"));
                assertEquals(1, cd.cambiarEstado(cn, idC, false));
                assertTrue(cd.listar(cn, clave, false).isEmpty());
                assertEquals(1, cd.listar(cn, clave, true).size());
                assertThrows(SQLIntegrityConstraintViolationException.class,
                        () -> cd.insertar(cn, clave, null));
                assertEquals(0, cd.cambiarEstado(cn, Long.MAX_VALUE, true));

                // editoriales (nombre único)
                EditorialDao ed = new EditorialDao();
                long idE = ed.insertar(cn, clave, "Honduras", "https://editorial.test");
                assertEquals(1, ed.listar(cn, "Hondu", false).stream().filter(e -> e.id() == idE).count());
                assertEquals(1, ed.actualizar(cn, idE, clave, null, null));
                assertEquals(1, ed.cambiarEstado(cn, idE, false));
                assertThrows(SQLIntegrityConstraintViolationException.class,
                        () -> ed.insertar(cn, clave, null, null));

                // autores (sin unicidad)
                AutorDao ad = new AutorDao();
                long idA = ad.insertar(cn, clave, "Apellido", null, null);
                assertEquals(1, ad.listar(cn, clave, false).size());
                assertEquals(1, ad.actualizar(cn, idA, clave, null, "Honduras", "bio"));
                assertEquals(1, ad.cambiarEstado(cn, idA, false));
                assertTrue(ad.listar(cn, clave, false).isEmpty());
                assertEquals(1, ad.listar(cn, clave, true).size());
            } finally {
                cn.rollback();
            }
        }
    }
}