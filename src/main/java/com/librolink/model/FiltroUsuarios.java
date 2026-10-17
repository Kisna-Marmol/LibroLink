package com.librolink.model;

/** texto, rol y estado son opcionales (null = sin filtro). */
public record FiltroUsuarios(String texto, Rol rol, EstadoUsuario estado) { }