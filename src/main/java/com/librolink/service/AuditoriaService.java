package com.librolink.service;

import com.librolink.config.Database;
import com.librolink.dao.AuditoriaDao;
import com.librolink.model.FiltroAuditoria;
import com.librolink.model.PaginaAuditoria;
import com.librolink.model.RegistroAuditoria;
import com.librolink.model.Rol;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Consulta de la auditoría (solo lectura). Solo ADMINISTRADOR. */
public final class AuditoriaService {

    public static final int TAMANO_PAGINA = 50;
    private static final int MAX_PAGINA = 20_000;
    private static final int MAX_TEXTO_BUSQUEDA = 100;

    private final Database db;
    private final Sesion sesion;
    private final AuditoriaDao dao = new AuditoriaDao();

    public AuditoriaService(Database db, Sesion sesion) {
        this.db = db;
        this.sesion = sesion;
    }

    /** Página numerada desde 1. Se piden TAMANO_PAGINA + 1 filas para saber si hay otra página sin contar todo. */
    public PaginaAuditoria pagina(FiltroAuditoria filtro, int pagina) {
        sesion.requerirRol(Rol.ADMINISTRADOR);
        if (pagina < 1 || pagina > MAX_PAGINA) {
            throw new ServicioException.Validacion("La página no es válida.");
        }
        if (filtro.desde() != null && filtro.hasta() != null && filtro.hasta().isBefore(filtro.desde())) {
            throw new ServicioException.Validacion("La fecha «hasta» no puede ser anterior a «desde».");
        }
        FiltroAuditoria efectivo = new FiltroAuditoria(filtro.desde(), filtro.hasta(),
                recortar(filtro.usuario()), vacioANull(filtro.accion()), vacioANull(filtro.entidad()));
        try (Connection cn = db.getConnection()) {
            List<RegistroAuditoria> filas = dao.listar(cn, efectivo, TAMANO_PAGINA + 1, (pagina - 1) * TAMANO_PAGINA);
            boolean hayMas = filas.size() > TAMANO_PAGINA;
            return new PaginaAuditoria(List.copyOf(hayMas ? filas.subList(0, TAMANO_PAGINA) : filas), pagina, hayMas);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo consultar la auditoría.", e);
        }
    }

    public List<String> acciones() {
        sesion.requerirRol(Rol.ADMINISTRADOR);
        try (Connection cn = db.getConnection()) {
            return dao.acciones(cn);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudieron cargar las acciones.", e);
        }
    }

    public List<String> entidades() {
        sesion.requerirRol(Rol.ADMINISTRADOR);
        try (Connection cn = db.getConnection()) {
            return dao.entidades(cn);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudieron cargar las entidades.", e);
        }
    }

    private static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static String recortar(String texto) {
        String t = vacioANull(texto);
        return t == null ? null : (t.length() > MAX_TEXTO_BUSQUEDA ? t.substring(0, MAX_TEXTO_BUSQUEDA) : t);
    }
}