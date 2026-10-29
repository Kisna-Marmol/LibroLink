package com.librolink.service;

import com.librolink.config.Database;
import com.librolink.dao.AuditoriaDao;
import com.librolink.dao.ConfiguracionDao;
import com.librolink.dao.EjemplarDao;
import com.librolink.dao.PrestamoDao;
import com.librolink.dao.PrestamoDao.ConsultaEjemplar;
import com.librolink.dao.PrestamoDao.EjemplarBloqueado;
import com.librolink.dao.UsuarioDao;
import com.librolink.dao.UsuarioDao.Credencial;
import com.librolink.model.DatosPrestamo;
import com.librolink.model.DetallePrestamo;
import com.librolink.model.EjemplarPrestable;
import com.librolink.model.EstadoEjemplar;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.FiltroPrestamos;
import com.librolink.model.PoliticaPrestamo;
import com.librolink.model.Prestamo;
import com.librolink.model.Rol;
import com.librolink.model.SituacionLector;
import com.librolink.model.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Préstamos y devoluciones. Solo ADMINISTRADOR y BIBLIOTECARIO. */
public final class PrestamoService {

    private static final int LIMITE_RESULTADOS = 500;
    private static final int MAX_TEXTO_BUSQUEDA = 100;

    private final Database db;
    private final Sesion sesion;
    private final PrestamoDao dao = new PrestamoDao();
    private final EjemplarDao ejemplarDao = new EjemplarDao();
    private final UsuarioDao usuarioDao = new UsuarioDao();
    private final ConfiguracionDao configuracionDao = new ConfiguracionDao();
    private final AuditoriaDao auditoria = new AuditoriaDao();

    public PrestamoService(Database db, Sesion sesion) {
        this.db = db;
        this.sesion = sesion;
    }

    // ---------- consultas ----------

    public List<Prestamo> listar(FiltroPrestamos filtro) {
        sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        FiltroPrestamos efectivo = new FiltroPrestamos(recortar(filtro.texto()),
                filtro.estado() == null ? FiltroPrestamos.Estado.TODOS : filtro.estado());
        try (Connection cn = db.getConnection()) {
            return dao.listar(cn, efectivo, LIMITE_RESULTADOS);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudieron cargar los préstamos.", e);
        }
    }

    public List<DetallePrestamo> detalles(long idPrestamo) {
        sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        try (Connection cn = db.getConnection()) {
            return dao.detalles(cn, idPrestamo);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudieron cargar los ejemplares del préstamo.", e);
        }
    }

    public PoliticaPrestamo politica() {
        sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        try (Connection cn = db.getConnection()) {
            return politica(cn);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo leer la configuración de préstamos.", e);
        }
    }

    public SituacionLector situacionLector(long idLector) {
        sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        try (Connection cn = db.getConnection()) {
            return situacion(cn, idLector, politica(cn));
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo consultar la situación del lector.", e);
        }
    }

    /** Comprobación previa (sin bloqueo) al agregar un ejemplar al formulario; crear() vuelve a validar con bloqueo. */
    public EjemplarPrestable consultarEjemplar(String codigo) {
        sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        String c = Validaciones.codigoInventario(codigo);
        try (Connection cn = db.getConnection()) {
            ConsultaEjemplar q = dao.consultarEjemplar(cn, c)
                    .orElseThrow(() -> new ServicioException.Validacion("No existe un ejemplar con el código " + c + "."));
            String problema = problemaDePrestamo(q.codigo(), q.estado(), q.pendientes(), q.libroActivo());
            if (problema != null) {
                throw new ServicioException.Validacion(problema);
            }
            return new EjemplarPrestable(q.idEjemplar(), q.codigo(), q.titulo());
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo consultar el ejemplar.", e);
        }
    }

    // ---------- operaciones ----------

    public long crear(DatosPrestamo datos) {
        Usuario actor = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        if (datos.idLector() == null) {
            throw new ServicioException.Validacion("Selecciona el lector.");
        }
        if (datos.codigosEjemplares() == null || datos.codigosEjemplares().isEmpty()) {
            throw new ServicioException.Validacion("Agrega al menos un ejemplar.");
        }
        // Ordenados: los ejemplares siempre se bloquean en el mismo orden, así dos préstamos no se traban entre sí.
        Set<String> codigos = new TreeSet<>();
        for (String bruto : datos.codigosEjemplares()) {
            String c = Validaciones.codigoInventario(bruto);
            if (!codigos.add(c)) {
                throw new ServicioException.Validacion("El ejemplar " + c + " está repetido.");
            }
        }
        String observaciones = Validaciones.textoOpcional(datos.observaciones(), "Las observaciones", 500);
        long idLector = datos.idLector();

        try {
            return db.enTransaccion(cn -> {
                PoliticaPrestamo pol = politica(cn);
                if (codigos.size() > pol.maxEjemplaresPorPrestamo()) {
                    throw new ServicioException.Validacion("Un préstamo admite como máximo "
                            + pol.maxEjemplaresPorPrestamo() + " ejemplar(es).");
                }
                exigirResponsableActivo(cn, actor);

                // El lector queda bloqueado hasta el final: dos préstamos simultáneos no superan su límite.
                Usuario lector = usuarioDao.buscarPorId(cn, idLector, true)
                        .orElseThrow(() -> new ServicioException.Validacion("El lector no existe."))
                        .usuario();
                if (lector.rol() != Rol.LECTOR) {
                    throw new ServicioException.Validacion("El usuario seleccionado no es un lector.");
                }
                if (lector.estado() != EstadoUsuario.ACTIVO) {
                    throw new ServicioException.Validacion(
                            "El lector no está activo (estado: " + lector.estado().etiqueta() + ").");
                }
                String motivo = situacion(cn, idLector, pol).motivoBloqueo();
                if (motivo != null) {
                    throw new ServicioException.Validacion(motivo);
                }

                List<Long> idsEjemplares = new ArrayList<>();
                for (String codigo : codigos) {
                    EjemplarBloqueado e = dao.bloquearEjemplarPorCodigo(cn, codigo)
                            .orElseThrow(() -> new ServicioException.Validacion(
                                    "No existe un ejemplar con el código " + codigo + "."));
                    boolean libroActivo = dao.libroActivoCompartido(cn, e.idLibro());
                    long pendientes = ejemplarDao.contarDetallesPendientes(cn, e.idEjemplar());
                    String problema = problemaDePrestamo(codigo, e.estado(), pendientes, libroActivo);
                    if (problema != null) {
                        throw new ServicioException.Validacion(problema);
                    }
                    idsEjemplares.add(e.idEjemplar());
                }

                long idPrestamo = dao.insertarPrestamo(cn, idLector, actor.id(), pol.diasPrestamo(), observaciones);
                for (long idEjemplar : idsEjemplares) {
                    dao.insertarDetalle(cn, idPrestamo, idEjemplar);
                    if (dao.reservarEjemplar(cn, idEjemplar) != 1) {
                        throw new ServicioException.Validacion(
                                "Un ejemplar cambió de estado mientras se registraba el préstamo. Inténtalo de nuevo.");
                    }
                }
                auditoria.registrar(cn, actor.id(), "CREAR", "prestamos", String.valueOf(idPrestamo),
                        "{\"lector\":" + idLector + ",\"ejemplares\":" + idsEjemplares.size() + "}");
                return idPrestamo;
            });
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo registrar el préstamo.", e);
        }
    }

    /** Devuelve (DEVUELTO) o da por perdidos (PERDIDO) uno o varios ejemplares de un mismo préstamo. */
    public void devolver(long idPrestamo, List<Long> idsDetalle, DetallePrestamo.Estado resultado,
                         String observaciones) {
        Usuario actor = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        if (resultado != DetallePrestamo.Estado.DEVUELTO && resultado != DetallePrestamo.Estado.PERDIDO) {
            throw new ServicioException.Validacion("Indica si el ejemplar se devuelve o se marca como perdido.");
        }
        Set<Long> ids = new LinkedHashSet<>();
        if (idsDetalle != null) {
            ids.addAll(idsDetalle);
        }
        if (ids.isEmpty()) {
            throw new ServicioException.Validacion("Selecciona al menos un ejemplar.");
        }
        String obs = Validaciones.textoOpcional(observaciones, "Las observaciones", 500);
        EstadoEjemplar estadoEjemplar = resultado == DetallePrestamo.Estado.DEVUELTO
                ? EstadoEjemplar.DISPONIBLE : EstadoEjemplar.PERDIDO;

        try {
            db.enTransaccion(cn -> {
                PrestamoDao.Base prestamo = dao.bloquearPrestamo(cn, idPrestamo)
                        .orElseThrow(() -> new ServicioException.Validacion("El préstamo ya no existe."));
                if (prestamo.estado() != Prestamo.Estado.ACTIVO) {
                    throw new ServicioException.Validacion("El préstamo ya no está activo.");
                }
                dao.bloquearDetalles(cn, idPrestamo);

                Map<Long, DetallePrestamo> porId = new HashMap<>();
                for (DetallePrestamo d : dao.detalles(cn, idPrestamo)) {
                    porId.put(d.id(), d);
                }
                for (long id : ids) {
                    DetallePrestamo d = porId.get(id);
                    if (d == null) {
                        throw new ServicioException.Validacion("Uno de los ejemplares no pertenece a este préstamo.");
                    }
                    if (d.estado() != DetallePrestamo.Estado.PRESTADO) {
                        throw new ServicioException.Validacion("El ejemplar " + d.codigo() + " ya fue registrado como "
                                + d.estado().etiqueta().toLowerCase(Locale.ROOT) + ".");
                    }
                    if (dao.cerrarDetalle(cn, d.id(), resultado, obs) != 1) {
                        throw new ServicioException.Validacion(
                                "El ejemplar " + d.codigo() + " cambió mientras se registraba. Inténtalo de nuevo.");
                    }
                    dao.cambiarEstadoEjemplar(cn, d.idEjemplar(), estadoEjemplar);
                }

                boolean finalizado = dao.contarPendientes(cn, idPrestamo) == 0;
                if (finalizado) {
                    dao.actualizarEstado(cn, idPrestamo, Prestamo.Estado.FINALIZADO);
                }
                String accion = resultado == DetallePrestamo.Estado.DEVUELTO ? "DEVOLVER" : "MARCAR_PERDIDO";
                auditoria.registrar(cn, actor.id(), accion, "prestamos", String.valueOf(idPrestamo),
                        "{\"ejemplares\":" + ids.size() + ",\"finalizado\":" + finalizado + "}");
                return null;
            });
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo registrar la devolución.", e);
        }
    }

    /** Solo si todavía no se devolvió ningún ejemplar. Los ejemplares vuelven a estar disponibles. */
    public void cancelar(long idPrestamo, String motivo) {
        Usuario actor = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        String m = Validaciones.textoObligatorio(motivo, "El motivo", 200);
        try {
            db.enTransaccion(cn -> {
                PrestamoDao.Base prestamo = dao.bloquearPrestamo(cn, idPrestamo)
                        .orElseThrow(() -> new ServicioException.Validacion("El préstamo ya no existe."));
                if (prestamo.estado() != Prestamo.Estado.ACTIVO) {
                    throw new ServicioException.Validacion("El préstamo ya no está activo.");
                }
                dao.bloquearDetalles(cn, idPrestamo);
                List<DetallePrestamo> detalles = dao.detalles(cn, idPrestamo);
                for (DetallePrestamo d : detalles) {
                    if (d.estado() != DetallePrestamo.Estado.PRESTADO) {
                        throw new ServicioException.Validacion(
                                "No se puede cancelar: ya se registraron devoluciones. Registra el resto como devoluciones.");
                    }
                }
                // La restricción del esquema exige una fecha para salir de PRESTADO: se cierran como DEVUELTO
                // con la nota "Cancelado", y el préstamo queda CANCELADO (así lo muestra el historial).
                for (DetallePrestamo d : detalles) {
                    dao.cerrarDetalle(cn, d.id(), DetallePrestamo.Estado.DEVUELTO, "Cancelado");
                    dao.cambiarEstadoEjemplar(cn, d.idEjemplar(), EstadoEjemplar.DISPONIBLE);
                }
                dao.cancelarPrestamo(cn, idPrestamo, "Cancelado: " + m);
                auditoria.registrar(cn, actor.id(), "CANCELAR", "prestamos", String.valueOf(idPrestamo),
                        "{\"ejemplares\":" + detalles.size() + "}");
                return null;
            });
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo cancelar el préstamo.", e);
        }
    }

    // ---------- reglas auxiliares ----------

    /** null si el ejemplar puede prestarse; si no, el motivo para mostrar. */
    static String problemaDePrestamo(String codigo, EstadoEjemplar estado, long pendientes, boolean libroActivo) {
        if (!libroActivo) {
            return "El libro del ejemplar " + codigo + " está inactivo.";
        }
        if (estado != EstadoEjemplar.DISPONIBLE) {
            return "El ejemplar " + codigo + " no está disponible (estado: " + estado.etiqueta() + ").";
        }
        if (pendientes > 0) {
            return "El ejemplar " + codigo + " ya figura en un préstamo activo.";
        }
        return null;
    }

    private PoliticaPrestamo politica(Connection cn) throws SQLException {
        return PoliticaPrestamo.desde(configuracionDao.todas(cn));
    }

    private SituacionLector situacion(Connection cn, long idLector, PoliticaPrestamo pol) throws SQLException {
        return new SituacionLector(dao.contarActivos(cn, idLector), dao.contarAtrasados(cn, idLector),
                pol.maxPrestamosActivos(), pol.permitirPrestamoConAtrasos());
    }

    /** El responsable sale de la sesión, pero se confirma en la base que su cuenta sigue activa y con rol de personal. */
    private void exigirResponsableActivo(Connection cn, Usuario actor) throws SQLException {
        Usuario r = usuarioDao.buscarPorId(cn, actor.id(), false).map(Credencial::usuario).orElse(null);
        boolean valido = r != null && r.estado() == EstadoUsuario.ACTIVO
                && (r.rol() == Rol.ADMINISTRADOR || r.rol() == Rol.BIBLIOTECARIO);
        if (!valido) {
            throw new ServicioException.Autorizacion("Tu cuenta no puede registrar préstamos.");
        }
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        return t.length() > MAX_TEXTO_BUSQUEDA ? t.substring(0, MAX_TEXTO_BUSQUEDA) : t;
    }
}