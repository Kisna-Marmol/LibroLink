package com.librolink.dao;

import com.librolink.model.FiltroAuditoria;
import com.librolink.model.RegistroAuditoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public final class AuditoriaDao {

    /** detalleJson debe ser JSON válido o null. No incluyas texto escrito por el usuario sin escapar. */
    public void registrar(Connection cn, Long idUsuario, String accion, String entidad,
                          String idRegistro, String detalleJson) throws SQLException {
        String sql = """
                INSERT INTO auditoria (id_usuario, accion, entidad, id_registro, detalle)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            if (idUsuario == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, idUsuario);
            }
            ps.setString(2, accion);
            ps.setString(3, entidad);
            if (idRegistro == null) {
                ps.setNull(4, Types.VARCHAR);
            } else {
                ps.setString(4, idRegistro);
            }
            if (detalleJson == null) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, detalleJson);
            }
            ps.executeUpdate();
        }
    }

    /** Del más reciente al más antiguo. */
    public List<RegistroAuditoria> listar(Connection cn, FiltroAuditoria f, int limite,
                                          int desplazamiento) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT a.id_auditoria, a.fecha, CONCAT_WS(' ', u.nombre, u.apellido) AS usuario, u.email,
                       a.accion, a.entidad, a.id_registro, a.detalle
                FROM auditoria a
                LEFT JOIN usuarios u ON u.id_usuario = a.id_usuario
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (f.desde() != null) {
            sql.append(" AND a.fecha >= ?");
            params.add(Timestamp.valueOf(f.desde().atStartOfDay()));
        }
        if (f.hasta() != null) {
            sql.append(" AND a.fecha < ?");
            params.add(Timestamp.valueOf(f.hasta().plusDays(1).atStartOfDay()));
        }
        String patron = Sql.patronContiene(f.usuario());
        if (patron != null) {
            sql.append(" AND (CONCAT_WS(' ', u.nombre, u.apellido) LIKE ? OR u.email LIKE ?)");
            params.add(patron);
            params.add(patron);
        }
        if (f.accion() != null) {
            sql.append(" AND a.accion = ?");
            params.add(f.accion());
        }
        if (f.entidad() != null) {
            sql.append(" AND a.entidad = ?");
            params.add(f.entidad());
        }
        sql.append(" ORDER BY a.id_auditoria DESC LIMIT ? OFFSET ?");
        params.add(limite);
        params.add(desplazamiento);

        List<RegistroAuditoria> lista = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String usuario = rs.getString("usuario"); // CONCAT_WS devuelve '' (no NULL) sin usuario
                    lista.add(new RegistroAuditoria(
                            rs.getLong("id_auditoria"),
                            rs.getTimestamp("fecha").toLocalDateTime(),
                            usuario == null || usuario.isBlank() ? null : usuario,
                            rs.getString("email"),
                            rs.getString("accion"),
                            rs.getString("entidad"),
                            rs.getString("id_registro"),
                            rs.getString("detalle")));
                }
            }
        }
        return lista;
    }

    public List<String> acciones(Connection cn) throws SQLException {
        return valoresDistintos(cn, "SELECT DISTINCT accion FROM auditoria ORDER BY accion");
    }

    public List<String> entidades(Connection cn) throws SQLException {
        return valoresDistintos(cn, "SELECT DISTINCT entidad FROM auditoria ORDER BY entidad");
    }

    private List<String> valoresDistintos(Connection cn, String sql) throws SQLException {
        List<String> lista = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(rs.getString(1));
            }
        }
        return lista;
    }
}