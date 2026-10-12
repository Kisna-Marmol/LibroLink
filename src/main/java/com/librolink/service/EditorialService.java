package com.librolink.service;

import com.librolink.config.Database;
import com.librolink.dao.AuditoriaDao;
import com.librolink.dao.EditorialDao;
import com.librolink.model.Editorial;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

public final class EditorialService {

    private static final String DUPLICADA =
            "Ya existe una editorial con ese nombre (no se distinguen mayúsculas ni tildes).";

    private final Database db;
    private final Sesion sesion;
    private final EditorialDao dao = new EditorialDao();
    private final AuditoriaDao auditoria = new AuditoriaDao();

    public EditorialService(Database db, Sesion sesion) {
        this.db = db;
        this.sesion = sesion;
    }

    public List<Editorial> listar(String texto, boolean incluirInactivas) {
        sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        try (Connection cn = db.getConnection()) {
            return dao.listar(cn, texto, incluirInactivas);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudieron cargar las editoriales.", e);
        }
    }

    public long crear(String nombre, String pais, String sitioWeb) {
        Usuario u = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        String n = Validaciones.textoObligatorio(nombre, "El nombre", 150);
        String p = Validaciones.textoOpcional(pais, "El país", 80);
        String w = Validaciones.urlOpcional(sitioWeb, "El sitio web");
        try {
            return db.enTransaccion(cn -> {
                long id = dao.insertar(cn, n, p, w);
                auditoria.registrar(cn, u.id(), "CREAR", "editoriales", String.valueOf(id), null);
                return id;
            });
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ServicioException.Validacion(DUPLICADA);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo crear la editorial.", e);
        }
    }

    public void actualizar(long id, String nombre, String pais, String sitioWeb) {
        Usuario u = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        String n = Validaciones.textoObligatorio(nombre, "El nombre", 150);
        String p = Validaciones.textoOpcional(pais, "El país", 80);
        String w = Validaciones.urlOpcional(sitioWeb, "El sitio web");
        try {
            db.enTransaccion(cn -> {
                if (dao.actualizar(cn, id, n, p, w) == 0) {
                    throw new ServicioException.Validacion("La editorial ya no existe.");
                }
                auditoria.registrar(cn, u.id(), "ACTUALIZAR", "editoriales", String.valueOf(id), null);
                return null;
            });
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ServicioException.Validacion(DUPLICADA);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo actualizar la editorial.", e);
        }
    }

    public void cambiarEstado(long id, boolean activa) {
        Usuario u = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        try {
            db.enTransaccion(cn -> {
                if (dao.cambiarEstado(cn, id, activa) == 0) {
                    throw new ServicioException.Validacion("La editorial ya no existe.");
                }
                auditoria.registrar(cn, u.id(), activa ? "ACTIVAR" : "DESACTIVAR",
                        "editoriales", String.valueOf(id), null);
                return null;
            });
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo cambiar el estado de la editorial.", e);
        }
    }
}