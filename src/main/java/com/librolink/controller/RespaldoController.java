package com.librolink.controller;

import com.librolink.service.RespaldoService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class RespaldoController {

    private final RespaldoService servicio;
    private boolean ocupado;

    @FXML private Label herramienta;
    @FXML private Button btnRespaldar;
    @FXML private Label aviso;
    @FXML private Label error;

    public RespaldoController(RespaldoService servicio) {
        this.servicio = servicio;
    }

    @FXML
    private void initialize() {
        Tareas.ejecutar(servicio::estadoHerramienta, herramienta::setText,
                ex -> error.setText(Tareas.mensaje(ex)));
    }

    @FXML
    private void respaldar() {
        if (ocupado) {
            return;
        }
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar respaldo de la base de datos");
        selector.setInitialFileName("librolink_respaldo_"
                + DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").format(LocalDateTime.now()) + ".sql");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo SQL (*.sql)", "*.sql"));
        File archivo = selector.showSaveDialog(btnRespaldar.getScene().getWindow());
        if (archivo == null) {
            return; // el usuario canceló
        }
        Path destino = archivo.getName().toLowerCase(Locale.ROOT).endsWith(".sql")
                ? archivo.toPath()
                : archivo.toPath().resolveSibling(archivo.getName() + ".sql");

        ocupado = true;
        btnRespaldar.setDisable(true);
        error.setText("");
        aviso.setText("Generando el respaldo…");
        Tareas.ejecutar(
                () -> {
                    servicio.respaldar(destino);
                    return Files.size(destino);
                },
                bytes -> {
                    ocupado = false;
                    btnRespaldar.setDisable(false);
                    aviso.setText("Respaldo guardado en " + destino + " (" + Math.max(1, bytes / 1024) + " KB).");
                },
                ex -> {
                    ocupado = false;
                    btnRespaldar.setDisable(false);
                    aviso.setText("");
                    error.setText(Tareas.mensaje(ex));
                });
    }
}