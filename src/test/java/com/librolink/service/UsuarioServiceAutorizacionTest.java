package com.librolink.service;

import com.librolink.model.DatosUsuario;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.FiltroUsuarios;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** La autorización y la validación ocurren antes de tocar la BD, por eso basta pasar null como Database. */
class UsuarioServiceAutorizacionTest {

    private static final long ID_ACTOR = 10L;

    private static Sesion sesion(Rol rol) {
        Sesion s = new Sesion();
        s.iniciar(new Usuario(ID_ACTOR, rol, null, "Ana", "Pérez", "ana@correo.com", null, EstadoUsuario.ACTIVO));
        return s;
    }

    private static DatosUsuario datos(Rol rol, String socio, String nombre, String email, String telefono,
                                      EstadoUsuario estado) {
        return new DatosUsuario(rol, socio, nombre, "Gómez", email, telefono, estado);
    }

    private static DatosUsuario lectorValido() {
        return datos(Rol.LECTOR, "S-000001", "Luis", "luis@correo.com", null, EstadoUsuario.ACTIVO);
    }

    private static char[] claveValida() {
        return "Biblioteca2026".toCharArray();
    }

    @Test
    void lectorYSinSesionNoPuedenOperar() {
        for (Sesion s : new Sesion[]{sesion(Rol.LECTOR), new Sesion()}) {
            UsuarioService svc = new UsuarioService(null, s);

            assertThrows(ServicioException.Autorizacion.class,
                    () -> svc.listar(new FiltroUsuarios("", null, null)));
            assertThrows(ServicioException.Autorizacion.class, svc::siguienteNumeroSocio);
            assertThrows(ServicioException.Autorizacion.class, () -> svc.crear(lectorValido(), claveValida()));
            assertThrows(ServicioException.Autorizacion.class, () -> svc.actualizar(1L, lectorValido()));
            assertThrows(ServicioException.Autorizacion.class, () -> svc.restablecerPassword(1L, claveValida()));
        }
    }

    @Test
    void bibliotecarioSoloGestionaLectores() {
        UsuarioService svc = new UsuarioService(null, sesion(Rol.BIBLIOTECARIO));
        DatosUsuario personal = datos(Rol.BIBLIOTECARIO, null, "Luis", "luis@correo.com", null,
                EstadoUsuario.ACTIVO);
        DatosUsuario admin = datos(Rol.ADMINISTRADOR, null, "Luis", "luis@correo.com", null,
                EstadoUsuario.ACTIVO);

        assertThrows(ServicioException.Autorizacion.class, () -> svc.crear(personal, claveValida()));
        assertThrows(ServicioException.Autorizacion.class, () -> svc.crear(admin, claveValida()));
        assertThrows(ServicioException.Autorizacion.class, () -> svc.actualizar(5L, admin));
    }

    @Test
    void seValidanLosDatosAntesDeTocarLaBd() {
        UsuarioService svc = new UsuarioService(null, sesion(Rol.BIBLIOTECARIO));
        char[] clave = claveValida();

        assertThrows(ServicioException.Validacion.class, () -> svc.crear(
                datos(Rol.LECTOR, "S-1", " ", "luis@correo.com", null, EstadoUsuario.ACTIVO), clave));    // sin nombre
        assertThrows(ServicioException.Validacion.class, () -> svc.crear(
                datos(Rol.LECTOR, "S-1", "Luis", "no-es-correo", null, EstadoUsuario.ACTIVO), clave));    // correo
        assertThrows(ServicioException.Validacion.class, () -> svc.crear(
                datos(Rol.LECTOR, null, "Luis", "luis@correo.com", null, EstadoUsuario.ACTIVO), clave));  // sin socio
        assertThrows(ServicioException.Validacion.class, () -> svc.crear(
                datos(Rol.LECTOR, "S-1", "Luis", "luis@correo.com", "abc", EstadoUsuario.ACTIVO), clave)); // teléfono
        assertThrows(ServicioException.Validacion.class, () -> svc.crear(
                datos(null, "S-1", "Luis", "luis@correo.com", null, EstadoUsuario.ACTIVO), clave));       // sin rol
        assertThrows(ServicioException.Validacion.class, () -> svc.crear(
                datos(Rol.LECTOR, "S-1", "Luis", "luis@correo.com", null, null), clave));                 // sin estado
        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(lectorValido(), "corta1".toCharArray()));                                  // clave débil

        assertThrows(ServicioException.Validacion.class,
                () -> svc.restablecerPassword(5L, "soloLetrasLargas".toCharArray()));                      // clave débil
        assertThrows(ServicioException.Validacion.class,
                () -> svc.restablecerPassword(ID_ACTOR, clave));                                           // cuenta propia
    }

    @Test
    void cambiarMiPassword() {
        char[] actual = "Biblioteca2026".toCharArray();
        char[] nueva = "OtraClave2027".toCharArray();

        assertThrows(ServicioException.Autorizacion.class,
                () -> new UsuarioService(null, new Sesion()).cambiarMiPassword(actual, nueva, nueva));

        UsuarioService svc = new UsuarioService(null, sesion(Rol.LECTOR));
        assertThrows(ServicioException.Validacion.class,
                () -> svc.cambiarMiPassword(actual, "corta1".toCharArray(), "corta1".toCharArray()));
        assertThrows(ServicioException.Validacion.class,
                () -> svc.cambiarMiPassword(actual, nueva, "Distinta2028".toCharArray()));
        assertThrows(ServicioException.Validacion.class,
                () -> svc.cambiarMiPassword(actual, actual.clone(), actual.clone()));
    }
}