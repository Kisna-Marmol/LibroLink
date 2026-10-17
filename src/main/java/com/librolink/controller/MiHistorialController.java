package com.librolink.controller;

import com.librolink.model.MovimientoHistorial;
import com.librolink.service.HistorialService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

/** Historial de préstamos del propio lector. */
public final class MiHistorialController {

    private final HistorialService servicio;

    @FXML private TableView<MovimientoHistorial> tabla;
    @FXML private Label error;

    public MiHistorialController(HistorialService servicio) {
        this.servicio = servicio;
    }

    @FXML
    private void initialize() {
        TablasHistorial.configurar(tabla);
        Tareas.ejecutar(servicio::miHistorial,
                lista -> tabla.getItems().setAll(lista),
                ex -> error.setText(Tareas.mensaje(ex)));
    }
}