package com.librolink.model;

import java.time.LocalDateTime;

/** usuario y email son null cuando la acción no tiene usuario asociado (p. ej. un login con correo inexistente). */
public record RegistroAuditoria(long id, LocalDateTime fecha, String usuario, String email, String accion,
                                String entidad, String idRegistro, String detalle) { }