package com.librolink.dao;

import com.librolink.model.Autor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class AutorDao {

    public List<Autor> listar(Connection cn, String texto, boolean incluirInactivos) throws SQLException {
        String patron = Sql.patronContiene(texto);
        StringBuilder sql = new StringBuilder(
                "SELECT id_autor, nombre, apellido, nacionalidad, biografia, estado FROM autores WHERE 1 = 1");
        if (patron != null) {
            sql.append(" AND (CONCAT_WS(' ', nombre, apellido) LIKE ? OR nacionalidad LIKE ?)");
        }
        if (!incluirInactivos) {
            sql.append(" AND estado = 'ACTIVO'");
        }
        sql.append(" ORDER BY COALESCE(apellido, nombre), nombre");

        List<Autor> lista = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            if (patron != null) {
                ps.setString(1, patron);
                ps.setString(2, patron);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Autor(
                            rs.getLong("id_autor"),
                            rs.getString("nombre"),
                            rs.getString("apellido"),
                            rs.getString("nacionalidad"),
                            rs.getString("biografia"),
                            "ACTIVO".equals(rs.getString("estado"))));
                }
            }
        }
        return lista;
    }

    public long insertar(Connection cn, String nombre, String apellido,
                         String nacionalidad, String biografia) throws SQLException {
        String sql = "INSERT INTO autores (nombre, apellido, nacionalidad, biografia) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setString(2, apellido);
            ps.setString(3, nacionalidad);
            ps.setString(4, biografia);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** Devuelve las filas afectadas: 0 significa que el id no existe. */
    public int actualizar(Connection cn, long id, String nombre, String apellido,
                          String nacionalidad, String biografia) throws SQLException {
        String sql = "UPDATE autores SET nombre = ?, apellido = ?, nacionalidad = ?, biografia = ? WHERE id_autor = ?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setString(2, apellido);
            ps.setString(3, nacionalidad);
            ps.setString(4, biografia);
            ps.setLong(5, id);
            return ps.executeUpdate();
        }
    }

    public int cambiarEstado(Connection cn, long id, boolean activo) throws SQLException {
        String sql = "UPDATE autores SET estado = ? WHERE id_autor = ?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, activo ? "ACTIVO" : "INACTIVO");
            ps.setLong(2, id);
            return ps.executeUpdate();
        }
    }
}