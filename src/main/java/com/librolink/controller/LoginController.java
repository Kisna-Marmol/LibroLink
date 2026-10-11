package com.librolink.controller;

import com.librolink.service.AuthService;
import com.librolink.service.Sesion;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;

import java.util.Arrays;

public final class LoginController {

    private final AuthService auth;
    private final Sesion sesion;
    private final Navegador nav;

    @FXML private ImageView logo;
    @FXML private TextField email;
    @FXML private PasswordField password;
    @FXML private Label error;

    public LoginController(AuthService auth, Sesion sesion, Navegador nav) {
        this.auth = auth;
        this.sesion = sesion;
        this.nav = nav;
    }

    @FXML
    private void initialize() {
        Navegador.aplicarLogo(logo);
        error.setText("");
    }

    @FXML
    private void ingresar() {
        String correo = email.getText();
        char[] pw = password.getText().toCharArray();
        Parent raiz = password.getScene().getRoot();
        raiz.setDisable(true);
        error.setText("");

        Tareas.ejecutar(
                () -> auth.iniciarSesion(correo, pw),
                u -> {
                    Arrays.fill(pw, '\0');
                    sesion.iniciar(u);
                    nav.mostrarPrincipal();
                },
                ex -> {
                    Arrays.fill(pw, '\0');
                    raiz.setDisable(false);
                    error.setText(Tareas.mensaje(ex));
                    password.clear();
                    password.requestFocus();
                });
    }
}