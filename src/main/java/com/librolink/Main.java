package com.librolink;

import com.librolink.config.AppConfig;
import com.librolink.config.Database;
import com.librolink.controller.Navegador;
import com.librolink.service.Servicios;
import com.librolink.service.Sesion;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class Main extends Application {

    private Database database;

    @Override
    public void start(Stage stage) {
        try {
            AppConfig config = AppConfig.load();
            database = new Database(config);

            Sesion sesion = new Sesion();
            Servicios servicios = Servicios.crear(database, sesion);

            Navegador navegador = new Navegador(stage, servicios, sesion);
            navegador.mostrarInicial();
            stage.show();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR,
                    "No se pudo iniciar LibroLink:\n" + e.getMessage()).showAndWait();
            stop();
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        if (database != null) {
            database.close();
            database = null;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}