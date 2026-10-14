package com.librolink.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidacionesLibroTest {

    @Test
    void isbnValidoSeNormaliza() {
        assertEquals("9780306406157", Validaciones.isbnOpcional("978-0-306-40615-7"));
        assertEquals("0306406152", Validaciones.isbnOpcional(" 0-306-40615-2 "));
        assertEquals("080442957X", Validaciones.isbnOpcional("0-8044-2957-x"));
        assertNull(Validaciones.isbnOpcional(""));
        assertNull(Validaciones.isbnOpcional("  "));
        assertNull(Validaciones.isbnOpcional(null));
    }

    @Test
    void isbnInvalidoFalla() {
        for (String malo : new String[]{
                "9780306406158",   // dígito verificador incorrecto (ISBN-13)
                "0306406153",      // dígito verificador incorrecto (ISBN-10)
                "1234567890",      // ISBN-10 con suma inválida
                "9770306406157",   // prefijo que no es 978/979
                "123",             // longitud
                "abcdefghij",      // letras
                "03064X6152"}) {   // X fuera de la última posición
            assertThrows(ServicioException.Validacion.class, () -> Validaciones.isbnOpcional(malo), malo);
        }
    }

    @Test
    void anioOpcional() {
        assertNull(Validaciones.anioOpcional("  ", 2027));
        assertEquals(1999, Validaciones.anioOpcional(" 1999 ", 2027));
        for (String malo : new String[]{"0", "2028", "12345", "abc", "-5", "19 99"}) {
            assertThrows(ServicioException.Validacion.class,
                    () -> Validaciones.anioOpcional(malo, 2027), malo);
        }
    }
}