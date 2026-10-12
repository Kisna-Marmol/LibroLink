package com.librolink.service;

import com.librolink.model.EstadoUsuario;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** La autorización y la validación ocurren antes de tocar la BD, por eso basta pasar null como Database. */
class AuxiliaresAutorizacionTest {

    private static Sesion sesion(Rol rol) {
        Sesion s = new Sesion();
        s.iniciar(new Usuario(1L, rol, null, "Ana", "Pérez", "ana@correo.com", null, EstadoUsuario.ACTIVO));
        return s;
    }

    @Test
    void lectorYSinSesionNoPuedenOperar() {
        for (Sesion s : new Sesion[]{sesion(Rol.LECTOR), new Sesion()}) {
            AutorService autores = new AutorService(null, s);
            CategoriaService categorias = new CategoriaService(null, s);
            EditorialService editoriales = new EditorialService(null, s);

            assertThrows(ServicioException.Autorizacion.class, () -> autores.listar("", true));
            assertThrows(ServicioException.Autorizacion.class, () -> autores.crear("Ana", null, null, null));
            assertThrows(ServicioException.Autorizacion.class, () -> autores.cambiarEstado(1L, false));

            assertThrows(ServicioException.Autorizacion.class, () -> categorias.listar("", true));
            assertThrows(ServicioException.Autorizacion.class, () -> categorias.crear("Novela", null));
            assertThrows(ServicioException.Autorizacion.class, () -> categorias.cambiarEstado(1L, false));

            assertThrows(ServicioException.Autorizacion.class, () -> editoriales.listar("", true));
            assertThrows(ServicioException.Autorizacion.class, () -> editoriales.crear("Planeta", null, null));
            assertThrows(ServicioException.Autorizacion.class, () -> editoriales.cambiarEstado(1L, false));
        }
    }

    @Test
    void bibliotecarioPasaLaAutorizacionPeroSeValidanLosDatos() {
        Sesion s = sesion(Rol.BIBLIOTECARIO);
        assertThrows(ServicioException.Validacion.class,
                () -> new AutorService(null, s).crear("  ", null, null, null));
        assertThrows(ServicioException.Validacion.class,
                () -> new CategoriaService(null, s).crear("", null));
        assertThrows(ServicioException.Validacion.class,
                () -> new EditorialService(null, s).crear("Planeta", null, "no-es-url"));
    }
}