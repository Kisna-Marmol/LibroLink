package com.librolink.service;

import com.librolink.config.Database;

/** Contenedor de servicios de la aplicación. Cada etapa agrega los suyos aquí. */
public record Servicios(AuthService auth, DashboardService dashboard,
                        AutorService autores, CategoriaService categorias,
                        EditorialService editoriales, LibroService libros,
                        EjemplarService ejemplares, UsuarioService usuarios,
                        HistorialService historial) {

    public static Servicios crear(Database db, Sesion sesion) {
        return new Servicios(
                new AuthService(db),
                new DashboardService(db, sesion),
                new AutorService(db, sesion),
                new CategoriaService(db, sesion),
                new EditorialService(db, sesion),
                new LibroService(db, sesion),
                new EjemplarService(db, sesion),
                new UsuarioService(db, sesion),
                new HistorialService(db, sesion));
    }
}