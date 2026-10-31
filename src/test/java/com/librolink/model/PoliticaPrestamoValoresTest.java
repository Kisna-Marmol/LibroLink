package com.librolink.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoliticaPrestamoValoresTest {

    @Test
    void valoresPorClaveYViajeDeIda() {
        PoliticaPrestamo p = new PoliticaPrestamo(21, 5, 4, true, 3);
        Map<String, String> valores = p.valoresPorClave();

        assertEquals(List.of("dias_prestamo", "max_ejemplares_por_prestamo", "max_prestamos_activos",
                "permitir_prestamo_con_atrasos", "dias_aviso_vencimiento"), List.copyOf(valores.keySet()));
        assertEquals(List.of("21", "5", "4", "true", "3"), List.copyOf(valores.values()));
        assertEquals(p, PoliticaPrestamo.desde(valores));
        assertEquals(PoliticaPrestamo.porDefecto(), PoliticaPrestamo.desde(Map.of()));
    }
}