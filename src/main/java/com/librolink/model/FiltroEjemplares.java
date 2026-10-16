package com.librolink.model;

/** texto y estado son opcionales (null = sin filtro). */
public record FiltroEjemplares(String texto, EstadoEjemplar estado) { }