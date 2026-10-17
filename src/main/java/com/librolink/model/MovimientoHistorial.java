package com.librolink.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una fila del historial: un ejemplar dentro de un préstamo. */
public record MovimientoHistorial(long idPrestamo, LocalDateTime fechaPrestamo, LocalDate fechaVencimiento,
                                  String estadoPrestamo, String titulo, String codigoEjemplar,
                                  LocalDateTime fechaDevolucion, String estadoDetalle) {

    /** Atrasado = vencimiento anterior a hoy con el ejemplar sin devolver (el día de vencimiento no cuenta). */
    public boolean atrasado(LocalDate hoy) {
        return "PRESTADO".equals(estadoDetalle)
                && !"CANCELADO".equals(estadoPrestamo)
                && fechaVencimiento.isBefore(hoy);
    }

    public String etiqueta(LocalDate hoy) {
        if ("CANCELADO".equals(estadoPrestamo)) {
            return "Cancelado";
        }
        return switch (estadoDetalle) {
            case "PERDIDO" -> "Perdido";
            case "DEVUELTO" -> fechaDevolucion != null
                    && fechaDevolucion.toLocalDate().isAfter(fechaVencimiento)
                    ? "Devuelto con atraso" : "Devuelto";
            default -> atrasado(hoy) ? "Atrasado" : "Prestado";
        };
    }
}