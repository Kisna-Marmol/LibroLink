package com.librolink.service;

import com.librolink.config.Database;
import com.librolink.dao.DashboardDao;
import com.librolink.model.ResumenPanel;
import com.librolink.model.Rol;

import java.sql.Connection;
import java.sql.SQLException;

public final class DashboardService {

    private final Database db;
    private final Sesion sesion;
    private final DashboardDao dao = new DashboardDao();

    public DashboardService(Database db, Sesion sesion) {
        this.db = db;
        this.sesion = sesion;
    }

    public ResumenPanel obtenerResumen() {
        sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        try (Connection cn = db.getConnection()) {
            return new ResumenPanel(
                    dao.titulosActivos(cn),
                    dao.ejemplaresDisponibles(cn),
                    dao.prestamosActivos(cn),
                    dao.prestamosAtrasados(cn),
                    dao.actividadReciente(cn, 8));
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudieron cargar las estadísticas.", e);
        }
    }
}