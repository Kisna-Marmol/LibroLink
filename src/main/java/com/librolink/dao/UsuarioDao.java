package com.librolink.dao;

import com.librolink.model.DatosUsuario;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.FiltroUsuarios;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;
import com.librolink.service.ServicioException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Acceso a la tabla usuarios. Recibe la Connection para que el servicio controle la transacción. */
public final class UsuarioDao {

    public record Credencial(Usuario usuario, String passwordHash) { }

    private static final String SELECT_CON_HASH = """
            SELECT u.id_usuario, r.nombre AS rol, u.numero_socio, u.nombre, u.apellido,
                   u.email, u.telefono, u.estado, u.password_hash
            FROM usuarios u
            JOIN roles r ON r.id_rol = u.id_rol
            """;

    private static Usuario mapear(ResultSet rs) throws SQLException {
        return new Usuario(
                rs.getLong("id_usuario"),
                Rol.valueOf(rs.getString("rol")),
                rs.getString("numero_socio"),
                rs.getString("nombre"),
                rs.getString("apellido"),
                rs.getString("email"),
                rs.getString("telefono"),
                EstadoUsuario.valueOf(rs.getString("estado")));
    }

    // ---------- autenticación (usado por AuthService) ----------

    public Optional<Credencial> buscarPorEmail(Connection cn, String email) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(SELECT_CON_HASH + " WHERE u.email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new Credencial(mapear(rs), rs.getString("password_hash")))
                        : Optional.empty();
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

    /** Bloquea la fila del rol hasta el fin de la transacción; serializa los cambios sobre administradores. */
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

    // ---------- gestión de usuarios ----------

    public List<Usuario> listar(Connection cn, FiltroUsuarios f, int limite) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT u.id_usuario, r.nombre AS rol, u.numero_socio, u.nombre, u.apellido,
                       u.email, u.telefono, u.estado
                FROM usuarios u
                JOIN roles r ON r.id_rol = u.id_rol
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();

        String patron = Sql.patronContiene(f.texto());
        if (patron != null) {
            sql.append("""
                    AND (CONCAT_WS(' ', u.nombre, u.apellido) LIKE ? OR u.email LIKE ?
                         OR u.numero_socio LIKE ? OR u.telefono LIKE ?)
                    """);
            params.addAll(List.of(patron, patron, patron, patron));
        }
        if (f.rol() != null) {
            sql.append(" AND r.nombre = ?");
            params.add(f.rol().name());
        }
        if (f.estado() != null) {
            sql.append(" AND u.estado = ?");
            params.add(f.estado().name());
        }
        sql.append(" ORDER BY u.apellido, u.nombre, u.id_usuario LIMIT ?");
        params.add(limite);

        List<Usuario> lista = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    /** Con bloquear = true toma un bloqueo de escritura sobre la fila del usuario hasta el fin de la transacción. */
    public Optional<Credencial> buscarPorId(Connection cn, long id, boolean bloquear) throws SQLException {
        if (bloquear) {
            // Solo se bloquea la fila de usuarios: FOR UPDATE sobre el JOIN bloquearía también la de roles.
            try (PreparedStatement ps = cn.prepareStatement(
                    "SELECT id_usuario FROM usuarios WHERE id_usuario = ? FOR UPDATE")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                }
            }
        }
        try (PreparedStatement ps = cn.prepareStatement(SELECT_CON_HASH + " WHERE u.id_usuario = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new Credencial(mapear(rs), rs.getString("password_hash")))
                        : Optional.empty();
            }
        }
    }

    public long idRol(Connection cn, Rol rol) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("SELECT id_rol FROM roles WHERE nombre = ?")) {
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

    public long insertarCompleto(Connection cn, long idRol, DatosUsuario d, String passwordHash) throws SQLException {
        String sql = """
                INSERT INTO usuarios (id_rol, numero_socio, nombre, apellido, email, telefono,
                                      password_hash, estado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, idRol);
            ps.setString(2, d.numeroSocio());
            ps.setString(3, d.nombre());
            ps.setString(4, d.apellido());
            ps.setString(5, d.email());
            ps.setString(6, d.telefono());
            ps.setString(7, passwordHash);
            ps.setString(8, d.estado().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** Devuelve las filas afectadas: 0 significa que el id no existe. No toca la contraseña. */
    public int actualizar(Connection cn, long id, long idRol, DatosUsuario d) throws SQLException {
        String sql = """
                UPDATE usuarios
                SET id_rol = ?, numero_socio = ?, nombre = ?, apellido = ?, email = ?,
                    telefono = ?, estado = ?
                WHERE id_usuario = ?
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, idRol);
            ps.setString(2, d.numeroSocio());
            ps.setString(3, d.nombre());
            ps.setString(4, d.apellido());
            ps.setString(5, d.email());
            ps.setString(6, d.telefono());
            ps.setString(7, d.estado().name());
            ps.setLong(8, id);
            return ps.executeUpdate();
        }
    }

    public int actualizarPasswordHash(Connection cn, long id, String passwordHash) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(
                "UPDATE usuarios SET password_hash = ? WHERE id_usuario = ?")) {
            ps.setString(1, passwordHash);
            ps.setLong(2, id);
            return ps.executeUpdate();
        }
    }

    /** Administradores ACTIVOS distintos de idExcluido (usar 0 para contarlos a todos). */
    public long contarAdministradoresActivosExcepto(Connection cn, long idExcluido) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM usuarios u
                JOIN roles r ON r.id_rol = u.id_rol
                WHERE r.nombre = 'ADMINISTRADOR' AND u.estado = 'ACTIVO' AND u.id_usuario <> ?
                """;
        return contar(cn, sql, idExcluido);
    }

    /** Préstamos en los que el usuario figura como lector o como responsable. */
    public long contarPrestamos(Connection cn, long idUsuario) throws SQLException {
        return contar(cn,
                "SELECT COUNT(*) FROM prestamos WHERE id_lector = ? OR id_usuario_responsable = ?",
                idUsuario, idUsuario);
    }

    /** Ejemplares que el lector todavía no ha devuelto. */
    public long contarPendientes(Connection cn, long idLector) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM detalle_prestamo dp
                JOIN prestamos p ON p.id_prestamo = dp.id_prestamo
                WHERE p.id_lector = ? AND dp.estado = 'PRESTADO'
                """;
        return contar(cn, sql, idLector);
    }

    /** Mayor número usado entre los números de socio con formato PREFIJO + dígitos (0 si no hay ninguno). */
    public long maximoNumeroSocio(Connection cn, String prefijo) throws SQLException {
        String sql = """
                SELECT COALESCE(MAX(CAST(SUBSTRING(numero_socio, ?) AS UNSIGNED)), 0)
                FROM usuarios
                WHERE numero_socio REGEXP ?
                """;
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, prefijo.length() + 1);
            ps.setString(2, "^" + prefijo + "[0-9]{1,9}$");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long contar(Connection cn, String sql, long... parametros) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                ps.setLong(i + 1, parametros[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}