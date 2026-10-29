package com.librolink.model;

/** Ejemplar que pasó la comprobación previa y puede agregarse a un préstamo. */
public record EjemplarPrestable(long idEjemplar, String codigo, String titulo) { }