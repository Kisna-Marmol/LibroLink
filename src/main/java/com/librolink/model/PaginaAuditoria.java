package com.librolink.model;

import java.util.List;

public record PaginaAuditoria(List<RegistroAuditoria> registros, int pagina, boolean hayMas) { }