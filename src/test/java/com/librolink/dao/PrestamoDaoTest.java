package com.librolink.dao;

import com.librolink.config.AppConfig;
import com.librolink.config.Database;
import com.librolink.model.DatosUsuario;
import com.librolink.model.DetallePrestamo;
import com.librolink.model.EstadoEjemplar;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.FiltroPrestamos;
import com.librolink.model.Prestamo;
import com.librolink.model.Rol;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integración: usa una transacción y hace rollback, así que no deja registros en la base. */
class PrestamoDaoTest {

    private static AppConfig config;

    @BeforeAll
    static void cargar() throws IOException {
        assumeTrue(Files.isRegularFile(AppConfig.DEFAULT_PATH),
                "Sin config/db.properties: prueba de integración omitida");
        config = AppConfig.load();
    }

    @Test
    void cicloDePrestamoYDevolucion() throws Exception {
        try (Database db = new Database(config); Connection cn = db.getConnection()) {
            cn.setAutoCommit(false);
            try {
                String clave = "prueba" + System.nanoTime();
                UsuarioDao usuarios = new UsuarioDao();
                long idLector = usuarios.insertarCompleto(cn, usuarios.idRol(cn, Rol.LECTOR), new DatosUsuario(
                        Rol.LECTOR, "T" + System.nanoTime(), "Lectora" + clave, "Prueba", clave + "@test.local",
                        null, EstadoUsuario.ACTIVO), "h");
                long idStaff = usuarios.insertarCompleto(cn, usuarios.idRol(cn, Rol.BIBLIOTECARIO), new DatosUsuario(
                        Rol.BIBLIOTECARIO, null, "Beto" + clave, "Prueba", clave + "b@test.local",
                        null, EstadoUsuario.ACTIVO), "h");

                long idCat = new CategoriaDao().insertar(cn, "Cat " + clave, null);
                long idLibro = new LibroDao().insertar(cn,
                        new LibroDao.Datos(null, "Titulo " + clave, null, idCat, null, null, null, null));
                EjemplarDao ejemplares = new EjemplarDao();
                String cod1 = "T1-" + System.nanoTime();
                String cod2 = "T2-" + System.nanoTime();
                long idE1 = ejemplares.insertar(cn, new EjemplarDao.Datos(
                        idLibro, cod1, null, null, EstadoEjemplar.DISPONIBLE, null));
                long idE2 = ejemplares.insertar(cn, new EjemplarDao.Datos(
                        idLibro, cod2, null, null, EstadoEjemplar.DISPONIBLE, null));

                PrestamoDao dao = new PrestamoDao();

                // comprobación previa y bloqueos
                PrestamoDao.ConsultaEjemplar q = dao.consultarEjemplar(cn, cod1).orElseThrow();
                assertEquals(idE1, q.idEjemplar());
                assertEquals(EstadoEjemplar.DISPONIBLE, q.estado());
                assertTrue(q.libroActivo());
                assertEquals(0, q.pendientes());
                assertTrue(dao.consultarEjemplar(cn, "NO-EXISTE-" + clave).isEmpty());
                PrestamoDao.EjemplarBloqueado bloqueado = dao.bloquearEjemplarPorCodigo(cn, cod1).orElseThrow();
                assertEquals(idLibro, bloqueado.idLibro());
                assertTrue(dao.libroActivoCompartido(cn, idLibro));

                // préstamo con dos ejemplares
                long idP = dao.insertarPrestamo(cn, idLector, idStaff, 14, "obs");
                long idD1 = dao.insertarDetalle(cn, idP, idE1);
                long idD2 = dao.insertarDetalle(cn, idP, idE2);
                assertEquals(1, dao.reservarEjemplar(cn, idE1));
                assertEquals(1, dao.reservarEjemplar(cn, idE2));
                assertEquals(0, dao.reservarEjemplar(cn, idE1)); // ya no está disponible
                assertEquals(1, dao.contarActivos(cn, idLector));
                assertEquals(0, dao.contarAtrasados(cn, idLector));
                assertEquals(2, dao.contarPendientes(cn, idP));

                // listado y filtros
                List<Prestamo> lista = dao.listar(cn,
                        new FiltroPrestamos("Lectora" + clave, FiltroPrestamos.Estado.TODOS), 50);
                assertEquals(1, lista.size());
                Prestamo p = lista.get(0);
                assertEquals(idP, p.id());
                assertEquals(Prestamo.Estado.ACTIVO, p.estado());
                assertEquals(2, p.totalEjemplares());
                assertEquals(2, p.pendientes());
                assertTrue(p.fechaVencimiento().isAfter(LocalDate.now()));
                assertEquals(1, dao.listar(cn, new FiltroPrestamos(cod2, FiltroPrestamos.Estado.ACTIVOS), 50).size());
                assertEquals(1, dao.listar(cn, new FiltroPrestamos(String.valueOf(idP), FiltroPrestamos.Estado.TODOS), 50)
                        .stream().filter(x -> x.id() == idP).count());
                assertTrue(dao.listar(cn, new FiltroPrestamos("Lectora" + clave, FiltroPrestamos.Estado.ATRASADOS), 50)
                        .isEmpty());
                assertTrue(dao.listar(cn, new FiltroPrestamos("Lectora" + clave, FiltroPrestamos.Estado.FINALIZADOS), 50)
                        .isEmpty());

                // detalles y bloqueo del préstamo
                List<DetallePrestamo> detalles = dao.detalles(cn, idP);
                assertEquals(2, detalles.size());
                assertEquals(DetallePrestamo.Estado.PRESTADO, detalles.get(0).estado());
                assertEquals(cod1, detalles.get(0).codigo());
                assertEquals("Titulo " + clave, detalles.get(0).titulo());
                assertNull(detalles.get(0).fechaDevolucion());
                PrestamoDao.Base base = dao.bloquearPrestamo(cn, idP).orElseThrow();
                assertEquals(idLector, base.idLector());
                assertEquals(Prestamo.Estado.ACTIVO, base.estado());
                assertTrue(dao.bloquearPrestamo(cn, Long.MAX_VALUE).isEmpty());
                dao.bloquearDetalles(cn, idP);

                // devolución parcial
                assertEquals(1, dao.cerrarDetalle(cn, idD1, DetallePrestamo.Estado.DEVUELTO, null));
                dao.cambiarEstadoEjemplar(cn, idE1, EstadoEjemplar.DISPONIBLE);
                assertEquals(0, dao.cerrarDetalle(cn, idD1, DetallePrestamo.Estado.DEVUELTO, null)); // ya cerrado
                assertEquals(1, dao.contarPendientes(cn, idP));
                DetallePrestamo devuelto = dao.detalles(cn, idP).get(0);
                assertEquals(DetallePrestamo.Estado.DEVUELTO, devuelto.estado());
                assertNotNull(devuelto.fechaDevolucion());

                // atraso: se retrasa el préstamo con SQL directo
                try (PreparedStatement ps = cn.prepareStatement("""
                        UPDATE prestamos
                        SET fecha_prestamo = DATE_SUB(NOW(), INTERVAL 10 DAY),
                            fecha_vencimiento = DATE_SUB(CURRENT_DATE(), INTERVAL 3 DAY)
                        WHERE id_prestamo = ?
                        """)) {
                    ps.setLong(1, idP);
                    ps.executeUpdate();
                }
                assertEquals(1, dao.contarAtrasados(cn, idLector));
                assertEquals(1, dao.listar(cn,
                        new FiltroPrestamos("Lectora" + clave, FiltroPrestamos.Estado.ATRASADOS), 50).size());

                // el último ejemplar se da por perdido y el préstamo finaliza
                assertEquals(1, dao.cerrarDetalle(cn, idD2, DetallePrestamo.Estado.PERDIDO, "perdido en prueba"));
                dao.cambiarEstadoEjemplar(cn, idE2, EstadoEjemplar.PERDIDO);
                assertEquals(0, dao.contarPendientes(cn, idP));
                dao.actualizarEstado(cn, idP, Prestamo.Estado.FINALIZADO);
                assertEquals(0, dao.contarActivos(cn, idLector));
                assertEquals(0, dao.contarAtrasados(cn, idLector));
                assertEquals(1, dao.listar(cn,
                        new FiltroPrestamos("Lectora" + clave, FiltroPrestamos.Estado.FINALIZADOS), 50).size());

                // cancelación
                long idP2 = dao.insertarPrestamo(cn, idLector, idStaff, 7, null);
                assertEquals(1, dao.cancelarPrestamo(cn, idP2, "Cancelado: error de registro"));
                List<Prestamo> cancelados = dao.listar(cn,
                        new FiltroPrestamos("Lectora" + clave, FiltroPrestamos.Estado.CANCELADOS), 50);
                assertEquals(1, cancelados.size());
                assertEquals("Cancelado: error de registro", cancelados.get(0).observaciones());

                // configuración de circulación
                Map<String, String> configuracion = new ConfiguracionDao().todas(cn);
                assertTrue(configuracion.containsKey("dias_prestamo"));
            } finally {
                cn.rollback();
            }
        }
    }
}