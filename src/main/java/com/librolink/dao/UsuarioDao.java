package com.librolink.dao;

import com.librolink.model.EstadoUsuario;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;
import com.librolink.service.ServicioException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/** Acceso a la tabla usuarios. Recibe la Connection para que el servicio controle la transacción. */
public final class UsuarioDao {

    public record Credencial(Usuario usuario, String passwordHash) { }

    public Optional<Credencial> buscarPorEmail(Connection cn, String email) throws SQLException {
        String sql = """
                SELECT u.id_usuario, r.nombre AS rol, u.numero_socio, u.nombre, u.apellido,
                       u.email, u.telefono, u.estado, u.password_hash
                FROM usuarios u
                JOIN roles r ON r.id_rol = u.id_rol
                WHERE u.email = ?
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Usuario u = new Usuario(
                        rs.getLong("id_usuario"),
                        Rol.valueOf(rs.getString("rol")),
                        rs.getString("numero_socio"),
                        rs.getString("nombre"),
                        rs.getString("apellido"),
                        rs.getString("email"),
                        rs.getString("telefono"),
                        EstadoUsuario.valueOf(rs.getString("estado")));
                return Optional.of(new Credencial(u, rs.getString("password_hash")));
            }
        }
    }

    /** Cuenta administradores de cualquier estado: un admin bloqueado no habilita crear otro "primero". */
    public boolean existeAdministrador(Connection cn) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM usuarios u
                JOIN roles r ON r.id_rol = u.id_rol
                WHERE r.nombre = ?
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, Rol.ADMINISTRADOR.name());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        }
    }

    /** Bloquea la fila del rol hasta el fin de la transacción; serializa la creación del primer admin. */
    public long bloquearRol(Connection cn, Rol rol) throws SQLException {
        String sql = "SELECT id_rol FROM roles WHERE nombre = ? FOR UPDATE";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, rol.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ServicioException.Datos(
                            "El rol " + rol + " no existe. Ejecuta schema_librolink.sql.");
                }
                return rs.getLong(1);
            }
        }
    }

    public long insertar(Connection cn, long idRol, String nombre, String apellido,
                         String email, String passwordHash) throws SQLException {
        String sql = """
                INSERT INTO usuarios (id_rol, nombre, apellido, email, password_hash)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, idRol);
            ps.setString(2, nombre);
            ps.setString(3, apellido);
            ps.setString(4, email);
            ps.setString(5, passwordHash);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }
}