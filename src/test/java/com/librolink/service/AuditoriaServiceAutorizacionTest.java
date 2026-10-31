package com.librolink.service;

import com.librolink.model.EstadoUsuario;
import com.librolink.model.FiltroAuditoria;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** La autorización y la validación ocurren antes de tocar la BD, por eso basta pasar null como Database. */
class AuditoriaServiceAutorizacionTest {

    private static Sesion sesion(Rol rol) {
        Sesion s = new Sesion();
        s.iniciar(new Usuario(1L, rol, null, "Ana", "Pérez", "ana@correo.com", null, EstadoUsuario.ACTIVO));
        return s;
    }

    private static final FiltroAuditoria SIN_FILTRO = new FiltroAuditoria(null, null, null, null, null);

    @Test
    void soloElAdministradorConsultaLaAuditoria() {
        for (Sesion s : new Sesion[]{sesion(Rol.BIBLIOTECARIO), sesion(Rol.LECTOR), new Sesion()}) {
            AuditoriaService svc = new AuditoriaService(null, s);
            assertThrows(ServicioException.Autorizacion.class, () -> svc.pagina(SIN_FILTRO, 1));
            assertThrows(ServicioException.Autorizacion.class, svc::acciones);
            assertThrows(ServicioException.Autorizacion.class, svc::entidades);
        }
    }

    @Test
    void seValidanLaPaginaYLasFechasAntesDeTocarLaBd() {
        AuditoriaService svc = new AuditoriaService(null, sesion(Rol.ADMINISTRADOR));
        LocalDate hoy = LocalDate.now();

        assertThrows(ServicioException.Validacion.class, () -> svc.pagina(SIN_FILTRO, 0));
        assertThrows(ServicioException.Validacion.class, () -> svc.pagina(SIN_FILTRO, -3));
        assertThrows(ServicioException.Validacion.class, () -> svc.pagina(SIN_FILTRO, 20_001));
        assertThrows(ServicioException.Validacion.class,
                () -> svc.pagina(new FiltroAuditoria(hoy, hoy.minusDays(1), null, null, null), 1));
    }
}