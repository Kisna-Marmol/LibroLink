package com.librolink.controller;

import com.librolink.model.Autor;
import com.librolink.service.AutorService;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.List;

public final class AutoresController extends GestorAuxiliar<Autor> {

    private final AutorService servicio;

    @FXML private TextField nombre;
    @FXML private TextField apellido;
    @FXML private TextField nacionalidad;
    @FXML private TextArea biografia;

    public AutoresController(AutorService servicio) {
        this.servicio = servicio;
    }

    @FXML
    private void initialize() {
        iniciar();
    }

    @Override
    protected List<Autor> consultar(String texto, boolean inactivos) {
        return servicio.listar(texto, inactivos);
    }

    @Override
    protected void columnas() {
        tabla.getColumns().add(columna("Autor", Autor::nombreCompleto));
        tabla.getColumns().add(columna("Nacionalidad", Autor::nacionalidad));
    }

    @Override
    protected boolean estaActivo(Autor a) {
        return a.activo();
    }

    @Override
    protected void mostrarEnFormulario(Autor a) {
        nombre.setText(a == null ? "" : a.nombre());
        apellido.setText(a == null ? "" : sinNulo(a.apellido()));
        nacionalidad.setText(a == null ? "" : sinNulo(a.nacionalidad()));
        biografia.setText(a == null ? "" : sinNulo(a.biografia()));
    }

    @Override
    protected void guardarFormulario() {
        String n = nombre.getText();
        String ap = apellido.getText();
        String na = nacionalidad.getText();
        String bio = biografia.getText();
        Autor sel = seleccionado;
        ejecutar(() -> {
            if (sel == null) {
                servicio.crear(n, ap, na, bio);
            } else {
                servicio.actualizar(sel.id(), n, ap, na, bio);
            }
        });
    }

    @Override
    protected void cambiarEstado(Autor a, boolean activo) {
        servicio.cambiarEstado(a.id(), activo);
    }
}