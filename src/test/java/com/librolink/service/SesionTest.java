package com.librolink.service;

import com.librolink.model.EstadoUsuario;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SesionTest {

    private final Usuario lector = new Usuario(1L, Rol.LECTOR, "S-001", "Ana", "Pérez",
            "ana@correo.com", null, EstadoUsuario.ACTIVO);

    @Test
    void sinSesionNoHayUsuario() {
        assertThrows(ServicioException.Autorizacion.class, () -> new Sesion().requerirUsuario());
    }

    @Test
    void requerirRolPermiteYNiega() {
        Sesion s = new Sesion();
        s.iniciar(lector);

        assertEquals(lector, s.requerirRol(Rol.LECTOR, Rol.BIBLIOTECARIO));
        assertThrows(ServicioException.Autorizacion.class, () -> s.requerirRol(Rol.ADMINISTRADOR));

        s.cerrar();
        assertThrows(ServicioException.Autorizacion.class, () -> s.requerirRol(Rol.LECTOR));
    }
}