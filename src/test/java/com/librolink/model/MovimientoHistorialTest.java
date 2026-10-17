package com.librolink.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MovimientoHistorialTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 15);

    private static MovimientoHistorial mov(String estadoPrestamo, String estadoDetalle,
                                           LocalDate vence, LocalDateTime devuelto) {
        return new MovimientoHistorial(1L, LocalDateTime.of(2026, 10, 1, 10, 0), vence,
                estadoPrestamo, "Libro", "LL-000001", devuelto, estadoDetalle);
    }

    @Test
    void pendientes() {
        MovimientoHistorial vigente = mov("ACTIVO", "PRESTADO", HOY.plusDays(5), null);
        assertEquals("Prestado", vigente.etiqueta(HOY));
        assertFalse(vigente.atrasado(HOY));

        MovimientoHistorial venceHoy = mov("ACTIVO", "PRESTADO", HOY, null);
        assertEquals("Prestado", venceHoy.etiqueta(HOY)); // el día de vencimiento no cuenta como atraso
        assertFalse(venceHoy.atrasado(HOY));

        MovimientoHistorial vencido = mov("ACTIVO", "PRESTADO", HOY.minusDays(1), null);
        assertEquals("Atrasado", vencido.etiqueta(HOY));
        assertTrue(vencido.atrasado(HOY));
    }

    @Test
    void cerrados() {
        LocalDate vence = HOY.minusDays(5);
        assertEquals("Devuelto",
                mov("FINALIZADO", "DEVUELTO", vence, vence.atTime(23, 0)).etiqueta(HOY));
        assertEquals("Devuelto con atraso",
                mov("FINALIZADO", "DEVUELTO", vence, vence.plusDays(2).atTime(9, 0)).etiqueta(HOY));
        assertEquals("Perdido",
                mov("FINALIZADO", "PERDIDO", vence, vence.atTime(9, 0)).etiqueta(HOY));

        MovimientoHistorial cancelado = mov("CANCELADO", "PRESTADO", vence, null);
        assertEquals("Cancelado", cancelado.etiqueta(HOY));
        assertFalse(cancelado.atrasado(HOY));
    }
}