package com.librolink.service;

import com.librolink.config.Database;
import com.librolink.dao.AuditoriaDao;
import com.librolink.dao.AutorDao;
import com.librolink.model.Autor;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class AutorService {

    private final Database db;
    private final Sesion sesion;
    private final AutorDao dao = new AutorDao();
    private final AuditoriaDao auditoria = new AuditoriaDao();

    public AutorService(Database db, Sesion sesion) {
        this.db = db;
        this.sesion = sesion;
    }

    public List<Autor> listar(String texto, boolean incluirInactivos) {
        sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        try (Connection cn = db.getConnection()) {
            return dao.listar(cn, texto, incluirInactivos);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudieron cargar los autores.", e);
        }
    }

    public long crear(String nombre, String apellido, String nacionalidad, String biografia) {
        Usuario u = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        String n = Validaciones.textoObligatorio(nombre, "El nombre", 100);
        String a = Validaciones.textoOpcional(apellido, "El apellido", 100);
        String na = Validaciones.textoOpcional(nacionalidad, "La nacionalidad", 80);
        String b = Validaciones.textoOpcional(biografia, "La biografía", 5000);
        try {
            return db.enTransaccion(cn -> {
                long id = dao.insertar(cn, n, a, na, b);
                auditoria.registrar(cn, u.id(), "CREAR", "autores", String.valueOf(id), null);
                return id;
            });
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo crear el autor.", e);
        }
    }

    public void actualizar(long id, String nombre, String apellido, String nacionalidad, String biografia) {
        Usuario u = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        String n = Validaciones.textoObligatorio(nombre, "El nombre", 100);
        String a = Validaciones.textoOpcional(apellido, "El apellido", 100);
        String na = Validaciones.textoOpcional(nacionalidad, "La nacionalidad", 80);
        String b = Validaciones.textoOpcional(biografia, "La biografía", 5000);
        try {
            db.enTransaccion(cn -> {
                if (dao.actualizar(cn, id, n, a, na, b) == 0) {
                    throw new ServicioException.Validacion("El autor ya no existe.");
                }
                auditoria.registrar(cn, u.id(), "ACTUALIZAR", "autores", String.valueOf(id), null);
                return null;
            });
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo actualizar el autor.", e);
        }
    }

    public void cambiarEstado(long id, boolean activo) {
        Usuario u = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        try {
            db.enTransaccion(cn -> {
                if (dao.cambiarEstado(cn, id, activo) == 0) {
                    throw new ServicioException.Validacion("El autor ya no existe.");
                }
                auditoria.registrar(cn, u.id(), activo ? "ACTIVAR" : "DESACTIVAR",
                        "autores", String.valueOf(id), null);
                return null;
            });
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo cambiar el estado del autor.", e);
        }
    }
}