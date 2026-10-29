package com.librolink.service;

import com.librolink.config.Database;
import com.librolink.dao.AuditoriaDao;
import com.librolink.dao.ReporteDao;
import com.librolink.dao.UsuarioDao;
import com.librolink.model.EstadoEjemplar;
import com.librolink.model.ResultadoReporte;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Reportes y exportación a CSV. Solo ADMINISTRADOR y BIBLIOTECARIO. */
public final class ReporteService {

    private static final int LIMITE_FILAS = 5000;
    private static final long MAX_DIAS_RANGO = 3660; // unos 10 años
    private static final int MAX_TOP = 100;

    private final Database db;
    private final Sesion sesion;
    private final ReporteDao dao = new ReporteDao();
    private final UsuarioDao usuarioDao = new UsuarioDao();
    private final AuditoriaDao auditoria = new AuditoriaDao();

    public ReporteService(Database db, Sesion sesion) {
        this.db = db;
        this.sesion = sesion;
    }

    public int limiteFilas() {
        return LIMITE_FILAS;
    }

    public ResultadoReporte atrasos() {
        exigirPersonal();
        return leer("No se pudo generar el reporte de atrasos.", cn -> dao.atrasos(cn, LIMITE_FILAS));
    }

    public ResultadoReporte historialDeLector(Long idLector) {
        exigirPersonal();
        if (idLector == null) {
            throw new ServicioException.Validacion("Selecciona un lector.");
        }
        return leer("No se pudo generar el historial del lector.", cn -> {
            Usuario lector = usuarioDao.buscarPorId(cn, idLector, false)
                    .orElseThrow(() -> new ServicioException.Validacion("El lector no existe."))
                    .usuario();
            if (lector.rol() != Rol.LECTOR) {
                throw new ServicioException.Validacion("El usuario seleccionado no es un lector.");
            }
            return dao.historialLector(cn, lector.id(), lector.nombreCompleto(), LIMITE_FILAS);
        });
    }

    /** estado null = todos los estados. */
    public ResultadoReporte inventarioPorEjemplar(EstadoEjemplar estado) {
        exigirPersonal();
        return leer("No se pudo generar el inventario.", cn -> dao.inventarioPorEjemplar(cn, estado, LIMITE_FILAS));
    }

    public ResultadoReporte inventarioPorTitulo() {
        exigirPersonal();
        return leer("No se pudo generar el inventario.", cn -> dao.inventarioPorTitulo(cn, LIMITE_FILAS));
    }

    public ResultadoReporte prestamosPorRango(LocalDate desde, LocalDate hasta) {
        exigirPersonal();
        if (desde == null || hasta == null) {
            throw new ServicioException.Validacion("Indica las fechas «desde» y «hasta».");
        }
        exigirRangoValido(desde, hasta);
        return leer("No se pudo generar el reporte de préstamos.",
                cn -> dao.prestamosPorRango(cn, desde, hasta, LIMITE_FILAS));
    }

    /** desde y hasta son opcionales; top entre 1 y 100. */
    public ResultadoReporte librosMasSolicitados(LocalDate desde, LocalDate hasta, int top) {
        exigirPersonal();
        if (top < 1 || top > MAX_TOP) {
            throw new ServicioException.Validacion("El número de libros debe estar entre 1 y " + MAX_TOP + ".");
        }
        if (desde != null && hasta != null) {
            exigirRangoValido(desde, hasta);
        }
        return leer("No se pudo generar el reporte de libros más solicitados.",
                cn -> dao.librosMasSolicitados(cn, desde, hasta, top));
    }

    /** Guarda el reporte ya generado y deja constancia en auditoría. */
    public void exportarCsv(ResultadoReporte reporte, Path destino) {
        Usuario actor = exigirPersonal();
        if (reporte == null || reporte.filas().isEmpty()) {
            throw new ServicioException.Validacion("No hay datos para exportar.");
        }
        try {
            CsvWriter.escribir(destino, reporte);
        } catch (IOException e) {
            throw new ServicioException.Datos("No se pudo guardar el archivo: " + e.getMessage(), e);
        }
        try {
            db.enTransaccion(cn -> {
                auditoria.registrar(cn, actor.id(), "EXPORTAR", "reportes", reporte.tipo().codigo(),
                        "{\"filas\":" + reporte.filas().size() + "}");
                return null;
            });
        } catch (SQLException e) {
            throw new ServicioException.Datos(
                    "El archivo se guardó, pero no se pudo registrar la exportación en auditoría.", e);
        }
    }

    // ---------- auxiliares ----------

    private Usuario exigirPersonal() {
        return sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
    }

    private static void exigirRangoValido(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new ServicioException.Validacion("La fecha «hasta» no puede ser anterior a «desde».");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > MAX_DIAS_RANGO) {
            throw new ServicioException.Validacion("El rango no puede superar los 10 años.");
        }
    }

    private <T> T leer(String errorTecnico, Database.Trabajo<T> trabajo) {
        try (Connection cn = db.getConnection()) {
            return trabajo.ejecutar(cn);
        } catch (SQLException e) {
            throw new ServicioException.Datos(errorTecnico, e);
        }
    }
}