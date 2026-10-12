package com.librolink.controller;

import com.librolink.model.Categoria;
import com.librolink.service.CategoriaService;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

import java.util.List;

public final class CategoriasController extends GestorAuxiliar<Categoria> {

    private final CategoriaService servicio;

    @FXML private TextField nombre;
    @FXML private TextField descripcion;

    public CategoriasController(CategoriaService servicio) {
        this.servicio = servicio;
    }

    @FXML
    private void initialize() {
        iniciar();
    }

    @Override
    protected List<Categoria> consultar(String texto, boolean inactivos) {
        return servicio.listar(texto, inactivos);
    }

    @Override
    protected void columnas() {
        tabla.getColumns().add(columna("Categoría", Categoria::nombre));
        tabla.getColumns().add(columna("Descripción", Categoria::descripcion));
    }

    @Override
    protected boolean estaActivo(Categoria c) {
        return c.activa();
    }

    @Override
    protected void mostrarEnFormulario(Categoria c) {
        nombre.setText(c == null ? "" : c.nombre());
        descripcion.setText(c == null ? "" : sinNulo(c.descripcion()));
    }

    @Override
    protected void guardarFormulario() {
        String n = nombre.getText();
        String d = descripcion.getText();
        Categoria sel = seleccionado;
        ejecutar(() -> {
            if (sel == null) {
                servicio.crear(n, d);
            } else {
                servicio.actualizar(sel.id(), n, d);
            }
        });
    }

    @Override
    protected void cambiarEstado(Categoria c, boolean activa) {
        servicio.cambiarEstado(c.id(), activa);
    }
}