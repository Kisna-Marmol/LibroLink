package com.librolink.service;

import com.librolink.model.DatosLibro;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.FiltroLibros;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** La autorización y la validación ocurren antes de tocar la BD, por eso basta pasar null como Database. */
class LibroServiceAutorizacionTest {

    private static Sesion sesion(Rol rol) {
        Sesion s = new Sesion();
        s.iniciar(new Usuario(1L, rol, null, "Ana", "Pérez", "ana@correo.com", null, EstadoUsuario.ACTIVO));
        return s;
    }

    private static DatosLibro datos(String titulo, String isbn, Long categoria, String anio, List<Long> autores) {
        return new DatosLibro(isbn, titulo, null, categoria, null, anio, null, null, autores);
    }

    @Test
    void lectorNoPuedeGestionar() {
        LibroService svc = new LibroService(null, sesion(Rol.LECTOR));
        DatosLibro d = datos("Un libro", null, 1L, null, List.of());

        assertFalse(svc.puedeGestionar());
        assertThrows(ServicioException.Autorizacion.class, () -> svc.crear(d));
        assertThrows(ServicioException.Autorizacion.class, () -> svc.actualizar(1L, d));
        assertThrows(ServicioException.Autorizacion.class, () -> svc.cambiarEstado(1L, false));
        assertThrows(ServicioException.Autorizacion.class, svc::opcionesFormulario);
    }

    @Test
    void sinSesionNoPuedeNiConsultar() {
        LibroService svc = new LibroService(null, new Sesion());

        assertFalse(svc.puedeGestionar());
        assertThrows(ServicioException.Autorizacion.class,
                () -> svc.buscar(new FiltroLibros("", null, false)));
        assertThrows(ServicioException.Autorizacion.class, svc::categoriasActivas);
        assertThrows(ServicioException.Autorizacion.class,
                () -> svc.crear(datos("Un libro", null, 1L, null, List.of())));
    }

    @Test
    void bibliotecarioPuedeGestionar() {
        assertTrue(new LibroService(null, sesion(Rol.BIBLIOTECARIO)).puedeGestionar());
        assertTrue(new LibroService(null, sesion(Rol.ADMINISTRADOR)).puedeGestionar());
    }

    @Test
    void seValidanLosDatosAntesDeTocarLaBd() {
        LibroService svc = new LibroService(null, sesion(Rol.BIBLIOTECARIO));

        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(datos("  ", null, 1L, null, List.of())));
        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(datos("Un libro", "123", 1L, null, List.of())));
        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(datos("Un libro", null, null, null, List.of())));
        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(datos("Un libro", null, 1L, "abc", List.of())));

        List<Long> muchos = new ArrayList<>();
        for (long i = 1; i <= 21; i++) {
            muchos.add(i);
        }
        assertThrows(ServicioException.Validacion.class,
                () -> svc.crear(datos("Un libro", null, 1L, null, muchos)));
    }
}