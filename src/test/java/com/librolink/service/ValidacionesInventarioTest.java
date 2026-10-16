package com.librolink.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidacionesInventarioTest {

    @Test
    void codigoValidoSeNormalizaAMayusculas() {
        assertEquals("LL-000001", Validaciones.codigoInventario(" ll-000001 "));
        assertEquals("A.1/2_B-3", Validaciones.codigoInventario("a.1/2_b-3"));
        assertEquals("X".repeat(50), Validaciones.codigoInventario("x".repeat(50)));
    }

    @Test
    void codigoInvalidoFalla() {
        for (String malo : new String[]{"", "   ", "-abc", "ll 001", "código1", "ab#c", "X".repeat(51)}) {
            assertThrows(ServicioException.Validacion.class,
                    () -> Validaciones.codigoInventario(malo), malo);
        }
        assertThrows(ServicioException.Validacion.class, () -> Validaciones.codigoInventario(null));
    }
}