package com.librolink.dao;

import com.librolink.config.AppConfig;
import com.librolink.config.Database;
import com.librolink.dao.UsuarioDao.Credencial;
import com.librolink.model.DatosUsuario;
import com.librolink.model.EstadoEjemplar;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.FiltroUsuarios;
import com.librolink.model.MovimientoHistorial;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integración: usa una transacción y hace rollback, así que no deja registros en la base. */
class UsuarioDaoTest {

    private static AppConfig config;

    @BeforeAll
    static void cargar() throws IOException {
        assumeTrue(Files.isRegularFile(AppConfig.DEFAULT_PATH),
                "Sin config/db.properties: prueba de integración omitida");
        config = AppConfig.load();
    }

    private static List<Long> ids(List<Usuario> usuarios) {
        return usuarios.stream().map(Usuario::id).toList();
    }

    private static long insertarPrestamo(Connection cn, long lector, long responsable) throws SQLException {
        String sql = """
                INSERT INTO prestamos (id_lector, id_usuario_responsable, fecha_vencimiento)
                VALUES (?, ?, DATE_ADD(CURRENT_DATE(), INTERVAL 14 DAY))
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, lector);
            ps.setLong(2, responsable);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    @Test
    void cicloDeUsuariosYHistorial() throws Exception {
        try (Database db = new Database(config); Connection cn = db.getConnection()) {
            cn.setAutoCommit(false);
            try {
                UsuarioDao dao = new UsuarioDao();
                String clave = "prueba" + System.nanoTime();
                String socio = "T" + System.nanoTime();
                String emailLector = clave + "@test.local";
                long rolLector = dao.idRol(cn, Rol.LECTOR);

                long idLector = dao.insertarCompleto(cn, rolLector, new DatosUsuario(Rol.LECTOR, socio,
                        "Ana" + clave, "Prueba", emailLector, "9999-8888", EstadoUsuario.ACTIVO), "$2a$12$hash-de-prueba");
                long idStaff = dao.insertarCompleto(cn, dao.idRol(cn, Rol.BIBLIOTECARIO), new DatosUsuario(
                        Rol.BIBLIOTECARIO, null, "Beto" + clave, "Prueba", clave + "b@test.local", null,
                        EstadoUsuario.ACTIVO), "$2a$12$otro");

                // búsqueda por nombre, socio y correo, y filtros por rol y estado
                assertTrue(ids(dao.listar(cn, new FiltroUsuarios("Ana" + clave, null, null), 50)).contains(idLector));
                assertTrue(ids(dao.listar(cn, new FiltroUsuarios(socio, null, null), 50)).contains(idLector));
                assertTrue(ids(dao.listar(cn, new FiltroUsuarios(emailLector, null, null), 50)).contains(idLector));
                assertFalse(ids(dao.listar(cn, new FiltroUsuarios(clave, Rol.LECTOR, null), 50)).contains(idStaff));
                assertTrue(ids(dao.listar(cn, new FiltroUsuarios(clave, Rol.BIBLIOTECARIO, EstadoUsuario.ACTIVO), 50))
                        .contains(idStaff));
                assertFalse(ids(dao.listar(cn, new FiltroUsuarios(clave, null, EstadoUsuario.BLOQUEADO), 50))
                        .contains(idLector));

                // búsqueda por id (con y sin bloqueo) y por correo
                Credencial cred = dao.buscarPorId(cn, idLector, false).orElseThrow();
                assertEquals("$2a$12$hash-de-prueba", cred.passwordHash());
                assertEquals(Rol.LECTOR, cred.usuario().rol());
                assertEquals(socio, cred.usuario().numeroSocio());
                assertTrue(dao.buscarPorId(cn, idLector, true).isPresent());
                assertTrue(dao.buscarPorId(cn, Long.MAX_VALUE, true).isEmpty());
                assertEquals(idLector, dao.buscarPorEmail(cn, emailLector).orElseThrow().usuario().id());

                // actualizar datos y contraseña
                assertEquals(1, dao.actualizar(cn, idLector, rolLector, new DatosUsuario(Rol.LECTOR, socio,
                        "Ana", "Nueva", emailLector, null, EstadoUsuario.BLOQUEADO)));
                Usuario editado = dao.buscarPorId(cn, idLector, false).orElseThrow().usuario();
                assertEquals(EstadoUsuario.BLOQUEADO, editado.estado());
                assertEquals("Nueva", editado.apellido());
                assertNull(editado.telefono());
                assertEquals(1, dao.actualizarPasswordHash(cn, idLector, "$2a$12$nuevo"));
                assertEquals("$2a$12$nuevo", dao.buscarPorId(cn, idLector, false).orElseThrow().passwordHash());
                assertEquals(0, dao.actualizarPasswordHash(cn, Long.MAX_VALUE, "x"));

                // duplicados: error 1062 con el nombre de la restricción
                SQLException dupEmail = assertThrows(SQLException.class, () -> dao.insertarCompleto(cn, rolLector,
                        new DatosUsuario(Rol.LECTOR, "OTRO" + System.nanoTime(), "X", "Y", emailLector, null,
                                EstadoUsuario.ACTIVO), "h"));
                assertEquals(1062, dupEmail.getErrorCode());
                assertTrue(dupEmail.getMessage().contains("uq_usuarios_email"));

                SQLException dupSocio = assertThrows(SQLException.class, () -> dao.insertarCompleto(cn, rolLector,
                        new DatosUsuario(Rol.LECTOR, socio, "X", "Y", clave + "z@test.local", null,
                                EstadoUsuario.ACTIVO), "h"));
                assertEquals(1062, dupSocio.getErrorCode());
                assertTrue(dupSocio.getMessage().contains("uq_usuarios_numero_socio"));

                // administradores activos
                long antes = dao.contarAdministradoresActivosExcepto(cn, 0);
                long idAdmin = dao.insertarCompleto(cn, dao.idRol(cn, Rol.ADMINISTRADOR), new DatosUsuario(
                        Rol.ADMINISTRADOR, null, "Admin" + clave, "Prueba", clave + "c@test.local", null,
                        EstadoUsuario.ACTIVO), "h");
                assertEquals(antes + 1, dao.contarAdministradoresActivosExcepto(cn, 0));
                assertEquals(antes, dao.contarAdministradoresActivosExcepto(cn, idAdmin));

                // siguiente número de socio
                dao.insertarCompleto(cn, rolLector, new DatosUsuario(Rol.LECTOR, "S-999999", "Z", "Z",
                        clave + "d@test.local", null, EstadoUsuario.ACTIVO), "h");
                assertTrue(dao.maximoNumeroSocio(cn, "S-") >= 999_999);

                // préstamo con un ejemplar sin devolver: conteos e historial
                long idCat = new CategoriaDao().insertar(cn, "Cat " + clave, null);
                long idLibro = new LibroDao().insertar(cn,
                        new LibroDao.Datos(null, "Titulo " + clave, null, idCat, null, null, null, null));
                String codigo = "T-" + System.nanoTime();
                long idEjemplar = new EjemplarDao().insertar(cn, new EjemplarDao.Datos(
                        idLibro, codigo, null, null, EstadoEjemplar.PRESTADO, null));
                long idPrestamo = insertarPrestamo(cn, idLector, idStaff);
                try (PreparedStatement ps = cn.prepareStatement(
                        "INSERT INTO detalle_prestamo (id_prestamo, id_ejemplar) VALUES (?, ?)")) {
                    ps.setLong(1, idPrestamo);
                    ps.setLong(2, idEjemplar);
                    ps.executeUpdate();
                }
                assertEquals(1, dao.contarPrestamos(cn, idLector));
                assertEquals(1, dao.contarPrestamos(cn, idStaff)); // como responsable
                assertEquals(1, dao.contarPendientes(cn, idLector));

                List<MovimientoHistorial> historial = new HistorialDao().porLector(cn, idLector, 50);
                assertEquals(1, historial.size());
                assertEquals("PRESTADO", historial.get(0).estadoDetalle());
                assertEquals("Titulo " + clave, historial.get(0).titulo());
                assertEquals(codigo, historial.get(0).codigoEjemplar());
                assertNull(historial.get(0).fechaDevolucion());
                assertTrue(new HistorialDao().porLector(cn, idStaff, 50).isEmpty());
            } finally {
                cn.rollback();
            }
        }
    }
}