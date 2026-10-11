package com.librolink.dao;

import com.librolink.model.ResumenPanel.Actividad;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Consultas de solo lectura para el panel. Los SQL son constantes: no reciben texto del usuario. */
public final class DashboardDao {

    public long titulosActivos(Connection cn) throws SQLException {
        return contar(cn, "SELECT COUNT(*) FROM libros WHERE estado = 'ACTIVO'");
    }

    public long ejemplaresDisponibles(Connection cn) throws SQLException {
        return contar(cn, """
                SELECT COUNT(*) FROM ejemplares e
                JOIN libros l ON l.id_libro = e.id_libro
                WHERE e.estado = 'DISPONIBLE' AND l.estado = 'ACTIVO'
                """);
    }

    public long prestamosActivos(Connection cn) throws SQLException {
        return contar(cn, "SELECT COUNT(*) FROM prestamos WHERE estado = 'ACTIVO'");
    }

    /** Usa la vista del esquema: atrasado = vencimiento anterior a hoy con ejemplares pendientes. */
    public long prestamosAtrasados(Connection cn) throws SQLException {
        return contar(cn, "SELECT COUNT(*) FROM v_prestamos_pendientes WHERE estado_calculado = 'ATRASADO'");
    }

    public List<Actividad> actividadReciente(Connection cn, int limite) throws SQLException {
        String sql = """
                SELECT a.fecha, a.accion, a.entidad, CONCAT(u.nombre, ' ', u.apellido) AS usuario
                FROM auditoria a
                LEFT JOIN usuarios u ON u.id_usuario = a.id_usuario
                ORDER BY a.id_auditoria DESC
                LIMIT ?
                """;
        List<Actividad> lista = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Actividad(
                            rs.getTimestamp("fecha").toLocalDateTime(),
                            rs.getString("accion"),
                            rs.getString("entidad"),
                            rs.getString("usuario")));
                }
            }
        }
        return lista;
    }

    private long contar(Connection cn, String sql) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}