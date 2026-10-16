package com.librolink.service;

import com.librolink.model.DatosEjemplar;
import com.librolink.model.EstadoEjemplar;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.FiltroEjemplares;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** La autorización y la validación ocurren antes de tocar la BD, por eso basta pasar null como Database. */
class EjemplarServiceAutorizacionTest {

    private static Sesion sesion(Rol rol) {
        Sesion s = new Sesion();
        s.iniciar(new Usuario(1L, rol, null, "Ana", "Pérez", "ana@correo.com", null, EstadoUsuario.ACTIVO));
        return s;
    }

    private static DatosEjemplar datos(Long libro, String codigo, LocalDate fecha, EstadoEjemplar estado) {
        return new DatosEjemplar(libro, codigo, null, fecha, estado, null);
    }

    @Test
    void lectorYSinSesionNoPuedenOperar() {
        for (Sesion s : new Sesion[]{sesion(Rol.LECTOR), new Sesion()}) {
            EjemplarService svc = new EjemplarService(null, s);
            DatosEjemplar d = datos(1L, "LL-1", null, EstadoEjemplar.DISPONIBLE);

            assertThrows(ServicioException.Autorizacion.class, () -> svc.listar(new FiltroEjemplares("", null)));
            assertThrows(ServicioException.Autorizacion.class, svc::siguienteCodigo);
            assertThrows(ServicioException.Autorizacion.class, () -> svc.crear(d));
            assertThrows(ServicioException.Autorizacion.class, () -> svc.actualizar(1L, d));
        }
    }

    @Test
    void seValidanLosDatosAntesDeTocarLaBd() {
        EjemplarService svc = new EjemplarService(null, sesion(Rol.BIBLIOTECARIO));
        LocalDate manana = LocalDate.now().plusDays(1);

        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(datos(null, "LL-1", null, EstadoEjemplar.DISPONIBLE)));      // sin libro
        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(datos(1L, " ", null, EstadoEjemplar.DISPONIBLE)));           // sin código
        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(datos(1L, "ll 1", null, EstadoEjemplar.DISPONIBLE)));        // código inválido
        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(datos(1L, "LL-1", manana, EstadoEjemplar.DISPONIBLE)));      // fecha futura
        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(datos(1L, "LL-1", null, null)));                             // sin estado
        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(datos(1L, "LL-1", null, EstadoEjemplar.PRESTADO)));          // lo asigna un préstamo
    }
}