package com.librolink.dao;

import com.librolink.model.Editorial;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class EditorialDao {

    public List<Editorial> listar(Connection cn, String texto, boolean incluirInactivas) throws SQLException {
        String patron = Sql.patronContiene(texto);
        StringBuilder sql = new StringBuilder(
                "SELECT id_editorial, nombre, pais, sitio_web, estado FROM editoriales WHERE 1 = 1");
        if (patron != null) {
            sql.append(" AND (nombre LIKE ? OR pais LIKE ?)");
        }
        if (!incluirInactivas) {
            sql.append(" AND estado = 'ACTIVA'");
        }
        sql.append(" ORDER BY nombre");

        List<Editorial> lista = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            if (patron != null) {
                ps.setString(1, patron);
                ps.setString(2, patron);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Editorial(
                            rs.getLong("id_editorial"),
                            rs.getString("nombre"),
                            rs.getString("pais"),
                            rs.getString("sitio_web"),
                            "ACTIVA".equals(rs.getString("estado"))));
                }
            }
        }
        return lista;
    }

    public long insertar(Connection cn, String nombre, String pais, String sitioWeb) throws SQLException {
        String sql = "INSERT INTO editoriales (nombre, pais, sitio_web) VALUES (?, ?, ?)";
        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setString(2, pais);
            ps.setString(3, sitioWeb);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public int actualizar(Connection cn, long id, String nombre, String pais, String sitioWeb) throws SQLException {
        String sql = "UPDATE editoriales SET nombre = ?, pais = ?, sitio_web = ? WHERE id_editorial = ?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setString(2, pais);
            ps.setString(3, sitioWeb);
            ps.setLong(4, id);
            return ps.executeUpdate();
        }
    }

    public int cambiarEstado(Connection cn, long id, boolean activa) throws SQLException {
        String sql = "UPDATE editoriales SET estado = ? WHERE id_editorial = ?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, activa ? "ACTIVA" : "INACTIVA");
            ps.setLong(2, id);
            return ps.executeUpdate();
        }
    }
}