package com.librolink.dao;

import com.librolink.model.MovimientoHistorial;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public final class HistorialDao {

    /** Un movimiento por ejemplar prestado, del más reciente al más antiguo. */
    public List<MovimientoHistorial> porLector(Connection cn, long idLector, int limite) throws SQLException {
        String sql = """
                SELECT p.id_prestamo, p.fecha_prestamo, p.fecha_vencimiento, p.estado AS estado_prestamo,
                       l.titulo, ep.codigo_inventario, dp.fecha_devolucion_real, dp.estado AS estado_detalle
                FROM prestamos p
                JOIN detalle_prestamo dp ON dp.id_prestamo = p.id_prestamo
                JOIN ejemplares ep ON ep.id_ejemplar = dp.id_ejemplar
                JOIN libros l ON l.id_libro = ep.id_libro
                WHERE p.id_lector = ?
                ORDER BY p.fecha_prestamo DESC, p.id_prestamo DESC, dp.id_detalle
                LIMIT ?
                """;
        List<MovimientoHistorial> lista = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, idLector);
            ps.setInt(2, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date vence = rs.getDate("fecha_vencimiento");
                    Timestamp devolucion = rs.getTimestamp("fecha_devolucion_real");
                    lista.add(new MovimientoHistorial(
                            rs.getLong("id_prestamo"),
                            rs.getTimestamp("fecha_prestamo").toLocalDateTime(),
                            vence.toLocalDate(),
                            rs.getString("estado_prestamo"),
                            rs.getString("titulo"),
                            rs.getString("codigo_inventario"),
                            devolucion == null ? null : devolucion.toLocalDateTime(),
                            rs.getString("estado_detalle")));
                }
            }
        }
        return lista;
    }
}
