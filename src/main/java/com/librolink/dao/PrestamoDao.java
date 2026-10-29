package com.librolink.dao;

import com.librolink.model.DetallePrestamo;
import com.librolink.model.EstadoEjemplar;
import com.librolink.model.FiltroPrestamos;
import com.librolink.model.Prestamo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Acceso a prestamos y detalle_prestamo. Recibe la Connection para que el servicio controle la transacción. */
public final class PrestamoDao {

    public record Base(long idLector, Prestamo.Estado estado) { }

    public record EjemplarBloqueado(long idEjemplar, long idLibro, String codigo, EstadoEjemplar estado) { }

    /** Lectura sin bloqueo de un ejemplar, para validar al armar el préstamo. */
    public record ConsultaEjemplar(long idEjemplar, String codigo, String titulo, EstadoEjemplar estado,
                                   boolean libroActivo, long pendientes) { }

    // ---------- consultas ----------

    public List<Prestamo> listar(Connection cn, FiltroPrestamos f, int limite) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id_prestamo, p.id_lector, CONCAT_WS(' ', l.nombre, l.apellido) AS lector, l.numero_socio,
                       CONCAT_WS(' ', r.nombre, r.apellido) AS responsable, p.fecha_prestamo,
                       p.fecha_vencimiento, p.estado, p.observaciones,
                       (SELECT COUNT(*) FROM detalle_prestamo d WHERE d.id_prestamo = p.id_prestamo) AS total,
                       (SELECT COUNT(*) FROM detalle_prestamo d
                         WHERE d.id_prestamo = p.id_prestamo AND d.estado = 'PRESTADO') AS pendientes
                FROM prestamos p
                JOIN usuarios l ON l.id_usuario = p.id_lector
                JOIN usuarios r ON r.id_usuario = p.id_usuario_responsable
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();

        String patron = Sql.patronContiene(f.texto());
        if (patron != null) {
            sql.append("""
                    AND (CONCAT_WS(' ', l.nombre, l.apellido) LIKE ? OR l.numero_socio LIKE ? OR l.email LIKE ?
                         OR EXISTS (SELECT 1 FROM detalle_prestamo d
                                    JOIN ejemplares ep ON ep.id_ejemplar = d.id_ejemplar
                                    WHERE d.id_prestamo = p.id_prestamo AND ep.codigo_inventario LIKE ?)
                    """);
            params.addAll(List.of(patron, patron, patron, patron));
            String texto = f.texto().trim();
            if (texto.matches("\\d{1,15}")) {
                sql.append(" OR p.id_prestamo = ?");
                params.add(Long.parseLong(texto));
            }
            sql.append(")");
        }

        switch (f.estado()) {
            case ACTIVOS -> sql.append(" AND p.estado = 'ACTIVO'");
            case ATRASADOS -> sql.append("""
                     AND p.estado = 'ACTIVO' AND p.fecha_vencimiento < CURRENT_DATE()
                     AND EXISTS (SELECT 1 FROM detalle_prestamo d
                                 WHERE d.id_prestamo = p.id_prestamo AND d.estado = 'PRESTADO')
                    """);
            case FINALIZADOS -> sql.append(" AND p.estado = 'FINALIZADO'");
            case CANCELADOS -> sql.append(" AND p.estado = 'CANCELADO'");
            case TODOS -> { }
        }
        sql.append(" ORDER BY p.fecha_prestamo DESC, p.id_prestamo DESC LIMIT ?");
        params.add(limite);

        List<Prestamo> lista = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Prestamo(
                            rs.getLong("id_prestamo"),
                            rs.getLong("id_lector"),
                            rs.getString("lector"),
                            rs.getString("numero_socio"),
                            rs.getString("responsable"),
                            rs.getTimestamp("fecha_prestamo").toLocalDateTime(),
                            rs.getDate("fecha_vencimiento").toLocalDate(),
                            Prestamo.Estado.valueOf(rs.getString("estado")),
                            rs.getString("observaciones"),
                            rs.getLong("total"),
                            rs.getLong("pendientes")));
                }
            }
        }
        return lista;
    }

    public List<DetallePrestamo> detalles(Connection cn, long idPrestamo) throws SQLException {
        String sql = """
                SELECT d.id_detalle, d.id_prestamo, d.id_ejemplar, ep.codigo_inventario, l.titulo,
                       d.fecha_devolucion_real, d.estado, d.observaciones
                FROM detalle_prestamo d
                JOIN ejemplares ep ON ep.id_ejemplar = d.id_ejemplar
                JOIN libros l ON l.id_libro = ep.id_libro
                WHERE d.id_prestamo = ?
                ORDER BY d.id_detalle
                """;
        List<DetallePrestamo> lista = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, idPrestamo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp devolucion = rs.getTimestamp("fecha_devolucion_real");
                    lista.add(new DetallePrestamo(
                            rs.getLong("id_detalle"),
                            rs.getLong("id_prestamo"),
                            rs.getLong("id_ejemplar"),
                            rs.getString("codigo_inventario"),
                            rs.getString("titulo"),
                            devolucion == null ? null : devolucion.toLocalDateTime(),
                            DetallePrestamo.Estado.valueOf(rs.getString("estado")),
                            rs.getString("observaciones")));
                }
            }
        }
        return lista;
    }

    public Optional<ConsultaEjemplar> consultarEjemplar(Connection cn, String codigo) throws SQLException {
        String sql = """
                SELECT ep.id_ejemplar, ep.codigo_inventario, ep.estado, l.titulo, l.estado AS estado_libro,
                       (SELECT COUNT(*) FROM detalle_prestamo d
                         WHERE d.id_ejemplar = ep.id_ejemplar AND d.estado = 'PRESTADO') AS pendientes
                FROM ejemplares ep
                JOIN libros l ON l.id_libro = ep.id_libro
                WHERE ep.codigo_inventario = ?
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ConsultaEjemplar(
                        rs.getLong("id_ejemplar"),
                        rs.getString("codigo_inventario"),
                        rs.getString("titulo"),
                        EstadoEjemplar.valueOf(rs.getString("estado")),
                        "ACTIVO".equals(rs.getString("estado_libro")),
                        rs.getLong("pendientes")));
            }
        }
    }

    public long contarActivos(Connection cn, long idLector) throws SQLException {
        return contar(cn, "SELECT COUNT(*) FROM prestamos WHERE id_lector = ? AND estado = 'ACTIVO'", idLector);
    }

    public long contarAtrasados(Connection cn, long idLector) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM prestamos p
                WHERE p.id_lector = ? AND p.estado = 'ACTIVO' AND p.fecha_vencimiento < CURRENT_DATE()
                  AND EXISTS (SELECT 1 FROM detalle_prestamo d
                              WHERE d.id_prestamo = p.id_prestamo AND d.estado = 'PRESTADO')
                """;
        return contar(cn, sql, idLector);
    }

    public long contarPendientes(Connection cn, long idPrestamo) throws SQLException {
        return contar(cn,
                "SELECT COUNT(*) FROM detalle_prestamo WHERE id_prestamo = ? AND estado = 'PRESTADO'", idPrestamo);
    }

    private long contar(Connection cn, String sql, long parametro) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // ---------- bloqueos (dentro de una transacción) ----------

    public Optional<Base> bloquearPrestamo(Connection cn, long idPrestamo) throws SQLException {
        String sql = "SELECT id_lector, estado FROM prestamos WHERE id_prestamo = ? FOR UPDATE";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, idPrestamo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new Base(rs.getLong("id_lector"), Prestamo.Estado.valueOf(rs.getString("estado"))))
                        : Optional.empty();
            }
        }
    }

    /** Bloquea solo las filas de detalle del préstamo (sin arrastrar ejemplares ni libros). */
    public void bloquearDetalles(Connection cn, long idPrestamo) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT id_detalle FROM detalle_prestamo WHERE id_prestamo = ? FOR UPDATE")) {
            ps.setLong(1, idPrestamo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // solo se necesita tomar el bloqueo
                }
            }
        }
    }

    public Optional<EjemplarBloqueado> bloquearEjemplarPorCodigo(Connection cn, String codigo) throws SQLException {
        String sql = """
                SELECT id_ejemplar, id_libro, codigo_inventario, estado
                FROM ejemplares WHERE codigo_inventario = ? FOR UPDATE
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new EjemplarBloqueado(
                        rs.getLong("id_ejemplar"), rs.getLong("id_libro"), rs.getString("codigo_inventario"),
                        EstadoEjemplar.valueOf(rs.getString("estado"))));
            }
        }
    }

    /**
     * Toma un bloqueo compartido sobre el libro: la desactivación de un libro (que lo bloquea de forma exclusiva)
     * espera a que termine este préstamo. LOCK IN SHARE MODE funciona en MariaDB y en MySQL 8.
     */
    public boolean libroActivoCompartido(Connection cn, long idLibro) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT estado FROM libros WHERE id_libro = ? LOCK IN SHARE MODE")) {
            ps.setLong(1, idLibro);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "ACTIVO".equals(rs.getString(1));
            }
        }
    }

    // ---------- escrituras ----------

    /** El vencimiento se calcula en la base (hoy + días) para que cumpla el CHECK del esquema. */
    public long insertarPrestamo(Connection cn, long idLector, long idResponsable, int dias,
                                 String observaciones) throws SQLException {
        String sql = """
                INSERT INTO prestamos (id_lector, id_usuario_responsable, fecha_vencimiento, observaciones)
                VALUES (?, ?, DATE_ADD(CURRENT_DATE(), INTERVAL ? DAY), ?)
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, idLector);
            ps.setLong(2, idResponsable);
            ps.setInt(3, dias);
            ps.setString(4, observaciones);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public long insertarDetalle(Connection cn, long idPrestamo, long idEjemplar) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(
                "INSERT INTO detalle_prestamo (id_prestamo, id_ejemplar) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, idPrestamo);
            ps.setLong(2, idEjemplar);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** Pasa de DISPONIBLE a PRESTADO. Devuelve 0 si el ejemplar ya no estaba disponible. */
    public int reservarEjemplar(Connection cn, long idEjemplar) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(
                "UPDATE ejemplares SET estado = 'PRESTADO' WHERE id_ejemplar = ? AND estado = 'DISPONIBLE'")) {
            ps.setLong(1, idEjemplar);
            return ps.executeUpdate();
        }
    }

    public int cambiarEstadoEjemplar(Connection cn, long idEjemplar, EstadoEjemplar nuevo) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(
                "UPDATE ejemplares SET estado = ? WHERE id_ejemplar = ?")) {
            ps.setString(1, nuevo.name());
            ps.setLong(2, idEjemplar);
            return ps.executeUpdate();
        }
    }

    /** Cierra un detalle pendiente (DEVUELTO o PERDIDO). Devuelve 0 si ya no estaba pendiente. */
    public int cerrarDetalle(Connection cn, long idDetalle, DetallePrestamo.Estado estado,
                             String observaciones) throws SQLException {
        String sql = """
                UPDATE detalle_prestamo
                SET estado = ?, fecha_devolucion_real = NOW(), observaciones = ?
                WHERE id_detalle = ? AND estado = 'PRESTADO'
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, estado.name());
            ps.setString(2, observaciones);
            ps.setLong(3, idDetalle);
            return ps.executeUpdate();
        }
    }

    public int actualizarEstado(Connection cn, long idPrestamo, Prestamo.Estado estado) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("UPDATE prestamos SET estado = ? WHERE id_prestamo = ?")) {
            ps.setString(1, estado.name());
            ps.setLong(2, idPrestamo);
            return ps.executeUpdate();
        }
    }

    /** Marca el préstamo como CANCELADO y agrega la nota a sus observaciones (máximo 500 caracteres). */
    public int cancelarPrestamo(Connection cn, long idPrestamo, String nota) throws SQLException {
        String sql = """
                UPDATE prestamos
                SET estado = 'CANCELADO', observaciones = LEFT(CONCAT_WS(' | ', observaciones, ?), 500)
                WHERE id_prestamo = ?
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, nota);
            ps.setLong(2, idPrestamo);
            return ps.executeUpdate();
        }
    }
}