package com.librolink.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticaPrestamoTest {

    @Test
    void sinConfiguracionUsaLosValoresIniciales() {
        PoliticaPrestamo p = PoliticaPrestamo.desde(Map.of());
        assertEquals(14, p.diasPrestamo());
        assertEquals(3, p.maxEjemplaresPorPrestamo());
        assertEquals(3, p.maxPrestamosActivos());
        assertFalse(p.permitirPrestamoConAtrasos());
        assertEquals(2, p.diasAvisoVencimiento());
    }

    @Test
    void valoresValidosSeRespetanYLosInvalidosCaenAlInicial() {
        PoliticaPrestamo p = PoliticaPrestamo.desde(Map.of(
                "dias_prestamo", " 21 ",
                "max_ejemplares_por_prestamo", "5",
                "max_prestamos_activos", "0",            // fuera de rango
                "permitir_prestamo_con_atrasos", "TRUE",
                "dias_aviso_vencimiento", "abc"));       // no numérico
        assertEquals(21, p.diasPrestamo());
        assertEquals(5, p.maxEjemplaresPorPrestamo());
        assertEquals(3, p.maxPrestamosActivos());
        assertTrue(p.permitirPrestamoConAtrasos());
        assertEquals(2, p.diasAvisoVencimiento());
    }
}