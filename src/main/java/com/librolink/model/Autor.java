package com.librolink.model;

public record Autor(long id, String nombre, String apellido, String nacionalidad,
                    String biografia, boolean activo) {

    public String nombreCompleto() {
        return apellido == null ? nombre : nombre + " " + apellido;
    }
}