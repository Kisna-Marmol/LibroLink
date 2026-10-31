package com.librolink.controller;

import com.librolink.service.Servicios;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;

/** Contenedor con las pestañas de circulación, auditoría y respaldo. */
public final class ConfiguracionController {

    private final Servicios servicios;

    @FXML private Tab tabCirculacion;
    @FXML private Tab tabAuditoria;
    @FXML private Tab tabRespaldo;

    public ConfiguracionController(Servicios servicios) {
        this.servicios = servicios;
    }

    @FXML
    private void initialize() {
        tabCirculacion.setContent(Navegador.cargarVista(
                "/fxml/circulacion.fxml", new CirculacionController(servicios.configuracion())));
        tabAuditoria.setContent(Navegador.cargarVista(
                "/fxml/auditoria.fxml", new AuditoriaController(servicios.auditoria())));
        tabRespaldo.setContent(Navegador.cargarVista(
                "/fxml/respaldo.fxml", new RespaldoController(servicios.respaldo())));
    }
}