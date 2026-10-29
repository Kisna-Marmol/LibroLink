package com.librolink.service;

import com.librolink.model.DatosPrestamo;
import com.librolink.model.DetallePrestamo;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.FiltroPrestamos;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** La autorización y la validación ocurren antes de tocar la BD, por eso basta pasar null como Database. */
class PrestamoServiceAutorizacionTest {

    private static Sesion sesion(Rol rol) {
        Sesion s = new Sesion();
        s.iniciar(new Usuario(1L, rol, null, "Ana", "Pérez", "ana@correo.com", null, EstadoUsuario.ACTIVO));
        return s;
    }

    @Test
    void lectorYSinSesionNoPuedenOperar() {
        for (Sesion s : new Sesion[]{sesion(Rol.LECTOR), new Sesion()}) {
            PrestamoService svc = new PrestamoService(null, s);
            DatosPrestamo d = new DatosPrestamo(2L, List.of("LL-1"), null);

            assertThrows(ServicioException.Autorizacion.class,
                    () -> svc.listar(new FiltroPrestamos("", FiltroPrestamos.Estado.TODOS)));
            assertThrows(ServicioException.Autorizacion.class, () -> svc.detalles(1L));
            assertThrows(ServicioException.Autorizacion.class, svc::politica);
            assertThrows(ServicioException.Autorizacion.class, () -> svc.situacionLector(2L));
            assertThrows(ServicioException.Autorizacion.class, () -> svc.consultarEjemplar("LL-1"));
            assertThrows(ServicioException.Autorizacion.class, () -> svc.crear(d));
            assertThrows(ServicioException.Autorizacion.class,
                    () -> svc.devolver(1L, List.of(1L), DetallePrestamo.Estado.DEVUELTO, null));
            assertThrows(ServicioException.Autorizacion.class, () -> svc.cancelar(1L, "error"));
        }
    }

    @Test
    void seValidanLosDatosAntesDeTocarLaBd() {
        PrestamoService svc = new PrestamoService(null, sesion(Rol.BIBLIOTECARIO));

        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(new DatosPrestamo(null, List.of("LL-1"), null)));            // sin lector
        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(new DatosPrestamo(2L, List.of(), null)));                    // sin ejemplares
        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(new DatosPrestamo(2L, null, null)));
        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(new DatosPrestamo(2L, List.of("ll 1"), null)));              // código inválido
        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(new DatosPrestamo(2L, List.of("LL-1", "ll-1"), null)));      // repetido
        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(new DatosPrestamo(2L, List.of("LL-1"), "x".repeat(501))));   // observaciones largas

        assertThrows(ServicioException.Validacion.class,
                () -> svc.devolver(1L, List.of(), DetallePrestamo.Estado.DEVUELTO, null));
        assertThrows(ServicioException.Validacion.class,
                () -> svc.devolver(1L, null, DetallePrestamo.Estado.DEVUELTO, null));
        assertThrows(ServicioException.Validacion.class,
                () -> svc.devolver(1L, List.of(1L), null, null));
        assertThrows(ServicioException.Validacion.class,
                () -> svc.devolver(1L, List.of(1L), DetallePrestamo.Estado.PRESTADO, null)); // no es un resultado

        assertThrows(ServicioException.Validacion.class, () -> svc.cancelar(1L, "  "));      // motivo obligatorio
        assertThrows(ServicioException.Validacion.class, () -> svc.consultarEjemplar("ll 1"));
    }
}