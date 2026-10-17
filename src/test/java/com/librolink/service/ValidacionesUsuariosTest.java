package com.librolink.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidacionesUsuariosTest {

    @Test
    void telefonoOpcional() {
        assertNull(Validaciones.telefonoOpcional("   "));
        assertNull(Validaciones.telefonoOpcional(null));
        assertEquals("+504 9999-8888", Validaciones.telefonoOpcional(" +504 9999-8888 "));
        for (String malo : new String[]{"123", "abc12345", "9999#8888", "1".repeat(31)}) {
            assertThrows(ServicioException.Validacion.class,
                    () -> Validaciones.telefonoOpcional(malo), malo);
        }
    }

    @Test
    void numeroSocio() {
        assertEquals("S-000001", Validaciones.numeroSocio(" s-000001 "));
        assertEquals("A12", Validaciones.numeroSocio("a12"));
        for (String malo : new String[]{"", "   ", "-1", "S 1", "S_1", "X".repeat(31)}) {
            assertThrows(ServicioException.Validacion.class, () -> Validaciones.numeroSocio(malo), malo);
        }
        assertThrows(ServicioException.Validacion.class, () -> Validaciones.numeroSocio(null));
    }

    @Test
    void generadorDeClaves() {
        for (int i = 0; i < 50; i++) {
            String clave = GeneradorClaves.generar(12);
            assertEquals(12, clave.length());
            assertDoesNotThrow(() -> Validaciones.password(clave.toCharArray()), clave);
        }
        assertNotEquals(GeneradorClaves.generar(12), GeneradorClaves.generar(12));
        assertThrows(IllegalArgumentException.class, () -> GeneradorClaves.generar(5));
    }
}