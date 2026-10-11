package com.librolink.controller;

import com.librolink.model.Rol;

import java.util.EnumSet;
import java.util.Set;

/** Secciones del menú lateral y los roles que las ven. Es solo presentación: cada servicio autoriza por su cuenta. */
public enum Seccion {
    PANEL("Panel", Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO),
    CATALOGO("Catálogo", Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO, Rol.LECTOR),
    INVENTARIO("Inventario", Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO),
    LECTORES("Lectores", Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO),
    PRESTAMOS("Préstamos", Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO),
    AUXILIARES("Catálogos auxiliares", Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO),
    REPORTES("Reportes", Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO),
    MI_HISTORIAL("Mi historial", Rol.LECTOR),
    USUARIOS("Usuarios y roles", Rol.ADMINISTRADOR),
    CONFIGURACION("Configuración", Rol.ADMINISTRADOR);

    private final String titulo;
    private final Set<Rol> roles;

    Seccion(String titulo, Rol... roles) {
        this.titulo = titulo;
        this.roles = EnumSet.copyOf(java.util.List.of(roles));
    }

    public String titulo() { return titulo; }

    public boolean visiblePara(Rol rol) { return roles.contains(rol); }
}