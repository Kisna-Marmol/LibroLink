package com.librolink.controller;

import com.librolink.model.ResumenPanel;
import com.librolink.model.ResumenPanel.Actividad;
import com.librolink.service.DashboardService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class PanelController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final DashboardService servicio;

    @FXML private Label valTitulos;
    @FXML private Label valDisponibles;
    @FXML private Label valActivos;
    @FXML private Label valAtrasados;
    @FXML private VBox cardAtrasos;
    @FXML private ListView<String> actividad;
    @FXML private Label error;

    public PanelController(DashboardService servicio) {
        this.servicio = servicio;
    }

    @FXML
    private void initialize() {
        actividad.setPlaceholder(new Label("Sin actividad registrada."));
        cargar();
    }

    @FXML
    private void cargar() {
        error.setText("");
        Tareas.ejecutar(servicio::obtenerResumen, this::mostrar, ex -> error.setText(Tareas.mensaje(ex)));
    }

    private void mostrar(ResumenPanel r) {
        valTitulos.setText(String.valueOf(r.titulosActivos()));
        valDisponibles.setText(String.valueOf(r.ejemplaresDisponibles()));
        valActivos.setText(String.valueOf(r.prestamosActivos()));
        valAtrasados.setText(String.valueOf(r.prestamosAtrasados()));

        cardAtrasos.getStyleClass().remove("stat-alerta");
        if (r.prestamosAtrasados() > 0) {
            cardAtrasos.getStyleClass().add("stat-alerta");
        }
        actividad.getItems().setAll(r.actividadReciente().stream().map(PanelController::linea).toList());
    }

    private static String linea(Actividad a) {
        String quien = a.usuario() == null ? "usuario desconocido" : a.usuario();
        return FECHA.format(a.fecha()) + "  ·  " + legible(a.accion()) + "  ·  " + a.entidad() + "  ·  " + quien;
    }

    private static String legible(String accion) {
        String t = accion.replace('_', ' ').toLowerCase(Locale.ROOT);
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}