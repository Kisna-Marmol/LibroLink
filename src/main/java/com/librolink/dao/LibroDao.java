package com.librolink.dao;

import com.librolink.model.Autor;
import com.librolink.model.FiltroLibros;
import com.librolink.model.Libro;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class LibroDao {

    /** Datos ya validados y normalizados por el servicio. */
    public record Datos(String isbn, String titulo, String subtitulo, long idCategoria, Long idEditorial,
                        Integer anio, String idioma, String descripcion) { }

    public record Referencias(long idCategoria, Long idEditorial) { }

    public List<Libro> listar(Connection cn, FiltroLibros filtro, int limite) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT l.id_libro, l.isbn, l.titulo, l.subtitulo, l.id_categoria, c.nombre AS categoria,
                       l.id_editorial, e.nombre AS editorial, l.anio_publicacion, l.idioma,
                       l.descripcion, l.estado,
                       (SELECT COUNT(*) FROM ejemplares ep WHERE ep.id_libro = l.id_libro) AS total,
                       (SELECT COUNT(*) FROM ejemplares ep
                         WHERE ep.id_libro = l.id_libro AND ep.estado = 'DISPONIBLE') AS disponibles
                FROM libros l
                JOIN categorias c ON c.id_categoria = l.id_categoria
                LEFT JOIN editoriales e ON e.id_editorial = l.id_editorial
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();

        String patron = Sql.patronContiene(filtro.texto());
        if (patron != null) {
            // El ISBN se guarda sin guiones: se busca también con el texto sin guiones ni espacios.
            String sinGuiones = Sql.patronContiene(filtro.texto().replaceAll("[\\s-]", ""));
            String patronIsbn = sinGuiones != null ? sinGuiones : patron;
            sql.append("""
                    AND (l.titulo LIKE ? OR l.subtitulo LIKE ? OR l.isbn LIKE ?
                         OR c.nombre LIKE ? OR e.nombre LIKE ?
                         OR EXISTS (SELECT 1 FROM libro_autor la
                                    JOIN autores a ON a.id_autor = la.id_autor
                                    WHERE la.id_libro = l.id_libro
                                      AND CONCAT_WS(' ', a.nombre, a.apellido) LIKE ?))
                    """);
            params.addAll(List.of(patron, patron, patronIsbn, patron, patron, patron));
        }
        if (filtro.idCategoria() != null) {
            sql.append(" AND l.id_categoria = ?");
            params.add(filtro.idCategoria());
        }
        if (!filtro.incluirInactivos()) {
            sql.append(" AND l.estado = 'ACTIVO'");
        }
        sql.append(" ORDER BY l.titulo, l.id_libro LIMIT ?");
        params.add(limite);

        List<Libro> libros = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long idEd = rs.getLong("id_editorial");
                    Long idEditorial = rs.wasNull() ? null : idEd;
                    int a = rs.getInt("anio_publicacion");
                    Integer anio = rs.wasNull() ? null : a;
                    libros.add(new Libro(
                            rs.getLong("id_libro"),
                            rs.getString("isbn"),
                            rs.getString("titulo"),
                            rs.getString("subtitulo"),
                            rs.getLong("id_categoria"),
                            rs.getString("categoria"),
                            idEditorial,
                            rs.getString("editorial"),
                            anio,
                            rs.getString("idioma"),
                            rs.getString("descripcion"),
                            "ACTIVO".equals(rs.getString("estado")),
                            List.of(),
                            rs.getLong("total"),
                            rs.getLong("disponibles")));
                }
            }
        }
        cargarAutores(cn, libros);
        return libros;
    }

    private void cargarAutores(Connection cn, List<Libro> libros) throws SQLException {
        if (libros.isEmpty()) {
            return;
        }
        String marcadores = String.join(",", Collections.nCopies(libros.size(), "?"));
        String sql = """
                SELECT la.id_libro, a.id_autor, a.nombre, a.apellido, a.estado
                FROM libro_autor la
                JOIN autores a ON a.id_autor = la.id_autor
                WHERE la.id_libro IN (%s)
                ORDER BY COALESCE(a.apellido, a.nombre), a.nombre
                """.formatted(marcadores);

        Map<Long, List<Autor>> porLibro = new HashMap<>();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            for (int i = 0; i < libros.size(); i++) {
                ps.setLong(i + 1, libros.get(i).id());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Autor autor = new Autor(rs.getLong("id_autor"), rs.getString("nombre"),
                            rs.getString("apellido"), null, null, "ACTIVO".equals(rs.getString("estado")));
                    porLibro.computeIfAbsent(rs.getLong("id_libro"), k -> new ArrayList<>()).add(autor);
                }
            }
        }
        libros.replaceAll(l -> l.conAutores(porLibro.getOrDefault(l.id(), List.of())));
    }

    public long insertar(Connection cn, Datos d) throws SQLException {
        String sql = """
                INSERT INTO libros (isbn, titulo, subtitulo, id_categoria, id_editorial,
                                    anio_publicacion, idioma, descripcion)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, d.isbn());
            ps.setString(2, d.titulo());
            ps.setString(3, d.subtitulo());
            ps.setLong(4, d.idCategoria());
            Sql.setLongNullable(ps, 5, d.idEditorial());
            Sql.setIntNullable(ps, 6, d.anio());
            ps.setString(7, d.idioma());
            ps.setString(8, d.descripcion());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** Devuelve las filas afectadas: 0 significa que el id no existe. */
    public int actualizar(Connection cn, long id, Datos d) throws SQLException {
        String sql = """
                UPDATE libros
                SET isbn = ?, titulo = ?, subtitulo = ?, id_categoria = ?, id_editorial = ?,
                    anio_publicacion = ?, idioma = ?, descripcion = ?
                WHERE id_libro = ?
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, d.isbn());
            ps.setString(2, d.titulo());
            ps.setString(3, d.subtitulo());
            ps.setLong(4, d.idCategoria());
            Sql.setLongNullable(ps, 5, d.idEditorial());
            Sql.setIntNullable(ps, 6, d.anio());
            ps.setString(7, d.idioma());
            ps.setString(8, d.descripcion());
            ps.setLong(9, id);
            return ps.executeUpdate();
        }
    }

    public int cambiarEstado(Connection cn, long id, boolean activo) throws SQLException {
        String sql = "UPDATE libros SET estado = ? WHERE id_libro = ?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, activo ? "ACTIVO" : "INACTIVO");
            ps.setLong(2, id);
            return ps.executeUpdate();
        }
    }

    /** Bloquea la fila del libro hasta el fin de la transacción y devuelve sus referencias actuales. */
    public Optional<Referencias> bloquear(Connection cn, long id) throws SQLException {
        String sql = "SELECT id_categoria, id_editorial FROM libros WHERE id_libro = ? FOR UPDATE";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                long idCategoria = rs.getLong("id_categoria");
                long idEd = rs.getLong("id_editorial");
                Long idEditorial = rs.wasNull() ? null : idEd;
                return Optional.of(new Referencias(idCategoria, idEditorial));
            }
        }
    }

    public List<Long> idsAutores(Connection cn, long idLibro) throws SQLException {
        String sql = "SELECT id_autor FROM libro_autor WHERE id_libro = ?";
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, idLibro);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    /** Reemplaza el conjunto de autores del libro (ids sin repetir). Llamar dentro de una transacción. */
    public void reemplazarAutores(Connection cn, long idLibro, List<Long> idsAutores) throws SQLException {
        try (PreparedStatement del = cn.prepareStatement("DELETE FROM libro_autor WHERE id_libro = ?")) {
            del.setLong(1, idLibro);
            del.executeUpdate();
        }
        if (idsAutores.isEmpty()) {
            return;
        }
        try (PreparedStatement ins = cn.prepareStatement(
                "INSERT INTO libro_autor (id_libro, id_autor) VALUES (?, ?)")) {
            for (long idAutor : idsAutores) {
                ins.setLong(1, idLibro);
                ins.setLong(2, idAutor);
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    /** De los ids dados, devuelve los que existen y están activos. */
    public Set<Long> autoresActivos(Connection cn, Collection<Long> ids) throws SQLException {
        if (ids.isEmpty()) {
            return Set.of();
        }
        String marcadores = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT id_autor FROM autores WHERE estado = 'ACTIVO' AND id_autor IN (%s)"
                .formatted(marcadores);
        Set<Long> activos = new HashSet<>();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            int i = 1;
            for (long id : ids) {
                ps.setLong(i++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    activos.add(rs.getLong(1));
                }
            }
        }
        return activos;
    }

    public Optional<Boolean> categoriaActiva(Connection cn, long id) throws SQLException {
        return estadoDe(cn, "SELECT estado FROM categorias WHERE id_categoria = ?", id).map("ACTIVA"::equals);
    }

    public Optional<Boolean> editorialActiva(Connection cn, long id) throws SQLException {
        return estadoDe(cn, "SELECT estado FROM editoriales WHERE id_editorial = ?", id).map("ACTIVA"::equals);
    }

    private Optional<String> estadoDe(Connection cn, String sql, long id) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    public long contarEjemplaresPrestados(Connection cn, long idLibro) throws SQLException {
        String sql = "SELECT COUNT(*) FROM ejemplares WHERE id_libro = ? AND estado = 'PRESTADO'";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, idLibro);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}