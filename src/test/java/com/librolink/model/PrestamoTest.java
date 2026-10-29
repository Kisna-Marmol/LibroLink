package com.librolink.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrestamoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 15);

    private static Prestamo prestamo(Prestamo.Estado estado, LocalDate vence, long pendientes) {
        return new Prestamo(1L, 2L, "Ana Pérez", "S-000001", "Beto Gómez",
                LocalDateTime.of(2026, 10, 1, 9, 0), vence, estado, null, 3, pendientes);
    }

    @Test
    void atrasoYEtiquetas() {
        assertTrue(prestamo(Prestamo.Estado.ACTIVO, HOY.minusDays(1), 1).atrasado(HOY));
        assertFalse(prestamo(Prestamo.Estado.ACTIVO, HOY, 1).atrasado(HOY));                    // vence hoy: no es atraso
        assertFalse(prestamo(Prestamo.Estado.ACTIVO, HOY.minusDays(5), 0).atrasado(HOY));       // sin pendientes
        assertFalse(prestamo(Prestamo.Estado.FINALIZADO, HOY.minusDays(5), 0).atrasado(HOY));

        assertEquals("Atrasado", prestamo(Prestamo.Estado.ACTIVO, HOY.minusDays(1), 1).etiquetaEstado(HOY, 2));
        assertEquals("Por vencer", prestamo(Prestamo.Estado.ACTIVO, HOY, 1).etiquetaEstado(HOY, 2));
        assertEquals("Por vencer", prestamo(Prestamo.Estado.ACTIVO, HOY.plusDays(2), 1).etiquetaEstado(HOY, 2));
        assertEquals("Activo", prestamo(Prestamo.Estado.ACTIVO, HOY.plusDays(3), 1).etiquetaEstado(HOY, 2));
        assertEquals("Activo", prestamo(Prestamo.Estado.ACTIVO, HOY.plusDays(1), 1).etiquetaEstado(HOY, 0));
        assertEquals("Finalizado", prestamo(Prestamo.Estado.FINALIZADO, HOY.minusDays(9), 0).etiquetaEstado(HOY, 2));
        assertEquals("Cancelado", prestamo(Prestamo.Estado.CANCELADO, HOY.minusDays(9), 0).etiquetaEstado(HOY, 2));
    }
}