package com.librolink.model;

/** Datos del formulario (crudos al llegar al servicio, normalizados al llegar al DAO). */
public record DatosUsuario(Rol rol, String numeroSocio, String nombre, String apellido,
                           String email, String telefono, EstadoUsuario estado) { }
