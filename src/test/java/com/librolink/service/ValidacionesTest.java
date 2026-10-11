package com.librolink.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidacionesTest {

    @Test
    void emailSeNormaliza() {
        assertEquals("ana@correo.com", Validaciones.email("  Ana@Correo.COM "));
    }

    @Test
    void emailInvalidoFalla() {
        for (String malo : new String[]{"", "   ", "sin-arroba", "a@b", "a@@b.com", "a b@c.com"}) {
            assertThrows(ServicioException.Validacion.class, () -> Validaciones.email(malo), malo);
        }
    }

    @Test
    void passwordValida() {
        assertDoesNotThrow(() -> Validaciones.password("Biblioteca2026".toCharArray()));
    }

    @Test
    void passwordInvalidaFalla() {
        for (String mala : new String[]{"corta1", "soloLetrasLargas", "12345678901", "a1".repeat(40)}) {
            assertThrows(ServicioException.Validacion.class,
                    () -> Validaciones.password(mala.toCharArray()), mala);
        }
    }

    @Test
    void textoObligatorio() {
        assertEquals("Ana", Validaciones.textoObligatorio(" Ana ", "El nombre", 10));
        assertThrows(ServicioException.Validacion.class,
                () -> Validaciones.textoObligatorio("  ", "El nombre", 10));
        assertThrows(ServicioException.Validacion.class,
                () -> Validaciones.textoObligatorio("x".repeat(11), "El nombre", 10));
    }
}