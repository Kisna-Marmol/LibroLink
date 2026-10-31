package com.librolink.controller;

import com.librolink.model.DatosPolitica;
import com.librolink.model.PoliticaPrestamo;
import com.librolink.service.ConfiguracionService;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public final class CirculacionController {

    private final ConfiguracionService servicio;
    private boolean ocupado;

    @FXML private TextField diasPrestamo;
    @FXML private TextField maxEjemplares;
    @FXML private TextField maxActivos;
    @FXML private CheckBox permitirAtrasos;
    @FXML private TextField diasAviso;
    @FXML private Label aviso;
    @FXML private Label error;

    public CirculacionController(ConfiguracionService servicio) {
        this.servicio = servicio;
    }

    @FXML
    private void initialize() {
        cargar();
    }

    @FXML
    private void cargar() {
        error.setText("");
        Tareas.ejecutar(servicio::obtener, this::mostrar, ex -> error.setText(Tareas.mensaje(ex)));
    }

    private void mostrar(PoliticaPrestamo p) {
        diasPrestamo.setText(String.valueOf(p.diasPrestamo()));
        maxEjemplares.setText(String.valueOf(p.maxEjemplaresPorPrestamo()));
        maxActivos.setText(String.valueOf(p.maxPrestamosActivos()));
        permitirAtrasos.setSelected(p.permitirPrestamoConAtrasos());
        diasAviso.setText(String.valueOf(p.diasAvisoVencimiento()));
    }

    /** Solo rellena el formulario con los valores iniciales; se aplican al pulsar «Guardar». */
    @FXML
    private void restaurar() {
        mostrar(PoliticaPrestamo.porDefecto());
        error.setText("");
        aviso.setText("Valores iniciales cargados. Pulsa «Guardar» para aplicarlos.");
    }

    @FXML
    private void guardar() {
        if (ocupado) {
            return;
        }
        DatosPolitica datos = new DatosPolitica(diasPrestamo.getText(), maxEjemplares.getText(),
                maxActivos.getText(), permitirAtrasos.isSelected(), diasAviso.getText());
        ocupado = true;
        error.setText("");
        aviso.setText("");
        Tareas.ejecutar(
                () -> servicio.guardar(datos),
                cambios -> {
                    ocupado = false;
                    aviso.setText(cambios == 0
                            ? "No hubo cambios."
                            : "Se guardaron " + cambios + " cambio(s). Se aplican a los próximos préstamos.");
                    cargar();
                },
                ex -> {
                    ocupado = false;
                    error.setText(Tareas.mensaje(ex));
                });
    }
}
