package com.librolink.dao;

import com.librolink.config.AppConfig;
import com.librolink.config.Database;
import com.librolink.model.DatosUsuario;
import com.librolink.model.DetallePrestamo;
import com.librolink.model.EstadoEjemplar;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.ResultadoReporte;
import com.librolink.model.Rol;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integración: usa una transacción y hace rollback, así que no deja registros en la base. */
class ReporteDaoTest {

    private static AppConfig config;

    @BeforeAll
    static void cargar() throws IOException {
        assumeTrue(Files.isRegularFile(AppConfig.DEFAULT_PATH),
                "Sin config/db.properties: prueba de integración omitida");
        config = AppConfig.load();
    }

    private static List<List<String>> filasCon(ResultadoReporte r, int columna, String valor) {
        return r.filas().stream().filter(f -> f.get(columna).equals(valor)).toList();
    }

    @Test
    void cadaReporteDevuelveLosDatosEsperados() throws Exception {
        try (Database db = new Database(config); Connection cn = db.getConnection()) {
            cn.setAutoCommit(false);
            try {
                String clave = "prueba" + System.nanoTime();
                String nombreLector = "Lectora" + clave + " Prueba";
                String titulo = "Titulo " + clave;

                // datos: un lector, un bibliotecario, un libro con 4 ejemplares
                UsuarioDao usuarios = new UsuarioDao();
                long idLector = usuarios.insertarCompleto(cn, usuarios.idRol(cn, Rol.LECTOR), new DatosUsuario(
                        Rol.LECTOR, "T" + System.nanoTime(), "Lectora" + clave, "Prueba", clave + "@test.local",
                        "9999-8888", EstadoUsuario.ACTIVO), "h");
                long idStaff = usuarios.insertarCompleto(cn, usuarios.idRol(cn, Rol.BIBLIOTECARIO), new DatosUsuario(
                        Rol.BIBLIOTECARIO, null, "Beto" + clave, "Prueba", clave + "b@test.local",
                        null, EstadoUsuario.ACTIVO), "h");
                long idCat = new CategoriaDao().insertar(cn, "Cat " + clave, null);
                long idLibro = new LibroDao().insertar(cn,
                        new LibroDao.Datos(null, titulo, null, idCat, null, null, null, null));

                EjemplarDao ejemplares = new EjemplarDao();
                String[] codigos = new String[4];
                long[] idsE = new long[4];
                for (int i = 0; i < 4; i++) {
                    codigos[i] = "R" + (i + 1) + "-" + System.nanoTime();
                    idsE[i] = ejemplares.insertar(cn, new EjemplarDao.Datos(
                            idLibro, codigos[i], "Estante " + i, null, EstadoEjemplar.DISPONIBLE, null));
                }

                // P1 activo con 2 ejemplares, P2 atrasado con 1, P3 cancelado con 1
                PrestamoDao pd = new PrestamoDao();
                long p1 = pd.insertarPrestamo(cn, idLector, idStaff, 14, null);
                for (int i = 0; i < 2; i++) {
                    pd.insertarDetalle(cn, p1, idsE[i]);
                    pd.reservarEjemplar(cn, idsE[i]);
                }
                long p2 = pd.insertarPrestamo(cn, idLector, idStaff, 14, null);
                pd.insertarDetalle(cn, p2, idsE[2]);
                pd.reservarEjemplar(cn, idsE[2]);
                try (PreparedStatement ps = cn.prepareStatement("""
                        UPDATE prestamos
                        SET fecha_prestamo = DATE_SUB(NOW(), INTERVAL 10 DAY),
                            fecha_vencimiento = DATE_SUB(CURRENT_DATE(), INTERVAL 3 DAY)
                        WHERE id_prestamo = ?
                        """)) {
                    ps.setLong(1, p2);
                    ps.executeUpdate();
                }
                long p3 = pd.insertarPrestamo(cn, idLector, idStaff, 7, null);
                long d3 = pd.insertarDetalle(cn, p3, idsE[3]);
                pd.cerrarDetalle(cn, d3, DetallePrestamo.Estado.DEVUELTO, "Cancelado");
                pd.cancelarPrestamo(cn, p3, "Cancelado: prueba");

                ReporteDao dao = new ReporteDao();
                LocalDate hoy = LocalDate.now();

                // 1. atrasos: solo el ejemplar del préstamo atrasado, con 3 días
                List<List<String>> atrasos = filasCon(dao.atrasos(cn, 5000), 1, nombreLector);
                assertEquals(1, atrasos.size());
                assertEquals(codigos[2], atrasos.get(0).get(5));
                assertEquals(titulo, atrasos.get(0).get(6));
                assertEquals("9999-8888", atrasos.get(0).get(4));
                assertEquals("3", atrasos.get(0).get(9));

                // 2. historial del lector: 4 movimientos
                ResultadoReporte historial = dao.historialLector(cn, idLector, nombreLector, 5000);
                assertEquals(4, historial.filas().size());
                assertEquals(2, filasCon(historial, 6, "Prestado").size());
                assertEquals(1, filasCon(historial, 6, "Atrasado").size());
                assertEquals(1, filasCon(historial, 6, "Cancelado").size());

                // 3. inventario por ejemplar, con y sin filtro de estado
                ResultadoReporte inventario = dao.inventarioPorEjemplar(cn, null, 5000);
                assertTrue(inventario.filas().stream().anyMatch(f -> f.get(0).equals(codigos[0])
                        && f.get(1).equals(titulo) && f.get(5).equals("Prestado")));
                assertTrue(dao.inventarioPorEjemplar(cn, EstadoEjemplar.DISPONIBLE, 5000).filas().stream()
                        .anyMatch(f -> f.get(0).equals(codigos[3])));
                assertFalse(dao.inventarioPorEjemplar(cn, EstadoEjemplar.PERDIDO, 5000).filas().stream()
                        .anyMatch(f -> f.get(0).equals(codigos[3])));

                // 4. inventario resumido: 4 ejemplares, 1 disponible, 3 prestados, 0 otros
                List<String> resumen = filasCon(dao.inventarioPorTitulo(cn, 5000), 0, titulo).get(0);
                assertEquals(List.of("4", "1", "3", "0"), resumen.subList(4, 8));

                // 5. préstamos por rango
                List<List<String>> enRango = filasCon(
                        dao.prestamosPorRango(cn, hoy.minusDays(30), hoy.plusDays(2), 5000), 2, nombreLector);
                assertEquals(3, enRango.size());
                assertEquals(1, filasCon(new ResultadoReporte(null, "", List.of(), enRango, false), 8, "Activo").size());
                assertEquals(1, filasCon(new ResultadoReporte(null, "", List.of(), enRango, false), 8, "Atrasado").size());
                assertEquals(1, filasCon(new ResultadoReporte(null, "", List.of(), enRango, false), 8, "Cancelado").size());
                List<String> activo = enRango.stream().filter(f -> f.get(8).equals("Activo")).findFirst().orElseThrow();
                assertEquals(List.of("2", "2"), activo.subList(6, 8)); // 2 ejemplares, 2 sin devolver
                assertTrue(filasCon(dao.prestamosPorRango(cn, hoy.minusDays(60), hoy.minusDays(20), 5000),
                        2, nombreLector).isEmpty());

                // 6. libros más solicitados: 3 préstamos de ejemplar (el cancelado no cuenta), 1 lector
                List<String> masSolicitado = filasCon(
                        dao.librosMasSolicitados(cn, hoy.minusDays(30), hoy.plusDays(2), 1000), 1, titulo).get(0);
                assertEquals("3", masSolicitado.get(4));
                assertEquals("1", masSolicitado.get(5));
                assertEquals(1, filasCon(dao.librosMasSolicitados(cn, null, null, 1000), 1, titulo).size());
            } finally {
                cn.rollback();
            }
        }
    }
}