package com.librolink.dao;

import com.librolink.model.Ejemplar;
import com.librolink.model.EstadoEjemplar;
import com.librolink.model.FiltroEjemplares;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class EjemplarDao {

    /** Datos ya validados y normalizados por el servicio. */
    public record Datos(long idLibro, String codigo, String ubicacion, LocalDate fechaAdquisicion,
                        EstadoEjemplar estado, String observaciones) { }

    /** Estado persistido de un ejemplar, leído con bloqueo. */
    public record Actual(long idLibro, EstadoEjemplar estado) { }

    public List<Ejemplar> listar(Connection cn, FiltroEjemplares filtro, int limite) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT ep.id_ejemplar, ep.id_libro, l.titulo, l.isbn, ep.codigo_inventario,
                       ep.ubicacion, ep.fecha_adquisicion, ep.estado, ep.observaciones
                FROM ejemplares ep
                JOIN libros l ON l.id_libro = ep.id_libro
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();

        String patron = Sql.patronContiene(filtro.texto());
        if (patron != null) {
            // El ISBN se guarda sin guiones: se busca también con el texto sin guiones ni espacios.
            String sinGuiones = Sql.patronContiene(filtro.texto().replaceAll("[\\s-]", ""));
            String patronIsbn = sinGuiones != null ? sinGuiones : patron;
            sql.append("""
                    AND (ep.codigo_inventario LIKE ? OR l.titulo LIKE ? OR l.isbn LIKE ? OR ep.ubicacion LIKE ?)
                    """);
            params.addAll(List.of(patron, patron, patronIsbn, patron));
        }
        if (filtro.estado() != null) {
            sql.append(" AND ep.estado = ?");
            params.add(filtro.estado().name());
        }
        sql.append(" ORDER BY ep.codigo_inventario LIMIT ?");
        params.add(limite);

        List<Ejemplar> lista = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date fecha = rs.getDate("fecha_adquisicion");
                    lista.add(new Ejemplar(
                            rs.getLong("id_ejemplar"),
                            rs.getLong("id_libro"),
                            rs.getString("titulo"),
                            rs.getString("isbn"),
                            rs.getString("codigo_inventario"),
                            rs.getString("ubicacion"),
                            fecha == null ? null : fecha.toLocalDate(),
                            EstadoEjemplar.valueOf(rs.getString("estado")),
                            rs.getString("observaciones")));
                }
            }
        }
        return lista;
    }

    public long insertar(Connection cn, Datos d) throws SQLException {
        String sql = """
                INSERT INTO ejemplares (id_libro, codigo_inventario, ubicacion, fecha_adquisicion,
                                        estado, observaciones)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, d.idLibro());
            ps.setString(2, d.codigo());
            ps.setString(3, d.ubicacion());
            Sql.setDateNullable(ps, 4, d.fechaAdquisicion());
            ps.setString(5, d.estado().name());
            ps.setString(6, d.observaciones());
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
                UPDATE ejemplares
                SET id_libro = ?, codigo_inventario = ?, ubicacion = ?, fecha_adquisicion = ?,
                    estado = ?, observaciones = ?
                WHERE id_ejemplar = ?
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, d.idLibro());
            ps.setString(2, d.codigo());
            ps.setString(3, d.ubicacion());
            Sql.setDateNullable(ps, 4, d.fechaAdquisicion());
            ps.setString(5, d.estado().name());
            ps.setString(6, d.observaciones());
            ps.setLong(7, id);
            return ps.executeUpdate();
        }
    }

    /** Bloquea la fila del ejemplar hasta el fin de la transacción y devuelve su libro y estado actuales. */
    public Optional<Actual> bloquear(Connection cn, long id) throws SQLException {
        String sql = "SELECT id_libro, estado FROM ejemplares WHERE id_ejemplar = ? FOR UPDATE";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Actual(rs.getLong("id_libro"),
                        EstadoEjemplar.valueOf(rs.getString("estado"))));
            }
        }
    }

    /** Detalles de préstamo sin devolver para este ejemplar (debería ser 0 o 1). */
    public long contarDetallesPendientes(Connection cn, long idEjemplar) throws SQLException {
        String sql = "SELECT COUNT(*) FROM detalle_prestamo WHERE id_ejemplar = ? AND estado = 'PRESTADO'";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, idEjemplar);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public Optional<Boolean> libroActivo(Connection cn, long idLibro) throws SQLException {
        String sql = "SELECT estado FROM libros WHERE id_libro = ?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, idLibro);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of("ACTIVO".equals(rs.getString(1))) : Optional.empty();
            }
        }
    }

    /** Mayor número usado entre los códigos con formato PREFIJO + dígitos (0 si no hay ninguno). */
    public long maximoNumeroCodigo(Connection cn, String prefijo) throws SQLException {
        String sql = """
                SELECT COALESCE(MAX(CAST(SUBSTRING(codigo_inventario, ?) AS UNSIGNED)), 0)
                FROM ejemplares
                WHERE codigo_inventario REGEXP ?
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, prefijo.length() + 1);
            ps.setString(2, "^" + prefijo + "[0-9]{1,9}$");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}