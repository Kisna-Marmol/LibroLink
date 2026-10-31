package com.librolink.service;

import com.librolink.config.Database;
import com.librolink.dao.AuditoriaDao;
import com.librolink.dao.ConfiguracionDao;
import com.librolink.model.DatosPolitica;
import com.librolink.model.PoliticaPrestamo;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/** Parámetros de circulación. Solo ADMINISTRADOR. */
public final class ConfiguracionService {

    private static final Map<String, String> DESCRIPCIONES = Map.of(
            PoliticaPrestamo.CLAVE_DIAS_PRESTAMO, "Duración inicial del préstamo en días.",
            PoliticaPrestamo.CLAVE_MAX_EJEMPLARES, "Máximo de ejemplares por operación.",
            PoliticaPrestamo.CLAVE_MAX_PRESTAMOS_ACTIVOS, "Máximo de préstamos activos por lector.",
            PoliticaPrestamo.CLAVE_PERMITIR_CON_ATRASOS, "Permite prestar si el lector tiene atrasos.",
            PoliticaPrestamo.CLAVE_DIAS_AVISO, "Días de anticipación para avisar vencimientos.");

    private final Database db;
    private final Sesion sesion;
    private final ConfiguracionDao dao = new ConfiguracionDao();
    private final AuditoriaDao auditoria = new AuditoriaDao();

    public ConfiguracionService(Database db, Sesion sesion) {
        this.db = db;
        this.sesion = sesion;
    }

    /** La política en vigor (un valor ausente o inválido en la tabla aparece con su valor inicial). */
    public PoliticaPrestamo obtener() {
        sesion.requerirRol(Rol.ADMINISTRADOR);
        try (Connection cn = db.getConnection()) {
            return PoliticaPrestamo.desde(dao.todas(cn));
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo leer la configuración.", e);
        }
    }

    /** Devuelve cuántos parámetros cambiaron. Cada cambio queda en auditoría con el valor anterior y el nuevo. */
    public int guardar(DatosPolitica datos) {
        Usuario actor = sesion.requerirRol(Rol.ADMINISTRADOR);
        PoliticaPrestamo nueva = new PoliticaPrestamo(
                Validaciones.enteroEnRango(datos.diasPrestamo(), "Los días de préstamo",
                        PoliticaPrestamo.DIAS_PRESTAMO_MIN, PoliticaPrestamo.DIAS_PRESTAMO_MAX),
                Validaciones.enteroEnRango(datos.maxEjemplaresPorPrestamo(), "El máximo de ejemplares por préstamo",
                        PoliticaPrestamo.MAX_EJEMPLARES_MIN, PoliticaPrestamo.MAX_EJEMPLARES_MAX),
                Validaciones.enteroEnRango(datos.maxPrestamosActivos(), "El máximo de préstamos activos",
                        PoliticaPrestamo.MAX_PRESTAMOS_ACTIVOS_MIN, PoliticaPrestamo.MAX_PRESTAMOS_ACTIVOS_MAX),
                datos.permitirPrestamoConAtrasos(),
                Validaciones.enteroEnRango(datos.diasAvisoVencimiento(), "Los días de aviso",
                        PoliticaPrestamo.DIAS_AVISO_MIN, PoliticaPrestamo.DIAS_AVISO_MAX));
        try {
            return db.enTransaccion(cn -> {
                dao.bloquearTodas(cn);
                Map<String, String> actuales = dao.todas(cn);
                int cambios = 0;
                for (Map.Entry<String, String> e : nueva.valoresPorClave().entrySet()) {
                    String clave = e.getKey();
                    String valor = e.getValue();
                    String anterior = actuales.get(clave);
                    if (valor.equals(anterior == null ? null : anterior.trim())) {
                        continue;
                    }
                    dao.guardar(cn, clave, valor, DESCRIPCIONES.get(clave));
                    auditoria.registrar(cn, actor.id(), "CAMBIAR_CONFIGURACION", "configuracion", clave,
                            "{\"anterior\":" + Json.texto(anterior) + ",\"nuevo\":" + Json.texto(valor) + "}");
                    cambios++;
                }
                return cambios;
            });
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo guardar la configuración.", e);
        }
    }
}