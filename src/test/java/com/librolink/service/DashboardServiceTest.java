package com.librolink.service;

import com.librolink.model.EstadoUsuario;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** La autorización se verifica antes de tocar la BD, por eso basta pasar null como Database. */
class DashboardServiceTest {

    @Test
    void lectorNoPuedeVerEstadisticas() {
        Sesion s = new Sesion();
        s.iniciar(new Usuario(1L, Rol.LECTOR, "S-001", "Ana", "Pérez",
                "ana@correo.com", null, EstadoUsuario.ACTIVO));
        DashboardService svc = new DashboardService(null, s);
        assertThrows(ServicioException.Autorizacion.class, svc::obtenerResumen);
    }

    @Test
    void sinSesionNoHayAcceso() {
        DashboardService svc = new DashboardService(null, new Sesion());
        assertThrows(ServicioException.Autorizacion.class, svc::obtenerResumen);
    }
}