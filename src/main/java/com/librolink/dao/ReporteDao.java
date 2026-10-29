package com.librolink.dao;

import com.librolink.model.EstadoEjemplar;
import com.librolink.model.MovimientoHistorial;
import com.librolink.model.ResultadoReporte;
import com.librolink.model.TipoReporte;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Consultas de solo lectura de los reportes. Los valores salen ya formateados como texto. */
public final class ReporteDao {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }

    private static String fecha(Date valor) {
        return valor == null ? "" : FECHA.format(valor.toLocalDate());
    }

    private static String fechaHora(Timestamp valor) {
        return valor == null ? "" : FECHA_HORA.format(valor.toLocalDateTime());
    }

    private static String num(long valor) {
        return String.valueOf(valor);
    }

    // ---------- 1. préstamos atrasados ----------

    public ResultadoReporte atrasos(Connection cn, int limite) throws SQLException {
        String sql = """
                SELECT p.id_prestamo, CONCAT_WS(' ', u.nombre, u.apellido) AS lector, u.numero_socio, u.email,
                       u.telefono, ep.codigo_inventario, l.titulo, p.fecha_prestamo, p.fecha_vencimiento,
                       DATEDIFF(CURRENT_DATE(), p.fecha_vencimiento) AS dias
                FROM prestamos p
                JOIN usuarios u ON u.id_usuario = p.id_lector
                JOIN detalle_prestamo dp ON dp.id_prestamo = p.id_prestamo AND dp.estado = 'PRESTADO'
                JOIN ejemplares ep ON ep.id_ejemplar = dp.id_ejemplar
                JOIN libros l ON l.id_libro = ep.id_libro
                WHERE p.estado = 'ACTIVO' AND p.fecha_vencimiento < CURRENT_DATE()
                ORDER BY dias DESC, p.id_prestamo, ep.codigo_inventario
                LIMIT ?
                """;
        List<List<String>> filas = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    filas.add(List.of(
                            num(rs.getLong("id_prestamo")),
                            texto(rs.getString("lector")),
                            texto(rs.getString("numero_socio")),
                            texto(rs.getString("email")),
                            texto(rs.getString("telefono")),
                            texto(rs.getString("codigo_inventario")),
                            texto(rs.getString("titulo")),
                            fecha(rs.getDate("fecha_prestamo")),
                            fecha(rs.getDate("fecha_vencimiento")),
                            num(rs.getLong("dias"))));
                }
            }
        }
        return new ResultadoReporte(TipoReporte.ATRASOS, "Préstamos atrasados",
                List.of("Préstamo", "Lector", "Socio", "Correo", "Teléfono", "Ejemplar", "Libro",
                        "Prestado el", "Vencía el", "Días de atraso"),
                filas, filas.size() >= limite);
    }

    // ---------- 2. historial por lector ----------

    public ResultadoReporte historialLector(Connection cn, long idLector, String nombreLector,
                                            int limite) throws SQLException {
        LocalDate hoy = LocalDate.now();
        List<List<String>> filas = new ArrayList<>();
        for (MovimientoHistorial m : new HistorialDao().porLector(cn, idLector, limite)) {
            filas.add(List.of(
                    num(m.idPrestamo()),
                    FECHA.format(m.fechaPrestamo()),
                    FECHA.format(m.fechaVencimiento()),
                    texto(m.codigoEjemplar()),
                    texto(m.titulo()),
                    m.fechaDevolucion() == null ? "" : FECHA.format(m.fechaDevolucion()),
                    m.etiqueta(hoy)));
        }
        return new ResultadoReporte(TipoReporte.HISTORIAL_LECTOR, "Historial de " + nombreLector,
                List.of("Préstamo", "Fecha del préstamo", "Vence", "Ejemplar", "Libro", "Devuelto el", "Estado"),
                filas, filas.size() >= limite);
    }

    // ---------- 3. inventario por ejemplar ----------

    /** estado null = todos los estados. */
    public ResultadoReporte inventarioPorEjemplar(Connection cn, EstadoEjemplar estado,
                                                  int limite) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT ep.codigo_inventario, l.titulo, l.isbn, c.nombre AS categoria, ep.ubicacion,
                       ep.estado, ep.fecha_adquisicion, ep.observaciones
                FROM ejemplares ep
                JOIN libros l ON l.id_libro = ep.id_libro
                JOIN categorias c ON c.id_categoria = l.id_categoria
                WHERE 1 = 1
                """);
        if (estado != null) {
            sql.append(" AND ep.estado = ?");
        }
        sql.append(" ORDER BY l.titulo, ep.codigo_inventario LIMIT ?");

        List<List<String>> filas = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            int i = 1;
            if (estado != null) {
                ps.setString(i++, estado.name());
            }
            ps.setInt(i, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    filas.add(List.of(
                            texto(rs.getString("codigo_inventario")),
                            texto(rs.getString("titulo")),
                            texto(rs.getString("isbn")),
                            texto(rs.getString("categoria")),
                            texto(rs.getString("ubicacion")),
                            EstadoEjemplar.valueOf(rs.getString("estado")).etiqueta(),
                            fecha(rs.getDate("fecha_adquisicion")),
                            texto(rs.getString("observaciones"))));
                }
            }
        }
        return new ResultadoReporte(TipoReporte.INVENTARIO_EJEMPLARES, "Inventario por ejemplar",
                List.of("Código", "Libro", "ISBN", "Categoría", "Ubicación", "Estado", "Adquisición",
                        "Observaciones"),
                filas, filas.size() >= limite);
    }

    // ---------- 4. inventario resumido por título ----------

    public ResultadoReporte inventarioPorTitulo(Connection cn, int limite) throws SQLException {
        String sql = """
                SELECT l.titulo, l.isbn, c.nombre AS categoria, e.nombre AS editorial,
                       COUNT(ep.id_ejemplar) AS total,
                       COALESCE(SUM(CASE WHEN ep.estado = 'DISPONIBLE' THEN 1 ELSE 0 END), 0) AS disponibles,
                       COALESCE(SUM(CASE WHEN ep.estado = 'PRESTADO' THEN 1 ELSE 0 END), 0) AS prestados
                FROM libros l
                JOIN categorias c ON c.id_categoria = l.id_categoria
                LEFT JOIN editoriales e ON e.id_editorial = l.id_editorial
                LEFT JOIN ejemplares ep ON ep.id_libro = l.id_libro
                WHERE l.estado = 'ACTIVO'
                GROUP BY l.id_libro, l.titulo, l.isbn, c.nombre, e.nombre
                ORDER BY l.titulo, l.id_libro
                LIMIT ?
                """;
        List<List<String>> filas = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long total = rs.getLong("total");
                    long disponibles = rs.getLong("disponibles");
                    long prestados = rs.getLong("prestados");
                    filas.add(List.of(
                            texto(rs.getString("titulo")),
                            texto(rs.getString("isbn")),
                            texto(rs.getString("categoria")),
                            texto(rs.getString("editorial")),
                            num(total),
                            num(disponibles),
                            num(prestados),
                            num(total - disponibles - prestados)));
                }
            }
        }
        return new ResultadoReporte(TipoReporte.INVENTARIO_TITULOS, "Inventario resumido por título",
                List.of("Libro", "ISBN", "Categoría", "Editorial", "Ejemplares", "Disponibles", "Prestados",
                        "Otros estados"),
                filas, filas.size() >= limite);
    }

    // ---------- 5. préstamos por rango de fechas ----------

    /** Por fecha del préstamo; el día "hasta" se incluye completo. */
    public ResultadoReporte prestamosPorRango(Connection cn, LocalDate desde, LocalDate hasta,
                                              int limite) throws SQLException {
        String sql = """
                SELECT p.id_prestamo, p.fecha_prestamo, CONCAT_WS(' ', l.nombre, l.apellido) AS lector,
                       l.numero_socio, CONCAT_WS(' ', r.nombre, r.apellido) AS responsable,
                       p.fecha_vencimiento, p.estado,
                       (SELECT COUNT(*) FROM detalle_prestamo d WHERE d.id_prestamo = p.id_prestamo) AS total,
                       (SELECT COUNT(*) FROM detalle_prestamo d
                         WHERE d.id_prestamo = p.id_prestamo AND d.estado = 'PRESTADO') AS pendientes
                FROM prestamos p
                JOIN usuarios l ON l.id_usuario = p.id_lector
                JOIN usuarios r ON r.id_usuario = p.id_usuario_responsable
                WHERE p.fecha_prestamo >= ? AND p.fecha_prestamo < ?
                ORDER BY p.fecha_prestamo DESC, p.id_prestamo DESC
                LIMIT ?
                """;
        LocalDate hoy = LocalDate.now();
        List<List<String>> filas = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(desde.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
            ps.setInt(3, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long pendientes = rs.getLong("pendientes");
                    LocalDate vence = rs.getDate("fecha_vencimiento").toLocalDate();
                    String estado = switch (rs.getString("estado")) {
                        case "CANCELADO" -> "Cancelado";
                        case "FINALIZADO" -> "Finalizado";
                        default -> pendientes > 0 && vence.isBefore(hoy) ? "Atrasado" : "Activo";
                    };
                    filas.add(List.of(
                            num(rs.getLong("id_prestamo")),
                            fechaHora(rs.getTimestamp("fecha_prestamo")),
                            texto(rs.getString("lector")),
                            texto(rs.getString("numero_socio")),
                            texto(rs.getString("responsable")),
                            FECHA.format(vence),
                            num(rs.getLong("total")),
                            num(pendientes),
                            estado));
                }
            }
        }
        return new ResultadoReporte(TipoReporte.PRESTAMOS_RANGO,
                "Préstamos del " + FECHA.format(desde) + " al " + FECHA.format(hasta),
                List.of("Préstamo", "Fecha", "Lector", "Socio", "Registrado por", "Vence", "Ejemplares",
                        "Sin devolver", "Estado"),
                filas, filas.size() >= limite);
    }

    // ---------- 6. libros más solicitados ----------

    /** Cuenta cada ejemplar prestado, sin préstamos cancelados. desde y hasta son opcionales. */
    public ResultadoReporte librosMasSolicitados(Connection cn, LocalDate desde, LocalDate hasta,
                                                 int top) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT l.titulo, l.isbn, c.nombre AS categoria,
                       COUNT(dp.id_detalle) AS veces, COUNT(DISTINCT p.id_lector) AS lectores
                FROM detalle_prestamo dp
                JOIN prestamos p ON p.id_prestamo = dp.id_prestamo
                JOIN ejemplares ep ON ep.id_ejemplar = dp.id_ejemplar
                JOIN libros l ON l.id_libro = ep.id_libro
                JOIN categorias c ON c.id_categoria = l.id_categoria
                WHERE p.estado <> 'CANCELADO'
                """);
        List<Object> params = new ArrayList<>();
        if (desde != null) {
            sql.append(" AND p.fecha_prestamo >= ?");
            params.add(Timestamp.valueOf(desde.atStartOfDay()));
        }
        if (hasta != null) {
            sql.append(" AND p.fecha_prestamo < ?");
            params.add(Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
        }
        sql.append(" GROUP BY l.id_libro, l.titulo, l.isbn, c.nombre")
                .append(" ORDER BY veces DESC, l.titulo, l.id_libro LIMIT ?");
        params.add(top);

        List<List<String>> filas = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                int posicion = 1;
                while (rs.next()) {
                    filas.add(List.of(
                            num(posicion++),
                            texto(rs.getString("titulo")),
                            texto(rs.getString("isbn")),
                            texto(rs.getString("categoria")),
                            num(rs.getLong("veces")),
                            num(rs.getLong("lectores"))));
                }
            }
        }
        String periodo = desde == null && hasta == null ? "todo el período"
                : (desde == null ? "hasta el " + FECHA.format(hasta)
                : hasta == null ? "desde el " + FECHA.format(desde)
                : "del " + FECHA.format(desde) + " al " + FECHA.format(hasta));
        return new ResultadoReporte(TipoReporte.LIBROS_SOLICITADOS, "Libros más solicitados (" + periodo + ")",
                List.of("Posición", "Libro", "ISBN", "Categoría", "Veces prestado", "Lectores distintos"),
                filas, false);
    }
}