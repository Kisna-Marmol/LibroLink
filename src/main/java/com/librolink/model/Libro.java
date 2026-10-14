package com.librolink.model;

import java.util.List;
import java.util.stream.Collectors;

public record Libro(long id, String isbn, String titulo, String subtitulo,
                    long idCategoria, String categoria,
                    Long idEditorial, String editorial,
                    Integer anio, String idioma, String descripcion,
                    boolean activo, List<Autor> autores,
                    long ejemplaresTotal, long ejemplaresDisponibles) {

    public String autoresTexto() {
        return autores.stream().map(Autor::nombreCompleto).collect(Collectors.joining(", "));
    }

    public Libro conAutores(List<Autor> nuevos) {
        return new Libro(id, isbn, titulo, subtitulo, idCategoria, categoria, idEditorial, editorial,
                anio, idioma, descripcion, activo, nuevos, ejemplaresTotal, ejemplaresDisponibles);
    }
}