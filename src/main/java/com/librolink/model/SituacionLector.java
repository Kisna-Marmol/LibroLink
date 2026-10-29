package com.librolink.model;

/** Situación de circulación de un lector, para decidir si puede recibir un préstamo nuevo. */
public record SituacionLector(long prestamosActivos, long prestamosAtrasados,
                              int maxPrestamosActivos, boolean permiteConAtrasos) {

    /** null si puede recibir un préstamo; si no, el motivo para mostrar. */
    public String motivoBloqueo() {
        if (prestamosActivos >= maxPrestamosActivos) {
            return "El lector ya tiene " + prestamosActivos + " préstamo(s) activo(s) (máximo "
                    + maxPrestamosActivos + ").";
        }
        if (prestamosAtrasados > 0 && !permiteConAtrasos) {
            return "El lector tiene " + prestamosAtrasados + " préstamo(s) atrasado(s).";
        }
        return null;
    }
}