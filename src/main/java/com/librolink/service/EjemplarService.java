package com.librolink.service;

import com.librolink.config.Database;
import com.librolink.dao.AuditoriaDao;
import com.librolink.dao.EjemplarDao;
import com.librolink.dao.EjemplarDao.Actual;
import com.librolink.model.DatosEjemplar;
import com.librolink.model.Ejemplar;
import com.librolink.model.EstadoEjemplar;
import com.librolink.model.FiltroEjemplares;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public final class EjemplarService {

    private static final int LIMITE_RESULTADOS = 500;
    private static final int MAX_TEXTO_BUSQUEDA = 100;
    private static final String PREFIJO_CODIGO = "LL-";

    private final Database db;
    private final Sesion sesion;
    private final EjemplarDao dao = new EjemplarDao();
    private final AuditoriaDao auditoria = new AuditoriaDao();

    public EjemplarService(Database db, Sesion sesion) {
        this.db = db;
        this.sesion = sesion;
    }

    public int limiteResultados() {
        return LIMITE_RESULTADOS;
    }

    public List<Ejemplar> listar(FiltroEjemplares filtro) {
        sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        FiltroEjemplares efectivo = new FiltroEjemplares(recortar(filtro.texto()), filtro.estado());
        try (Connection cn = db.getConnection()) {
            return dao.listar(cn, efectivo, LIMITE_RESULTADOS);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo consultar el inventario.", e);
        }
    }

    /** Solo una sugerencia: la unicidad real la garantiza la restricción UNIQUE al guardar. */
    public String siguienteCodigo() {
        sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        try (Connection cn = db.getConnection()) {
            long siguiente = dao.maximoNumeroCodigo(cn, PREFIJO_CODIGO) + 1;
            return PREFIJO_CODIGO + String.format("%06d", siguiente);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo calcular el siguiente código.", e);
        }
    }

    public long crear(DatosEjemplar datos) {
        Usuario u = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        EjemplarDao.Datos d = normalizar(datos);
        exigirEstadoAsignable(d.estado());
        try {
            return db.enTransaccion(cn -> {
                exigirLibroActivo(cn, d.idLibro());
                long id = dao.insertar(cn, d);
                auditoria.registrar(cn, u.id(), "CREAR", "ejemplares", String.valueOf(id), null);
                return id;
            });
        } catch (SQLException e) {
            throw traducir(e, "No se pudo crear el ejemplar.");
        }
    }

    public void actualizar(long id, DatosEjemplar datos) {
        Usuario u = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        EjemplarDao.Datos d = normalizar(datos);
        try {
            db.enTransaccion(cn -> {
                Actual actual = dao.bloquear(cn, id)
                        .orElseThrow(() -> new ServicioException.Validacion("El ejemplar ya no existe."));
                boolean prestado = actual.estado() == EstadoEjemplar.PRESTADO
                        || dao.contarDetallesPendientes(cn, id) > 0;

                if (prestado) {
                    if (d.idLibro() != actual.idLibro()) {
                        throw new ServicioException.Validacion(
                                "No se puede cambiar el libro de un ejemplar prestado.");
                    }
                    if (d.estado() != EstadoEjemplar.PRESTADO) {
                        throw new ServicioException.Validacion(
                                "El ejemplar está prestado: su estado cambia al registrar la devolución.");
                    }
                } else {
                    exigirEstadoAsignable(d.estado());
                    if (d.idLibro() != actual.idLibro()) {
                        exigirLibroActivo(cn, d.idLibro());
                    }
                }

                dao.actualizar(cn, id, d);
                String detalle = actual.estado() == d.estado() ? null
                        : "{\"estado_anterior\":\"" + actual.estado().name()
                        + "\",\"estado_nuevo\":\"" + d.estado().name() + "\"}";
                auditoria.registrar(cn, u.id(), "ACTUALIZAR", "ejemplares", String.valueOf(id), detalle);
                return null;
            });
        } catch (SQLException e) {
            throw traducir(e, "No se pudo actualizar el ejemplar.");
        }
    }

    // ---------- validación ----------

    private EjemplarDao.Datos normalizar(DatosEjemplar d) {
        if (d.idLibro() == null) {
            throw new ServicioException.Validacion("Selecciona el libro del ejemplar.");
        }
        String codigo = Validaciones.codigoInventario(d.codigo());
        String ubicacion = Validaciones.textoOpcional(d.ubicacion(), "La ubicación", 100);
        LocalDate fecha = d.fechaAdquisicion();
        if (fecha != null && fecha.isAfter(LocalDate.now())) {
            throw new ServicioException.Validacion("La fecha de adquisición no puede ser futura.");
        }
        if (d.estado() == null) {
            throw new ServicioException.Validacion("Selecciona el estado del ejemplar.");
        }
        String observaciones = Validaciones.textoOpcional(d.observaciones(), "Las observaciones", 500);
        return new EjemplarDao.Datos(d.idLibro(), codigo, ubicacion, fecha, d.estado(), observaciones);
    }

    private static void exigirEstadoAsignable(EstadoEjemplar estado) {
        if (estado == EstadoEjemplar.PRESTADO) {
            throw new ServicioException.Validacion(
                    "El estado Prestado lo asigna un préstamo. Elige otro estado.");
        }
    }

    private void exigirLibroActivo(Connection cn, long idLibro) throws SQLException {
        if (!dao.libroActivo(cn, idLibro).orElse(false)) {
            throw new ServicioException.Validacion("El libro seleccionado no existe o está inactivo.");
        }
    }

    private static ServicioException traducir(SQLException e, String mensajeTecnico) {
        if (e.getErrorCode() == 1062 && String.valueOf(e.getMessage()).contains("uq_ejemplares_codigo")) {
            return new ServicioException.Validacion("Ya existe un ejemplar con ese código de inventario.");
        }
        return new ServicioException.Datos(mensajeTecnico, e);
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        return t.length() > MAX_TEXTO_BUSQUEDA ? t.substring(0, MAX_TEXTO_BUSQUEDA) : t;
    }
}