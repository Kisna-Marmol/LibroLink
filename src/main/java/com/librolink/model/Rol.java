package com.librolink.model;

public enum Rol {
    ADMINISTRADOR("Administrador"),
    BIBLIOTECARIO("Bibliotecario"),
    LECTOR("Lector");

    private final String etiqueta;

    Rol(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}