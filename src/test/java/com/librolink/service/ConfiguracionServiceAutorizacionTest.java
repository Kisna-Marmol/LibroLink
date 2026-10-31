package com.librolink.service;

import com.librolink.model.DatosPolitica;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** La autorización y la validación ocurren antes de tocar la BD, por eso basta pasar null como Database. */
class ConfiguracionServiceAutorizacionTest {

    private static Sesion sesion(Rol rol) {
        Sesion s = new Sesion();
        s.iniciar(new Usuario(1L, rol, null, "Ana", "Pérez", "ana@correo.com", null, EstadoUsuario.ACTIVO));
        return s;
    }

    private static DatosPolitica valida() {
        return new DatosPolitica("14", "3", "3", false, "2");
    }

    @Test
    void soloElAdministradorPuedeVerYCambiar() {
        for (Sesion s : new Sesion[]{sesion(Rol.BIBLIOTECARIO), sesion(Rol.LECTOR), new Sesion()}) {
            ConfiguracionService svc = new ConfiguracionService(null, s);
            assertThrows(ServicioException.Autorizacion.class, svc::obtener);
            assertThrows(ServicioException.Autorizacion.class, () -> svc.guardar(valida()));
        }
    }

    @Test
    void seValidanLosRangosAntesDeTocarLaBd() {
        ConfiguracionService svc = new ConfiguracionService(null, sesion(Rol.ADMINISTRADOR));

        assertThrows(ServicioException.Validacion.class, () -> svc.guardar(new DatosPolitica("0", "3", "3", false, "2")));
        assertThrows(ServicioException.Validacion.class, () -> svc.guardar(new DatosPolitica("366", "3", "3", false, "2")));
        assertThrows(ServicioException.Validacion.class, () -> svc.guardar(new DatosPolitica("abc", "3", "3", false, "2")));
        assertThrows(ServicioException.Validacion.class, () -> svc.guardar(new DatosPolitica("14", "0", "3", false, "2")));
        assertThrows(ServicioException.Validacion.class, () -> svc.guardar(new DatosPolitica("14", "21", "3", false, "2")));
        assertThrows(ServicioException.Validacion.class, () -> svc.guardar(new DatosPolitica("14", "3", "51", false, "2")));
        assertThrows(ServicioException.Validacion.class, () -> svc.guardar(new DatosPolitica("14", "3", "3", false, "-1")));
        assertThrows(ServicioException.Validacion.class, () -> svc.guardar(new DatosPolitica("14", "3", "3", false, "31")));
        assertThrows(ServicioException.Validacion.class, () -> svc.guardar(new DatosPolitica("", "", "", false, "")));
    }
}