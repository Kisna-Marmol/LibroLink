package com.librolink.dao;

import com.librolink.config.AppConfig;
import com.librolink.config.Database;
import com.librolink.model.Ejemplar;
import com.librolink.model.EstadoEjemplar;
import com.librolink.model.FiltroEjemplares;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integración: usa una transacción y hace rollback, así que no deja registros en la base. */
class EjemplarDaoTest {

    private static AppConfig config;

    @BeforeAll
    static void cargar() throws IOException {
        assumeTrue(Files.isRegularFile(AppConfig.DEFAULT_PATH),
                "Sin config/db.properties: prueba de integración omitida");
        config = AppConfig.load();
    }

    private static boolean contiene(EjemplarDao dao, Connection cn, FiltroEjemplares f, long id)
            throws SQLException {
        return dao.listar(cn, f, 500).stream().anyMatch(e -> e.id() == id);
    }

    private static long libroDePrueba(Connection cn, String titulo) throws SQLException {
        long idCat = new CategoriaDao().insertar(cn, "Cat " + titulo, null);
        return new LibroDao().insertar(cn,
                new LibroDao.Datos(null, titulo, null, idCat, null, null, null, null));
    }

    @Test
    void cicloCompletoDeUnEjemplar() throws Exception {
        try (Database db = new Database(config); Connection cn = db.getConnection()) {
            cn.setAutoCommit(false);
            try {
                String clave = "Prueba" + System.nanoTime();
                long idLibro = libroDePrueba(cn, "Titulo " + clave);
                long idLibro2 = libroDePrueba(cn, "Otro " + clave);

                EjemplarDao dao = new EjemplarDao();
                String codigo = "T-" + System.nanoTime();
                long id = dao.insertar(cn, new EjemplarDao.Datos(
                        idLibro, codigo, "Estante A", LocalDate.of(2024, 5, 1), EstadoEjemplar.DISPONIBLE, "obs"));

                // búsqueda por cada criterio y por estado
                assertTrue(contiene(dao, cn, new FiltroEjemplares(codigo, null), id), "código");
                assertTrue(contiene(dao, cn, new FiltroEjemplares("Titulo " + clave, null), id), "título");
                assertTrue(contiene(dao, cn, new FiltroEjemplares("Estante A", EstadoEjemplar.DISPONIBLE), id));
                assertFalse(contiene(dao, cn, new FiltroEjemplares(codigo, EstadoEjemplar.PERDIDO), id));
                assertFalse(contiene(dao, cn, new FiltroEjemplares("zzz-no-existe-" + clave, null), id));

                Ejemplar e = dao.listar(cn, new FiltroEjemplares(codigo, null), 10).get(0);
                assertEquals(idLibro, e.idLibro());
                assertEquals("Titulo " + clave, e.tituloLibro());
                assertEquals(LocalDate.of(2024, 5, 1), e.fechaAdquisicion());
                assertEquals(EstadoEjemplar.DISPONIBLE, e.estado());

                // bloqueo y consultas auxiliares
                Optional<EjemplarDao.Actual> actual = dao.bloquear(cn, id);
                assertTrue(actual.isPresent());
                assertEquals(idLibro, actual.get().idLibro());
                assertEquals(EstadoEjemplar.DISPONIBLE, actual.get().estado());
                assertTrue(dao.bloquear(cn, Long.MAX_VALUE).isEmpty());
                assertEquals(0, dao.contarDetallesPendientes(cn, id));
                assertEquals(Optional.of(true), dao.libroActivo(cn, idLibro));
                assertEquals(Optional.empty(), dao.libroActivo(cn, Long.MAX_VALUE));

                // actualizar: cambia de libro y estado, y se vacían los opcionales
                assertEquals(1, dao.actualizar(cn, id, new EjemplarDao.Datos(
                        idLibro2, codigo, null, null, EstadoEjemplar.MANTENIMIENTO, null)));
                Ejemplar editado = dao.listar(cn, new FiltroEjemplares(codigo, null), 10).get(0);
                assertEquals(idLibro2, editado.idLibro());
                assertEquals(EstadoEjemplar.MANTENIMIENTO, editado.estado());
                assertNull(editado.ubicacion());
                assertNull(editado.fechaAdquisicion());
                assertEquals(0, dao.actualizar(cn, Long.MAX_VALUE, new EjemplarDao.Datos(
                        idLibro, "X-1", null, null, EstadoEjemplar.DISPONIBLE, null)));

                // código duplicado: error 1062 con el nombre de la restricción
                SQLException dup = assertThrows(SQLException.class, () -> dao.insertar(cn,
                        new EjemplarDao.Datos(idLibro, codigo, null, null, EstadoEjemplar.DISPONIBLE, null)));
                assertEquals(1062, dup.getErrorCode());
                assertTrue(dup.getMessage().contains("uq_ejemplares_codigo"));

                // siguiente número de código
                dao.insertar(cn, new EjemplarDao.Datos(
                        idLibro, "LL-999999", null, null, EstadoEjemplar.DISPONIBLE, null));
                assertTrue(dao.maximoNumeroCodigo(cn, "LL-") >= 999_999);
            } finally {
                cn.rollback();
            }
        }
    }

    @Test
    void detallesPendientesYAuditoriaConJson() throws Exception {
        try (Database db = new Database(config); Connection cn = db.getConnection()) {
            cn.setAutoCommit(false);
            try {
                Long idUsuario = null;
                try (Statement st = cn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT id_usuario FROM usuarios LIMIT 1")) {
                    if (rs.next()) {
                        idUsuario = rs.getLong(1);
                    }
                }
                assumeTrue(idUsuario != null, "Sin usuarios en la base: prueba omitida");

                String clave = "Prueba" + System.nanoTime();
                long idLibro = libroDePrueba(cn, "Titulo " + clave);
                EjemplarDao dao = new EjemplarDao();
                long idEjemplar = dao.insertar(cn, new EjemplarDao.Datos(
                        idLibro, "T-" + System.nanoTime(), null, null, EstadoEjemplar.DISPONIBLE, null));

                // un préstamo con este ejemplar sin devolver
                long idPrestamo;
                String sqlPrestamo = """
                        INSERT INTO prestamos (id_lector, id_usuario_responsable, fecha_vencimiento)
                        VALUES (?, ?, DATE_ADD(CURRENT_DATE(), INTERVAL 14 DAY))
                        """;
                try (PreparedStatement ps = cn.prepareStatement(sqlPrestamo, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, idUsuario);
                    ps.setLong(2, idUsuario);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        idPrestamo = keys.getLong(1);
                    }
                }
                try (PreparedStatement ps = cn.prepareStatement(
                        "INSERT INTO detalle_prestamo (id_prestamo, id_ejemplar) VALUES (?, ?)")) {
                    ps.setLong(1, idPrestamo);
                    ps.setLong(2, idEjemplar);
                    ps.executeUpdate();
                }
                assertEquals(1, dao.contarDetallesPendientes(cn, idEjemplar));

                // auditoría con detalle JSON (JSON_VALUE funciona en MariaDB y en MySQL 8)
                new AuditoriaDao().registrar(cn, idUsuario, "PRUEBA", "ejemplares", String.valueOf(idEjemplar),
                        "{\"estado_anterior\":\"DISPONIBLE\",\"estado_nuevo\":\"PERDIDO\"}");
                try (PreparedStatement ps = cn.prepareStatement("""
                        SELECT JSON_VALUE(detalle, '$.estado_nuevo') FROM auditoria
                        WHERE accion = 'PRUEBA' AND id_registro = ?
                        ORDER BY id_auditoria DESC LIMIT 1
                        """)) {
                    ps.setString(1, String.valueOf(idEjemplar));
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("PERDIDO", rs.getString(1));
                    }
                }
            } finally {
                cn.rollback();
            }
        }
    }
}