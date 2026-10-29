package com.librolink.service;

import com.librolink.model.EstadoEjemplar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrestamoServiceReglasTest {

    @Test
    void problemasDePrestamo() {
        assertNull(PrestamoService.problemaDePrestamo("LL-1", EstadoEjemplar.DISPONIBLE, 0, true));

        assertTrue(PrestamoService.problemaDePrestamo("LL-1", EstadoEjemplar.DISPONIBLE, 0, false)
                .contains("inactivo"));
        assertTrue(PrestamoService.problemaDePrestamo("LL-1", EstadoEjemplar.PRESTADO, 0, true)
                .contains("Prestado"));
        assertTrue(PrestamoService.problemaDePrestamo("LL-1", EstadoEjemplar.PERDIDO, 0, true)
                .contains("Perdido"));
        assertTrue(PrestamoService.problemaDePrestamo("LL-1", EstadoEjemplar.MANTENIMIENTO, 0, true)
                .contains("Mantenimiento"));
        // aunque el estado diga DISPONIBLE, un detalle sin devolver lo impide (dos préstamos activos del mismo ejemplar)
        assertTrue(PrestamoService.problemaDePrestamo("LL-1", EstadoEjemplar.DISPONIBLE, 1, true)
                .contains("préstamo activo"));
    }
}