package com.librolink.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidacionesOpcionalesTest {

    @Test
    void textoOpcional() {
        assertNull(Validaciones.textoOpcional("   ", "El campo", 5));
        assertNull(Validaciones.textoOpcional(null, "El campo", 5));
        assertEquals("ab", Validaciones.textoOpcional(" ab ", "El campo", 5));
        assertThrows(ServicioException.Validacion.class,
                () -> Validaciones.textoOpcional("abcdef", "El campo", 5));
    }

    @Test
    void urlOpcional() {
        assertNull(Validaciones.urlOpcional("", "El sitio web"));
        assertEquals("https://editorial.com/libros", Validaciones.urlOpcional(" https://editorial.com/libros ", "El sitio web"));
        for (String mala : new String[]{"editorial.com", "ftp://editorial.com", "javascript:alert(1)", "http://", "https://a b.com"}) {
            assertThrows(ServicioException.Validacion.class,
                    () -> Validaciones.urlOpcional(mala, "El sitio web"), mala);
        }
    }
}