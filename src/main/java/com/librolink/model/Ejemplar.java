package com.librolink.model;

import java.time.LocalDate;

public record Ejemplar(long id, long idLibro, String tituloLibro, String isbn, String codigo,
                       String ubicacion, LocalDate fechaAdquisicion, EstadoEjemplar estado,
                       String observaciones) { }