package com.librolink.service;

import com.librolink.config.Database;
import com.librolink.dao.HistorialDao;
import com.librolink.model.MovimientoHistorial;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class HistorialService {

    private static final int LIMITE_RESULTADOS = 200;

    private final Database db;
    private final Sesion sesion;
    private final HistorialDao dao = new HistorialDao();

    public HistorialService(Database db, Sesion sesion) {
        this.db = db;
        this.sesion = sesion;
    }

    /** El personal consulta cualquier lector; un LECTOR solo el suyo. */
    public List<MovimientoHistorial> deLector(long idLector) {
        Usuario u = sesion.requerirUsuario();
        boolean personal = u.rol() == Rol.ADMINISTRADOR || u.rol() == Rol.BIBLIOTECARIO;
        boolean propio = u.rol() == Rol.LECTOR && u.id() == idLector;
        if (!personal && !propio) {
            throw new ServicioException.Autorizacion("No tienes permiso para ver este historial.");
        }
        try (Connection cn = db.getConnection()) {
            return dao.porLector(cn, idLector, LIMITE_RESULTADOS);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo cargar el historial.", e);
        }
    }

    public List<MovimientoHistorial> miHistorial() {
        Usuario u = sesion.requerirRol(Rol.LECTOR);
        return deLector(u.id());
    }
}