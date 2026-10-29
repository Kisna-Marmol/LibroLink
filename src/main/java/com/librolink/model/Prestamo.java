package com.librolink.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Prestamo(long id, long idLector, String lector, String numeroSocio, String responsable,
                       LocalDateTime fechaPrestamo, LocalDate fechaVencimiento, Estado estado,
                       String observaciones, long totalEjemplares, long pendientes) {

    public enum Estado {
        ACTIVO("Activo"),
        FINALIZADO("Finalizado"),
        CANCELADO("Cancelado");

        private final String etiqueta;

        Estado(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }

    /** Atrasado = activo, con ejemplares sin devolver y vencimiento anterior a hoy (el día de vencimiento no cuenta). */
    public boolean atrasado(LocalDate hoy) {
        return estado == Estado.ACTIVO && pendientes > 0 && fechaVencimiento.isBefore(hoy);
    }

    public String etiquetaEstado(LocalDate hoy, int diasAviso) {
        if (atrasado(hoy)) {
            return "Atrasado";
        }
        if (estado == Estado.ACTIVO && pendientes > 0 && !fechaVencimiento.isAfter(hoy.plusDays(diasAviso))) {
            return "Por vencer";
        }
        return estado.etiqueta();
    }
}