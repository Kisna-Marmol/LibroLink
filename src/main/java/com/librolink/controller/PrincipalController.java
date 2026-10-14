package com.librolink.controller;

import com.librolink.model.Usuario;
import com.librolink.service.Servicios;
import com.librolink.service.Sesion;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

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
            case AUXILIARES -> Navegador.cargarVista("/fxml/auxiliares.fxml",
                    new AuxiliaresController(servicios));
            case CATALOGO -> Navegador.cargarVista("/fxml/libros.fxml",
                    new LibrosController(servicios.libros()));
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
    private void cerrarSesion() {
        sesion.cerrar();
        nav.mostrarLogin();
    }
}