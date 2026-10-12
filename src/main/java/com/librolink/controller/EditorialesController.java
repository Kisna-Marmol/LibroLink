package com.librolink.controller;

import com.librolink.model.Editorial;
import com.librolink.service.EditorialService;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

import java.util.List;

public final class EditorialesController extends GestorAuxiliar<Editorial> {

    private final EditorialService servicio;

    @FXML private TextField nombre;
    @FXML private TextField pais;
    @FXML private TextField sitioWeb;

    public EditorialesController(EditorialService servicio) {
        this.servicio = servicio;
    }

    @FXML
    private void initialize() {
        iniciar();
    }

    @Override
    protected List<Editorial> consultar(String texto, boolean inactivos) {
        return servicio.listar(texto, inactivos);
    }

    @Override
    protected void columnas() {
        tabla.getColumns().add(columna("Editorial", Editorial::nombre));
        tabla.getColumns().add(columna("País", Editorial::pais));
        tabla.getColumns().add(columna("Sitio web", Editorial::sitioWeb));
    }

    @Override
    protected boolean estaActivo(Editorial e) {
        return e.activa();
    }

    @Override
    protected void mostrarEnFormulario(Editorial e) {
        nombre.setText(e == null ? "" : e.nombre());
        pais.setText(e == null ? "" : sinNulo(e.pais()));
        sitioWeb.setText(e == null ? "" : sinNulo(e.sitioWeb()));
    }

    @Override
    protected void guardarFormulario() {
        String n = nombre.getText();
        String p = pais.getText();
        String w = sitioWeb.getText();
        Editorial sel = seleccionado;
        ejecutar(() -> {
            if (sel == null) {
                servicio.crear(n, p, w);
            } else {
                servicio.actualizar(sel.id(), n, p, w);
            }
        });
    }

    @Override
    protected void cambiarEstado(Editorial e, boolean activa) {
        servicio.cambiarEstado(e.id(), activa);
    }
}