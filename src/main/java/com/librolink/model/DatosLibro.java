package com.librolink.model;

import java.util.List;

/** Datos crudos del formulario: el servicio los valida y normaliza. */
public record DatosLibro(String isbn, String titulo, String subtitulo, Long idCategoria,
                         Long idEditorial, String anio, String idioma, String descripcion,
                         List<Long> idsAutores) { }