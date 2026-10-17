package com.librolink.service;

import com.librolink.config.Database;
import com.librolink.dao.AuditoriaDao;
import com.librolink.dao.UsuarioDao;
import com.librolink.dao.UsuarioDao.Credencial;
import com.librolink.model.DatosUsuario;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.FiltroUsuarios;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cuentas de usuario y lectores.
 * ADMINISTRADOR gestiona todas las cuentas; BIBLIOTECARIO solo las de rol LECTOR.
 */
public final class UsuarioService {

    private static final int LIMITE_RESULTADOS = 500;
    private static final int MAX_TEXTO_BUSQUEDA = 100;
    private static final String PREFIJO_SOCIO = "S-";

    private final Database db;
    private final Sesion sesion;
    private final UsuarioDao dao = new UsuarioDao();
    private final AuditoriaDao auditoria = new AuditoriaDao();
    private final PasswordHasher hasher = new PasswordHasher();

    public UsuarioService(Database db, Sesion sesion) {
        this.db = db;
        this.sesion = sesion;
    }

    public int limiteResultados() {
        return LIMITE_RESULTADOS;
    }

    public List<Usuario> listar(FiltroUsuarios filtro) {
        Usuario actor = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        Rol rol = actor.rol() == Rol.BIBLIOTECARIO ? Rol.LECTOR : filtro.rol(); // el bibliotecario solo ve lectores
        FiltroUsuarios efectivo = new FiltroUsuarios(recortar(filtro.texto()), rol, filtro.estado());
        try (Connection cn = db.getConnection()) {
            return dao.listar(cn, efectivo, LIMITE_RESULTADOS);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudieron cargar los usuarios.", e);
        }
    }

    /** Solo una sugerencia: la unicidad real la garantiza la restricción UNIQUE al guardar. */
    public String siguienteNumeroSocio() {
        sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        try (Connection cn = db.getConnection()) {
            long siguiente = dao.maximoNumeroSocio(cn, PREFIJO_SOCIO) + 1;
            return PREFIJO_SOCIO + String.format("%06d", siguiente);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo calcular el siguiente número de socio.", e);
        }
    }

    public long crear(DatosUsuario datos, char[] password) {
        Usuario actor = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        DatosUsuario n = normalizar(datos);
        exigirRolAsignable(actor, n.rol());
        Validaciones.password(password);
        String hash = hasher.hash(password); // fuera de la transacción: tarda ~0,3 s
        try {
            return db.enTransaccion(cn -> {
                long idRol = dao.idRol(cn, n.rol());
                long id = dao.insertarCompleto(cn, idRol, n, hash);
                auditoria.registrar(cn, actor.id(), "CREAR", "usuarios", String.valueOf(id),
                        "{\"rol\":\"" + n.rol().name() + "\"}");
                return id;
            });
        } catch (SQLException e) {
            throw traducir(e, "No se pudo crear el usuario.");
        }
    }

    public void actualizar(long id, DatosUsuario datos) {
        Usuario actor = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        DatosUsuario n = normalizar(datos);
        exigirRolAsignable(actor, n.rol());
        try {
            db.enTransaccion(cn -> {
                // Siempre en este orden (rol ADMIN, luego usuario) para que dos cambios simultáneos no se bloqueen entre sí.
                dao.bloquearRol(cn, Rol.ADMINISTRADOR);
                Usuario actual = dao.buscarPorId(cn, id, true)
                        .orElseThrow(() -> new ServicioException.Validacion("El usuario ya no existe."))
                        .usuario();

                if (actor.rol() == Rol.BIBLIOTECARIO && actual.rol() != Rol.LECTOR) {
                    throw new ServicioException.Autorizacion("Solo un administrador puede modificar esta cuenta.");
                }
                if (id == actor.id() && (n.rol() != actual.rol() || n.estado() != actual.estado())) {
                    throw new ServicioException.Validacion("No puedes cambiar tu propio rol ni tu estado.");
                }

                boolean cambiaRol = n.rol() != actual.rol();
                if (cambiaRol && dao.contarPrestamos(cn, id) > 0) {
                    throw new ServicioException.Validacion("No se puede cambiar el rol: el usuario tiene préstamos "
                            + "registrados. Desactívalo y crea otra cuenta si hace falta.");
                }

                boolean eraAdminActivo = actual.rol() == Rol.ADMINISTRADOR && actual.estado() == EstadoUsuario.ACTIVO;
                boolean seguiraSiéndolo = n.rol() == Rol.ADMINISTRADOR && n.estado() == EstadoUsuario.ACTIVO;
                if (eraAdminActivo && !seguiraSiéndolo && dao.contarAdministradoresActivosExcepto(cn, id) == 0) {
                    throw new ServicioException.Validacion("Debe quedar al menos un administrador activo.");
                }

                if (n.rol() == Rol.LECTOR && n.estado() == EstadoUsuario.INACTIVO
                        && actual.estado() != EstadoUsuario.INACTIVO) {
                    long pendientes = dao.contarPendientes(cn, id);
                    if (pendientes > 0) {
                        throw new ServicioException.Validacion(
                                "El lector tiene " + pendientes + " ejemplar(es) sin devolver.");
                    }
                }

                dao.actualizar(cn, id, dao.idRol(cn, n.rol()), n);

                List<String> cambios = new ArrayList<>();
                if (cambiaRol) {
                    cambios.add("\"rol_anterior\":\"" + actual.rol().name()
                            + "\",\"rol_nuevo\":\"" + n.rol().name() + "\"");
                }
                if (actual.estado() != n.estado()) {
                    cambios.add("\"estado_anterior\":\"" + actual.estado().name()
                            + "\",\"estado_nuevo\":\"" + n.estado().name() + "\"");
                }
                String detalle = cambios.isEmpty() ? null : "{" + String.join(",", cambios) + "}";
                auditoria.registrar(cn, actor.id(), "ACTUALIZAR", "usuarios", String.valueOf(id), detalle);
                return null;
            });
        } catch (SQLException e) {
            throw traducir(e, "No se pudo actualizar el usuario.");
        }
    }

    /** Define una contraseña nueva para otro usuario. Para la propia cuenta existe cambiarMiPassword. */
    public void restablecerPassword(long id, char[] nueva) {
        Usuario actor = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        Validaciones.password(nueva);
        if (id == actor.id()) {
            throw new ServicioException.Validacion(
                    "Para cambiar tu propia contraseña usa la opción «Cambiar contraseña».");
        }
        String hash = hasher.hash(nueva);
        try {
            db.enTransaccion(cn -> {
                Usuario objetivo = dao.buscarPorId(cn, id, true)
                        .orElseThrow(() -> new ServicioException.Validacion("El usuario ya no existe."))
                        .usuario();
                if (actor.rol() == Rol.BIBLIOTECARIO && objetivo.rol() != Rol.LECTOR) {
                    throw new ServicioException.Autorizacion("Solo un administrador puede modificar esta cuenta.");
                }
                dao.actualizarPasswordHash(cn, id, hash);
                auditoria.registrar(cn, actor.id(), "RESTABLECER_PASSWORD", "usuarios", String.valueOf(id), null);
                return null;
            });
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo restablecer la contraseña.", e);
        }
    }

    /** Cambio de la contraseña propia: exige la actual. Disponible para cualquier usuario autenticado. */
    public void cambiarMiPassword(char[] actual, char[] nueva, char[] confirmacion) {
        Usuario u = sesion.requerirUsuario();
        Validaciones.password(nueva);
        if (!Arrays.equals(nueva, confirmacion)) {
            throw new ServicioException.Validacion("Las contraseñas nuevas no coinciden.");
        }
        if (Arrays.equals(actual, nueva)) {
            throw new ServicioException.Validacion("La contraseña nueva debe ser distinta de la actual.");
        }
        try {
            Credencial cred = db.enTransaccion(cn -> dao.buscarPorId(cn, u.id(), false)
                    .orElseThrow(() -> new ServicioException.Autenticacion("Tu cuenta ya no existe.")));
            if (cred.usuario().estado() != EstadoUsuario.ACTIVO) {
                throw new ServicioException.Autenticacion("Tu cuenta no está activa.");
            }
            if (!hasher.verificar(actual, cred.passwordHash())) {
                // El intento fallido se registra en su propia transacción: lanzar la excepción no lo revierte.
                db.enTransaccion(cn -> {
                    auditoria.registrar(cn, u.id(), "CAMBIAR_PASSWORD_FALLIDO", "usuarios",
                            String.valueOf(u.id()), null);
                    return null;
                });
                throw new ServicioException.Autenticacion("La contraseña actual no es correcta.");
            }
            String hash = hasher.hash(nueva);
            db.enTransaccion(cn -> {
                dao.actualizarPasswordHash(cn, u.id(), hash);
                auditoria.registrar(cn, u.id(), "CAMBIAR_PASSWORD", "usuarios", String.valueOf(u.id()), null);
                return null;
            });
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo cambiar la contraseña.", e);
        }
    }

    // ---------- validación ----------

    private DatosUsuario normalizar(DatosUsuario d) {
        if (d.rol() == null) {
            throw new ServicioException.Validacion("Selecciona el rol.");
        }
        if (d.estado() == null) {
            throw new ServicioException.Validacion("Selecciona el estado.");
        }
        String nombre = Validaciones.textoObligatorio(d.nombre(), "El nombre", 80);
        String apellido = Validaciones.textoObligatorio(d.apellido(), "El apellido", 80);
        String email = Validaciones.email(d.email());
        String telefono = Validaciones.telefonoOpcional(d.telefono());
        // El número de socio solo aplica a lectores; para el personal se guarda vacío.
        String socio = d.rol() == Rol.LECTOR ? Validaciones.numeroSocio(d.numeroSocio()) : null;
        return new DatosUsuario(d.rol(), socio, nombre, apellido, email, telefono, d.estado());
    }

    private static void exigirRolAsignable(Usuario actor, Rol rol) {
        if (actor.rol() == Rol.BIBLIOTECARIO && rol != Rol.LECTOR) {
            throw new ServicioException.Autorizacion("Solo un administrador puede crear o asignar ese rol.");
        }
    }

    private static ServicioException traducir(SQLException e, String mensajeTecnico) {
        String mensaje = String.valueOf(e.getMessage());
        if (e.getErrorCode() == 1062) {
            if (mensaje.contains("uq_usuarios_email")) {
                return new ServicioException.Validacion("Ya existe un usuario con ese correo.");
            }
            if (mensaje.contains("uq_usuarios_numero_socio")) {
                return new ServicioException.Validacion("Ya existe un usuario con ese número de socio.");
            }
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
