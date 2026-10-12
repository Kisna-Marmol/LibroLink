package com.librolink.service;

import com.librolink.config.Database;
import com.librolink.dao.AuditoriaDao;
import com.librolink.dao.CategoriaDao;
import com.librolink.model.Categoria;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

public final class CategoriaService {

    private static final String DUPLICADA =
            "Ya existe una categoría con ese nombre (no se distinguen mayúsculas ni tildes).";

    private final Database db;
    private final Sesion sesion;
    private final CategoriaDao dao = new CategoriaDao();
    private final AuditoriaDao auditoria = new AuditoriaDao();

    public CategoriaService(Database db, Sesion sesion) {
        this.db = db;
        this.sesion = sesion;
    }

    public List<Categoria> listar(String texto, boolean incluirInactivas) {
        sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        try (Connection cn = db.getConnection()) {
            return dao.listar(cn, texto, incluirInactivas);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudieron cargar las categorías.", e);
        }
    }

    public long crear(String nombre, String descripcion) {
        Usuario u = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        String n = Validaciones.textoObligatorio(nombre, "El nombre", 100);
        String d = Validaciones.textoOpcional(descripcion, "La descripción", 255);
        try {
            return db.enTransaccion(cn -> {
                long id = dao.insertar(cn, n, d);
                auditoria.registrar(cn, u.id(), "CREAR", "categorias", String.valueOf(id), null);
                return id;
            });
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ServicioException.Validacion(DUPLICADA);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo crear la categoría.", e);
        }
    }

    public void actualizar(long id, String nombre, String descripcion) {
        Usuario u = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        String n = Validaciones.textoObligatorio(nombre, "El nombre", 100);
        String d = Validaciones.textoOpcional(descripcion, "La descripción", 255);
        try {
            db.enTransaccion(cn -> {
                if (dao.actualizar(cn, id, n, d) == 0) {
                    throw new ServicioException.Validacion("La categoría ya no existe.");
                }
                auditoria.registrar(cn, u.id(), "ACTUALIZAR", "categorias", String.valueOf(id), null);
                return null;
            });
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ServicioException.Validacion(DUPLICADA);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo actualizar la categoría.", e);
        }
    }

    public void cambiarEstado(long id, boolean activa) {
        Usuario u = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        try {
            db.enTransaccion(cn -> {
                if (dao.cambiarEstado(cn, id, activa) == 0) {
                    throw new ServicioException.Validacion("La categoría ya no existe.");
                }
                auditoria.registrar(cn, u.id(), activa ? "ACTIVAR" : "DESACTIVAR",
                        "categorias", String.valueOf(id), null);
                return null;
            });
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo cambiar el estado de la categoría.", e);
        }
    }
}