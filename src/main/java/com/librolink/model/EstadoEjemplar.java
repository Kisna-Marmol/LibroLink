package com.librolink.model;

/** Los nombres coinciden con el ENUM de la tabla ejemplares. */
public enum EstadoEjemplar {
    DISPONIBLE("Disponible"),
    PRESTADO("Prestado"),
    RESERVADO("Reservado"),
    PERDIDO("Perdido"),
    MANTENIMIENTO("Mantenimiento");

    private final String etiqueta;

    EstadoEjemplar(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}