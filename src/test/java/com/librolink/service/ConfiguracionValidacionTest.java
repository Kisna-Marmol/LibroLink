package com.librolink.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfiguracionValidacionTest {

    @Test
    void enteroEnRango() {
        assertEquals(14, Validaciones.enteroEnRango(" 14 ", "Los días", 1, 365));
        assertEquals(0, Validaciones.enteroEnRango("0", "Los días de aviso", 0, 30));
        assertEquals(365, Validaciones.enteroEnRango("365", "Los días", 1, 365));
        for (String malo : new String[]{"", "  ", "abc", "-1", "1.5", "366", "0", "1234567890", "1 4"}) {
            assertThrows(ServicioException.Validacion.class,
                    () -> Validaciones.enteroEnRango(malo, "Los días", 1, 365), malo);
        }
        assertThrows(ServicioException.Validacion.class, () -> Validaciones.enteroEnRango(null, "Los días", 1, 365));
    }

    @Test
    void jsonTextoEscapaLoPeligroso() {
        assertEquals("null", Json.texto(null));
        assertEquals("\"abc\"", Json.texto("abc"));
        assertEquals("\"a\\\"b\"", Json.texto("a\"b"));
        assertEquals("\"a\\\\b\"", Json.texto("a\\b"));
        assertEquals("\"l1\\nl2\"", Json.texto("l1\nl2"));
        assertEquals("\"\\u0001\"", Json.texto("\u0001"));
        assertEquals("\"ñ\"", Json.texto("ñ"));
    }
}