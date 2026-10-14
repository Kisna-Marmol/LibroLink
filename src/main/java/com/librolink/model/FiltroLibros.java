package com.librolink.model;

/** texto e idCategoria son opcionales (null = sin filtro). */
public record FiltroLibros(String texto, Long idCategoria, boolean incluirInactivos) { }