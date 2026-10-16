package com.librolink.model;

import java.time.LocalDate;

/** Datos crudos del formulario: el servicio los valida y normaliza. */
public record DatosEjemplar(Long idLibro, String codigo, String ubicacion, LocalDate fechaAdquisicion,
                            EstadoEjemplar estado, String observaciones) { }