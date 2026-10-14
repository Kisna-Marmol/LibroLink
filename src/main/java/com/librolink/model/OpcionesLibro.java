package com.librolink.model;

import java.util.List;

/** Registros activos que el formulario ofrece para un libro nuevo. */
public record OpcionesLibro(List<Categoria> categorias, List<Editorial> editoriales, List<Autor> autores) { }