package com.librolink.service;

import com.librolink.config.Database;
import com.librolink.dao.AuditoriaDao;
import com.librolink.dao.UsuarioDao;
import com.librolink.dao.UsuarioDao.Credencial;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Arrays;
import java.util.Optional;

public final class AuthService {

    private static final String ERROR_CREDENCIALES = "Correo o contraseña incorrectos.";

    private final Database db;
    private final UsuarioDao usuarios = new UsuarioDao();
    private final AuditoriaDao auditoria = new AuditoriaDao();
    private final PasswordHasher hasher = new PasswordHasher();
    /** Se verifica contra este hash cuando el correo no existe, para igualar el tiempo de respuesta. */
    private final String hashFicticio;

    public AuthService(Database db) {
        this.db = db;
        this.hashFicticio = hasher.hash("clave-ficticia-123".toCharArray());
    }

    public boolean existeAdministrador() {
        try (Connection cn = db.getConnection()) {
            return usuarios.existeAdministrador(cn);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo consultar la base de datos.", e);
        }
    }

    public Usuario crearPrimerAdministrador(String nombre, String apellido, String email,
                                            char[] password, char[] confirmacion) {
        String n = Validaciones.textoObligatorio(nombre, "El nombre", 80);
        String a = Validaciones.textoObligatorio(apellido, "El apellido", 80);
        String correo = Validaciones.email(email);
        Validaciones.password(password);
        if (!Arrays.equals(password, confirmacion)) {
            throw new ServicioException.Validacion("Las contraseñas no coinciden.");
        }
        String hash = hasher.hash(password); // fuera de la transacción: tarda ~0,3 s

        try (Connection cn = db.getConnection()) {
            cn.setAutoCommit(false);
            try {
                long idRol = usuarios.bloquearRol(cn, Rol.ADMINISTRADOR);
                if (usuarios.existeAdministrador(cn)) {
                    throw new ServicioException.Validacion("Ya existe un administrador. Inicia sesión.");
                }
                long id = usuarios.insertar(cn, idRol, n, a, correo, hash);
                auditoria.registrar(cn, id, "CREAR_PRIMER_ADMIN", "usuarios", String.valueOf(id), null);
                cn.commit();
                return new Usuario(id, Rol.ADMINISTRADOR, null, n, a, correo, null, EstadoUsuario.ACTIVO);
            } catch (SQLIntegrityConstraintViolationException e) {
                cn.rollback();
                throw new ServicioException.Validacion("Ese correo ya está registrado.");
            } catch (SQLException | RuntimeException e) {
                cn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo crear el administrador.", e);
        }
    }

    public Usuario iniciarSesion(String email, char[] password) {
        String correo;
        try {
            correo = Validaciones.email(email);
        } catch (ServicioException.Validacion e) {
            hasher.verificar(password, hashFicticio);
            throw new ServicioException.Autenticacion(ERROR_CREDENCIALES);
        }

        try (Connection cn = db.getConnection()) {
            Optional<Credencial> cred = usuarios.buscarPorEmail(cn, correo);
            boolean claveOk = hasher.verificar(password,
                    cred.map(Credencial::passwordHash).orElse(hashFicticio));
            Long idUsuario = cred.map(c -> c.usuario().id()).orElse(null);
            String idRegistro = idUsuario == null ? null : idUsuario.toString();

            if (cred.isEmpty() || !claveOk || cred.get().usuario().estado() != EstadoUsuario.ACTIVO) {
                auditoria.registrar(cn, idUsuario, "LOGIN_FALLIDO", "usuarios", idRegistro, null);
                throw new ServicioException.Autenticacion(ERROR_CREDENCIALES);
            }
            auditoria.registrar(cn, idUsuario, "LOGIN", "usuarios", idRegistro, null);
            return cred.get().usuario();
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo iniciar sesión por un error de base de datos.", e);
        }
    }
}