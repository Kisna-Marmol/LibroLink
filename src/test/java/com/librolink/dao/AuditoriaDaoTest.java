package com.librolink.dao;

import com.librolink.config.AppConfig;
import com.librolink.config.Database;
import com.librolink.model.FiltroAuditoria;
import com.librolink.model.RegistroAuditoria;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integración: usa una transacción y hace rollback, así que no deja registros en la base. */
class AuditoriaDaoTest {

    private static AppConfig config;

    @BeforeAll
    static void cargar() throws IOException {
        assumeTrue(Files.isRegularFile(AppConfig.DEFAULT_PATH),
                "Sin config/db.properties: prueba de integración omitida");
        config = AppConfig.load();
    }

    @Test
    void filtrosOrdenYPaginacion() throws Exception {
        try (Database db = new Database(config); Connection cn = db.getConnection()) {
            cn.setAutoCommit(false);
            try {
                Long idUsuario = null;
                String nombre = null;
                try (Statement st = cn.createStatement();
                     ResultSet rs = st.executeQuery(
                             "SELECT id_usuario, CONCAT_WS(' ', nombre, apellido) FROM usuarios LIMIT 1")) {
                    if (rs.next()) {
                        idUsuario = rs.getLong(1);
                        nombre = rs.getString(2);
                    }
                }
                assumeTrue(idUsuario != null, "Sin usuarios en la base: prueba omitida");

                String accion = "TEST_" + System.nanoTime();
                String entidad = "ent_prueba";
                AuditoriaDao dao = new AuditoriaDao();
                dao.registrar(cn, idUsuario, accion, entidad, "1", null);
                dao.registrar(cn, idUsuario, accion, entidad, "2", "{\"k\":\"v\"}");
                dao.registrar(cn, null, accion, entidad, "3", null);

                // orden: el más reciente primero; un registro sin usuario devuelve usuario null
                FiltroAuditoria porAccion = new FiltroAuditoria(null, null, null, accion, null);
                List<RegistroAuditoria> todos = dao.listar(cn, porAccion, 10, 0);
                assertEquals(3, todos.size());
                assertEquals("3", todos.get(0).idRegistro());
                assertNull(todos.get(0).usuario());
                assertEquals("2", todos.get(1).idRegistro());
                assertEquals(nombre, todos.get(1).usuario());
                assertTrue(todos.get(1).detalle().contains("\"k\""));
                assertNull(todos.get(2).detalle());

                // paginación
                assertEquals(2, dao.listar(cn, porAccion, 2, 0).size());
                List<RegistroAuditoria> segunda = dao.listar(cn, porAccion, 2, 2);
                assertEquals(1, segunda.size());
                assertEquals("1", segunda.get(0).idRegistro());

                // filtros por entidad, fechas y usuario
                assertEquals(3, dao.listar(cn, new FiltroAuditoria(null, null, null, null, entidad), 10_000, 0)
                        .stream().filter(r -> r.accion().equals(accion)).count());
                LocalDate hoy = LocalDate.now();
                assertEquals(3, dao.listar(cn,
                        new FiltroAuditoria(hoy.minusDays(1), hoy.plusDays(1), null, accion, null), 10, 0).size());
                assertEquals(0, dao.listar(cn,
                        new FiltroAuditoria(hoy.plusDays(5), hoy.plusDays(6), null, accion, null), 10, 0).size());
                assertEquals(2, dao.listar(cn, new FiltroAuditoria(null, null, nombre, accion, null), 10, 0).size());

                // listas para los filtros
                assertTrue(dao.acciones(cn).contains(accion));
                assertTrue(dao.entidades(cn).contains(entidad));
            } finally {
                cn.rollback();
            }
        }
    }
}