package com.librolink.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

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
}