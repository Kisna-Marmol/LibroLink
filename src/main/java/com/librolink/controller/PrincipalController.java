package com.librolink.controller;

import com.librolink.model.Usuario;
import com.librolink.service.Servicios;
import com.librolink.service.Sesion;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Arrays;

/** Contenedor principal: menú lateral por rol, cabecera y área de contenido. */
public final class PrincipalController {

    private final Sesion sesion;
    private final Navegador nav;
    private final Servicios servicios;
    private final ToggleGroup grupo = new ToggleGroup();

    @FXML private VBox menu;
    @FXML private StackPane contenido;
    @FXML private Label tituloSeccion;
    @FXML private Label usuarioLabel;

    public PrincipalController(Sesion sesion, Navegador nav, Servicios servicios) {
        this.sesion = sesion;
        this.nav = nav;
        this.servicios = servicios;
    }

    @FXML
    private void initialize() {
        Usuario u = sesion.requerirUsuario();
        usuarioLabel.setText(u.nombreCompleto() + "  ·  " + u.rol());

        ToggleButton primero = null;
        Seccion primeraSeccion = null;
        for (Seccion s : Seccion.values()) {
            if (!s.visiblePara(u.rol())) {
                continue;
            }
            ToggleButton b = new ToggleButton(s.titulo());
            b.setToggleGroup(grupo);
            b.setMaxWidth(Double.MAX_VALUE);
            b.getStyleClass().add("menu-item");
            b.setOnAction(e -> {
                b.setSelected(true); // evita que un segundo clic deje el menú sin selección
                mostrar(s);
            });
            menu.getChildren().add(b);
            if (primero == null) {
                primero = b;
                primeraSeccion = s;
            }
        }
        if (primero != null) { // todos los roles ven al menos "Catálogo"
            primero.setSelected(true);
            mostrar(primeraSeccion);
        }
    }

    private void mostrar(Seccion seccion) {
        tituloSeccion.setText(seccion.titulo());
        Node vista = switch (seccion) {
            case PANEL -> Navegador.cargarVista("/fxml/panel.fxml",
                    new PanelController(servicios.dashboard()));
            case CATALOGO -> Navegador.cargarVista("/fxml/libros.fxml",
                    new LibrosController(servicios.libros()));
            case INVENTARIO -> Navegador.cargarVista("/fxml/ejemplares.fxml",
                    new EjemplaresController(servicios.ejemplares(), servicios.libros()));
            case AUXILIARES -> Navegador.cargarVista("/fxml/auxiliares.fxml",
                    new AuxiliaresController(servicios));
            case LECTORES -> Navegador.cargarVista("/fxml/usuarios.fxml",
                    new UsuariosController(servicios.usuarios(), servicios.historial(),
                            UsuariosController.Modo.LECTORES));
            case USUARIOS -> Navegador.cargarVista("/fxml/usuarios.fxml",
                    new UsuariosController(servicios.usuarios(), servicios.historial(),
                            UsuariosController.Modo.CUENTAS));
            case MI_HISTORIAL -> Navegador.cargarVista("/fxml/mi-historial.fxml",
                    new MiHistorialController(servicios.historial()));
            case PRESTAMOS -> Navegador.cargarVista("/fxml/prestamos.fxml",
                    new PrestamosController(servicios.prestamos(), servicios.usuarios()));
            case REPORTES -> Navegador.cargarVista("/fxml/reportes.fxml",
                    new ReportesController(servicios.reportes(), servicios.usuarios()));
            case CONFIGURACION -> Navegador.cargarVista("/fxml/configuracion.fxml",
                    new ConfiguracionController(servicios));
            default -> marcador(seccion);
        };
        contenido.getChildren().setAll(vista);
    }

    private Node marcador(Seccion seccion) {
        Label titulo = new Label(seccion.titulo());
        titulo.getStyleClass().add("subtitulo");
        Label texto = new Label("Este módulo se construye en una próxima etapa.");
        texto.getStyleClass().add("marcador");
        VBox caja = new VBox(8, titulo, texto);
        caja.setAlignment(Pos.TOP_LEFT);
        return caja;
    }

    @FXML
    private void cambiarPassword() {
        Dialog<Void> dialogo = new Dialog<>();
        dialogo.setTitle("Cambiar contraseña");
        dialogo.setHeaderText("Cambiar mi contraseña");
        dialogo.initOwner(contenido.getScene().getWindow());
        dialogo.getDialogPane().getStylesheets().add(
                PrincipalController.class.getResource("/css/app.css").toExternalForm());

        ButtonType aceptar = new ButtonType("Cambiar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(aceptar, ButtonType.CANCEL);

        PasswordField actual = new PasswordField();
        actual.setPromptText("Contraseña actual");
        PasswordField nueva = new PasswordField();
        nueva.setPromptText("Nueva contraseña (mínimo 10, con letras y números)");
        PasswordField confirmacion = new PasswordField();
        confirmacion.setPromptText("Repite la nueva contraseña");
        Label mensaje = new Label();
        mensaje.getStyleClass().add("mensaje-error");
        mensaje.setWrapText(true);
        VBox cuerpo = new VBox(8, actual, nueva, confirmacion, mensaje);
        cuerpo.setPrefWidth(360);
        dialogo.getDialogPane().setContent(cuerpo);

        Button botonAceptar = (Button) dialogo.getDialogPane().lookupButton(aceptar);
        botonAceptar.addEventFilter(ActionEvent.ACTION, evento -> {
            evento.consume(); // el diálogo solo se cierra cuando el cambio sale bien
            char[] a = actual.getText().toCharArray();
            char[] n = nueva.getText().toCharArray();
            char[] c = confirmacion.getText().toCharArray();
            dialogo.getDialogPane().setDisable(true);
            mensaje.setText("");
            Tareas.ejecutar(
                    () -> {
                        try {
                            servicios.usuarios().cambiarMiPassword(a, n, c);
                        } finally {
                            Arrays.fill(a, '\0');
                            Arrays.fill(n, '\0');
                            Arrays.fill(c, '\0');
                        }
                        return null;
                    },
                    resultado -> {
                        dialogo.setResult(null);
                        dialogo.close();
                        new Alert(Alert.AlertType.INFORMATION, "Tu contraseña fue actualizada.").show();
                    },
                    ex -> {
                        dialogo.getDialogPane().setDisable(false);
                        mensaje.setText(Tareas.mensaje(ex));
                    });
        });
        dialogo.showAndWait();
    }

    @FXML
    private void cerrarSesion() {
        sesion.cerrar();
        nav.mostrarLogin();
    }
}