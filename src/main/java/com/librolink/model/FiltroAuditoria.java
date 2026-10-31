package com.librolink.model;

import java.time.LocalDate;

/** Todos los campos son opcionales (null = sin filtro). El día "hasta" se incluye completo. */
public record FiltroAuditoria(LocalDate desde, LocalDate hasta, String usuario, String accion, String entidad) { }