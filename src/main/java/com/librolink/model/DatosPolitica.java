package com.librolink.model;

/** Valores del formulario de circulación; el servicio los valida (rangos estrictos). */
public record DatosPolitica(String diasPrestamo, String maxEjemplaresPorPrestamo, String maxPrestamosActivos,
                            boolean permitirPrestamoConAtrasos, String diasAvisoVencimiento) { }