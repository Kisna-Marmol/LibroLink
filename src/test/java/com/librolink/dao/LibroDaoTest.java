package com.librolink.dao;

import com.librolink.config.AppConfig;
import com.librolink.config.Database;
import com.librolink.model.FiltroLibros;
import com.librolink.model.Libro;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integración: usa una transacción y hace rollback, así que no deja registros en la base. */
class LibroDaoTest {

    private static AppConfig config;

    @BeforeAll
    static void cargar() throws IOException {
        assumeTrue(Files.isRegularFile(AppConfig.DEFAULT_PATH),
                "Sin config/db.properties: prueba de integración omitida");
        config = AppConfig.load();
    }

    /** ISBN-13 único y válido para no chocar con datos reales. */
    private static String isbnUnico() {
        String base = "978" + String.format("%09d", System.nanoTime() % 1_000_000_000L);
        int suma = 0;
        for (int i = 0; i < 12; i++) {
            suma += (base.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return base + ((10 - suma % 10) % 10);
    }

    private static void insertarEjemplar(Connection cn, long idLibro, String codigo, String estado)
            throws SQLException {
        String sql = "INSERT INTO ejemplares (id_libro, codigo_inventario, estado) VALUES (?, ?, ?)";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, idLibro);
            ps.setString(2, codigo);
            ps.setString(3, estado);
            ps.executeUpdate();
        }
    }

    @Test
    void cicloCompletoDeUnLibro() throws Exception {
        try (Database db = new Database(config); Connection cn = db.getConnection()) {
            cn.setAutoCommit(false);
            try {
                String clave = "Prueba" + System.nanoTime();
                String isbn = isbnUnico();

                long idCat = new CategoriaDao().insertar(cn, clave, null);
                long idEd = new EditorialDao().insertar(cn, clave, null, null);
                AutorDao autores = new AutorDao();
                long idA1 = autores.insertar(cn, "Nombre" + clave, "Uno", null, null);
                long idA2 = autores.insertar(cn, "Otro", "Dos" + clave, null, null);

                LibroDao dao = new LibroDao();
                long id = dao.insertar(cn, new LibroDao.Datos(
                        isbn, "Titulo " + clave, "Sub", idCat, idEd, 2020, "Español", "desc"));
                dao.reemplazarAutores(cn, id, List.of(idA1, idA2));

                // búsqueda por cada criterio
                assertTrue(contiene(dao, cn, new FiltroLibros("Titulo " + clave, null, false), id), "título");
                assertTrue(contiene(dao, cn, new FiltroLibros("978-" + isbn.substring(3), null, false), id),
                        "ISBN con guiones");
                assertTrue(contiene(dao, cn, new FiltroLibros("Dos" + clave, null, false), id), "autor");
                assertTrue(contiene(dao, cn, new FiltroLibros(clave, null, false), id), "categoría o editorial");
                assertFalse(contiene(dao, cn, new FiltroLibros("zzz-no-existe-" + clave, null, false), id));

                // filtro por categoría: la categoría es nueva, así que solo hay este libro
                List<Libro> porCategoria = dao.listar(cn, new FiltroLibros(null, idCat, false), 10);
                assertEquals(1, porCategoria.size());
                Libro libro = porCategoria.get(0);
                assertEquals(2, libro.autores().size());
                assertEquals(0, libro.ejemplaresTotal());

                // ejemplares: total y disponibles
                insertarEjemplar(cn, id, "T-" + clave + "-1", "DISPONIBLE");
                insertarEjemplar(cn, id, "T-" + clave + "-2", "PRESTADO");
                Libro conEjemplares = dao.listar(cn, new FiltroLibros(null, idCat, false), 10).get(0);
                assertEquals(2, conEjemplares.ejemplaresTotal());
                assertEquals(1, conEjemplares.ejemplaresDisponibles());
                assertEquals(1, dao.contarEjemplaresPrestados(cn, id));

                // referencias y autores
                Optional<LibroDao.Referencias> refs = dao.bloquear(cn, id);
                assertTrue(refs.isPresent());
                assertEquals(idCat, refs.get().idCategoria());
                assertEquals(idEd, refs.get().idEditorial());
                assertEquals(2, dao.idsAutores(cn, id).size());
                assertEquals(1, dao.autoresActivos(cn, List.of(idA1, Long.MAX_VALUE)).size());

                // actualizar y reemplazar autores
                assertEquals(1, dao.actualizar(cn, id, new LibroDao.Datos(
                        isbn, "Titulo " + clave, null, idCat, null, null, null, null)));
                dao.reemplazarAutores(cn, id, List.of(idA1));
                Libro editado = dao.listar(cn, new FiltroLibros(null, idCat, false), 10).get(0);
                assertEquals(1, editado.autores().size());
                assertNull(editado.idEditorial());
                assertNull(editado.anio());

                // ISBN duplicado: error 1062 con el nombre de la restricción
                SQLException dup = assertThrows(SQLException.class, () -> dao.insertar(cn,
                        new LibroDao.Datos(isbn, "Otro titulo", null, idCat, null, null, null, null)));
                assertEquals(1062, dup.getErrorCode());
                assertTrue(dup.getMessage().contains("uq_libros_isbn"));

                // desactivar
                assertEquals(1, dao.cambiarEstado(cn, id, false));
                assertTrue(dao.listar(cn, new FiltroLibros(null, idCat, false), 10).isEmpty());
                assertEquals(1, dao.listar(cn, new FiltroLibros(null, idCat, true), 10).size());
                assertEquals(0, dao.cambiarEstado(cn, Long.MAX_VALUE, true));
            } finally {
                cn.rollback();
            }
        }
    }

    private static boolean contiene(LibroDao dao, Connection cn, FiltroLibros f, long id) throws SQLException {
        return dao.listar(cn, f, 500).stream().anyMatch(l -> l.id() == id);
    }
}