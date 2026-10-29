package com.librolink.model;

import java.util.List;

/** Datos del formulario de préstamo: los ejemplares se identifican por su código de inventario. */
public record DatosPrestamo(Long idLector, List<String> codigosEjemplares, String observaciones) { }