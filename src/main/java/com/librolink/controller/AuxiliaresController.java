package com.librolink.controller;

import com.librolink.service.Servicios;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;

/** Contenedor con las pestañas de autores, categorías y editoriales. */
public final class AuxiliaresController {

    private final Servicios servicios;

    @FXML private Tab tabAutores;
    @FXML private Tab tabCategorias;
    @FXML private Tab tabEditoriales;

    public AuxiliaresController(Servicios servicios) {
        this.servicios = servicios;
    }

    @FXML
    private void initialize() {
        tabAutores.setContent(Navegador.cargarVista(
                "/fxml/autores.fxml", new AutoresController(servicios.autores())));
        tabCategorias.setContent(Navegador.cargarVista(
                "/fxml/categorias.fxml", new CategoriasController(servicios.categorias())));
        tabEditoriales.setContent(Navegador.cargarVista(
                "/fxml/editoriales.fxml", new EditorialesController(servicios.editoriales())));
    }
}