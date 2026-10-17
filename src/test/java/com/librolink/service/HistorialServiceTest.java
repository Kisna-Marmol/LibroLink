package com.librolink.service;

import com.librolink.model.EstadoUsuario;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** La autorización ocurre antes de tocar la BD, por eso basta pasar null como Database. */
class HistorialServiceTest {

    private static Sesion sesion(long id, Rol rol) {
        Sesion s = new Sesion();
        s.iniciar(new Usuario(id, rol, null, "Ana", "Pérez", "ana@correo.com", null, EstadoUsuario.ACTIVO));
        return s;
    }

    @Test
    void unLectorNoVeElHistorialDeOtro() {
        HistorialService svc = new HistorialService(null, sesion(7L, Rol.LECTOR));
        assertThrows(ServicioException.Autorizacion.class, () -> svc.deLector(8L));
    }

    @Test
    void sinSesionNoHayAcceso() {
        HistorialService svc = new HistorialService(null, new Sesion());
        assertThrows(ServicioException.Autorizacion.class, () -> svc.deLector(1L));
        assertThrows(ServicioException.Autorizacion.class, svc::miHistorial);
    }

    @Test
    void miHistorialEsSoloParaLectores() {
        assertThrows(ServicioException.Autorizacion.class,
                new HistorialService(null, sesion(1L, Rol.BIBLIOTECARIO))::miHistorial);
        assertThrows(ServicioException.Autorizacion.class,
                new HistorialService(null, sesion(1L, Rol.ADMINISTRADOR))::miHistorial);
    }
}