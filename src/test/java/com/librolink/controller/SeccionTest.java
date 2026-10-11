package com.librolink.controller;

import com.librolink.model.Rol;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeccionTest {

    private static Set<Seccion> visibles(Rol rol) {
        Set<Seccion> r = EnumSet.noneOf(Seccion.class);
        for (Seccion s : Seccion.values()) {
            if (s.visiblePara(rol)) {
                r.add(s);
            }
        }
        return r;
    }

    @Test
    void lectorSoloVeCatalogoEHistorial() {
        assertEquals(EnumSet.of(Seccion.CATALOGO, Seccion.MI_HISTORIAL), visibles(Rol.LECTOR));
    }

    @Test
    void bibliotecarioNoVeAdministracion() {
        Set<Seccion> v = visibles(Rol.BIBLIOTECARIO);
        assertTrue(v.contains(Seccion.PRESTAMOS));
        assertFalse(v.contains(Seccion.USUARIOS));
        assertFalse(v.contains(Seccion.CONFIGURACION));
        assertFalse(v.contains(Seccion.MI_HISTORIAL));
    }

    @Test
    void administradorVeTodoMenosHistorialDeLector() {
        Set<Seccion> esperado = EnumSet.allOf(Seccion.class);
        esperado.remove(Seccion.MI_HISTORIAL);
        assertEquals(esperado, visibles(Rol.ADMINISTRADOR));
    }
}