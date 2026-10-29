package com.librolink.model;

import java.time.LocalDateTime;

/** Un ejemplar dentro de un préstamo. */
public record DetallePrestamo(long id, long idPrestamo, long idEjemplar, String codigo, String titulo,
                              LocalDateTime fechaDevolucion, Estado estado, String observaciones) {

    public enum Estado {
        PRESTADO("Prestado"),
        DEVUELTO("Devuelto"),
        PERDIDO("Perdido");

        private final String etiqueta;

        Estado(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }
}