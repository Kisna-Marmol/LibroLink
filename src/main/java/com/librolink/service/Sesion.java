package com.librolink.service;

import com.librolink.model.Rol;
import com.librolink.model.Usuario;

import java.util.Optional;

/** Usuario autenticado de la ejecución actual. Los servicios la usan para autorizar. */
public final class Sesion {

    private volatile Usuario usuario;

    public void iniciar(Usuario usuario) { this.usuario = usuario; }

    public void cerrar() { this.usuario = null; }

    public Optional<Usuario> usuario() { return Optional.ofNullable(usuario); }

    public Usuario requerirUsuario() {
        if (usuario == null) {
            throw new ServicioException.Autorizacion("Debes iniciar sesión.");
        }
        return usuario;
    }

    public Usuario requerirRol(Rol... permitidos) {
        Usuario u = requerirUsuario();
        for (Rol r : permitidos) {
            if (u.rol() == r) {
                return u;
            }
        }
        throw new ServicioException.Autorizacion("No tienes permiso para esta operación.");
    }
}