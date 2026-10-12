package com.librolink.dao;

import com.librolink.model.Categoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class CategoriaDao {

    public List<Categoria> listar(Connection cn, String texto, boolean incluirInactivas) throws SQLException {
        String patron = Sql.patronContiene(texto);
        StringBuilder sql = new StringBuilder(
                "SELECT id_categoria, nombre, descripcion, estado FROM categorias WHERE 1 = 1");
        if (patron != null) {
            sql.append(" AND (nombre LIKE ? OR descripcion LIKE ?)");
        }
        if (!incluirInactivas) {
            sql.append(" AND estado = 'ACTIVA'");
        }
        sql.append(" ORDER BY nombre");

        List<Categoria> lista = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            if (patron != null) {
                ps.setString(1, patron);
                ps.setString(2, patron);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Categoria(
                            rs.getLong("id_categoria"),
                            rs.getString("nombre"),
                            rs.getString("descripcion"),
                            "ACTIVA".equals(rs.getString("estado"))));
                }
            }
        }
        return lista;
    }

    public long insertar(Connection cn, String nombre, String descripcion) throws SQLException {
        String sql = "INSERT INTO categorias (nombre, descripcion) VALUES (?, ?)";
        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setString(2, descripcion);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public int actualizar(Connection cn, long id, String nombre, String descripcion) throws SQLException {
        String sql = "UPDATE categorias SET nombre = ?, descripcion = ? WHERE id_categoria = ?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setString(2, descripcion);
            ps.setLong(3, id);
            return ps.executeUpdate();
        }
    }

    public int cambiarEstado(Connection cn, long id, boolean activa) throws SQLException {
        String sql = "UPDATE categorias SET estado = ? WHERE id_categoria = ?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, activa ? "ACTIVA" : "INACTIVA");
            ps.setLong(2, id);
            return ps.executeUpdate();
        }
    }
}