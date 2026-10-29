package com.librolink.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultadoReporteTest {

    @Test
    void nombreDeArchivoSugerido() {
        ResultadoReporte r = new ResultadoReporte(TipoReporte.ATRASOS, "Atrasos", List.of("A"), List.of(), false);
        assertEquals("librolink_atrasos_20261028.csv", r.nombreArchivoSugerido(LocalDate.of(2026, 10, 28)));

        ResultadoReporte h = new ResultadoReporte(TipoReporte.HISTORIAL_LECTOR, "H", List.of("A"), List.of(), false);
        assertEquals("librolink_historial_lector_20260105.csv", h.nombreArchivoSugerido(LocalDate.of(2026, 1, 5)));
    }
}