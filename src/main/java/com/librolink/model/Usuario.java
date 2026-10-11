package com.librolink.model;

/** Usuario sin el hash de contraseña: nunca viaja fuera del DAO/servicio de autenticación. */
public record Usuario(long id, Rol rol, String numeroSocio, String nombre, String apellido,
                      String email, String telefono, EstadoUsuario estado) {

    public String nombreCompleto() {
        return nombre + " " + apellido;
    }
}