package com.librolink.controller;

import com.librolink.service.AuthService;
import com.librolink.service.DashboardService;
import com.librolink.service.Sesion;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

/** Cambia la pantalla de la ventana única de la aplicación. */
public final class Navegador {

    private final Scene scene;
    private final AuthService auth;
    private final Sesion sesion;
    private final DashboardService dashboard;

    public Navegador(Stage stage, AuthService auth, Sesion sesion, DashboardService dashboard) {
        this.auth = auth;
        this.sesion = sesion;
        this.dashboard = dashboard;
        this.scene = new Scene(new StackPane(), 1100, 700);
        scene.getStylesheets().add(
                Objects.requireNonNull(Navegador.class.getResource("/css/app.css")).toExternalForm());
        stage.setTitle("LibroLink — Gestor de Biblioteca");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
    }

    public void mostrarInicial() {
        if (auth.existeAdministrador()) {
            mostrarLogin();
        } else {
            mostrarSetupAdmin();
        }
    }

    public void mostrarLogin() {
        scene.setRoot(cargarVista("/fxml/login.fxml", new LoginController(auth, sesion, this)));
    }

    public void mostrarSetupAdmin() {
        scene.setRoot(cargarVista("/fxml/setup-admin.fxml", new SetupAdminController(auth, sesion, this)));
    }

    public void mostrarPrincipal() {
        scene.setRoot(cargarVista("/fxml/principal.fxml", new PrincipalController(sesion, this, dashboard)));
    }

    static Parent cargarVista(String ruta, Object controlador) {
        try {
            FXMLLoader loader = new FXMLLoader(Navegador.class.getResource(ruta));
            loader.setController(controlador);
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la vista " + ruta, e);
        }
    }

    /** Muestra el logo si existe en resources/images/logo.png; si no, no pasa nada. */
    static void aplicarLogo(ImageView view) {
        try (InputStream in = Navegador.class.getResourceAsStream("/images/logo.png")) {
            if (in != null) {
                view.setImage(new Image(in));
            }
        } catch (IOException ignored) {
            // decorativo
        }
    }
}