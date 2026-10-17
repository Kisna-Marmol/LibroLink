package com.librolink.controller;

import com.librolink.model.DatosUsuario;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.FiltroUsuarios;
import com.librolink.model.MovimientoHistorial;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;
import com.librolink.service.GeneradorClaves;
import com.librolink.service.HistorialService;
import com.librolink.service.UsuarioService;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Pantalla de usuarios. En modo LECTORES muestra solo lectores y su historial de préstamos;
 * en modo CUENTAS (solo administrador) muestra todas las cuentas y permite cambiar el rol.
 */
public final class UsuariosController {

    public enum Modo { LECTORES, CUENTAS }

    private final UsuarioService servicio;
    private final HistorialService historial;
    private final Modo modo;
    private final PauseTransition pausa = new PauseTransition(Duration.millis(300));

    private int ultimaCarga;
    private int ultimaCargaHistorial;
    private boolean ocupado;
    private Usuario seleccionado;
    private EstadoUsuario estadoFiltro;
    private Rol rolFiltro;

    @FXML private TextField buscar;
    @FXML private ComboBox<String> filtroRol;
    @FXML private ComboBox<String> filtroEstado;
    @FXML private TableView<Usuario> tabla;
    @FXML private VBox panelHistorial;
    @FXML private TableView<MovimientoHistorial> tablaHistorial;
    @FXML private Label aviso;
    @FXML private Label error;

    @FXML private Label tituloFormulario;
    @FXML private ComboBox<Rol> comboRol;
    @FXML private TextField numeroSocio;
    @FXML private Button btnSugerir;
    @FXML private TextField nombre;
    @FXML private TextField apellido;
    @FXML private TextField email;
    @FXML private TextField telefono;
    @FXML private ComboBox<EstadoUsuario> comboEstado;
    @FXML private TextField password;
    @FXML private Button btnReset;

    public UsuariosController(UsuarioService servicio, HistorialService historial, Modo modo) {
        this.servicio = servicio;
        this.historial = historial;
        this.modo = modo;
    }

    @FXML
    private void initialize() {
        boolean lectores = modo == Modo.LECTORES;
        filtroRol.setVisible(!lectores);
        filtroRol.setManaged(!lectores);
        panelHistorial.setVisible(lectores);
        panelHistorial.setManaged(lectores);

        // tabla principal
        agregarColumna("Socio", 90, u -> sinNulo(u.numeroSocio()));
        agregarColumna("Nombre", 150, Usuario::nombreCompleto);
        agregarColumna("Correo", 170, Usuario::email);
        agregarColumna("Teléfono", 110, u -> sinNulo(u.telefono()));
        if (!lectores) {
            agregarColumna("Rol", 100, u -> u.rol().etiqueta());
        }
        agregarColumna("Estado", 90, u -> u.estado().etiqueta());
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("No hay usuarios."));
        tabla.setRowFactory(t -> new TableRow<Usuario>() {
            @Override
            protected void updateItem(Usuario u, boolean vacio) {
                super.updateItem(u, vacio);
                getStyleClass().removeAll("fila-inactiva", "fila-alerta");
                if (!vacio && u != null) {
                    if (u.estado() == EstadoUsuario.INACTIVO) {
                        getStyleClass().add("fila-inactiva");
                    } else if (u.estado() == EstadoUsuario.BLOQUEADO) {
                        getStyleClass().add("fila-alerta");
                    }
                }
            }
        });
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, anterior, u) -> seleccionar(u));
        TablasHistorial.configurar(tablaHistorial);

        // filtros: la posición 0 es "todos", el resto sigue el orden del enum
        List<String> estados = new ArrayList<>();
        estados.add("Todos los estados");
        for (EstadoUsuario e : EstadoUsuario.values()) {
            estados.add(e.etiqueta());
        }
        filtroEstado.getItems().setAll(estados);
        filtroEstado.getSelectionModel().selectFirst();
        filtroEstado.getSelectionModel().selectedIndexProperty().addListener((obs, anterior, idx) -> {
            int i = idx.intValue();
            estadoFiltro = i <= 0 ? null : EstadoUsuario.values()[i - 1];
            recargar();
        });

        List<String> roles = new ArrayList<>();
        roles.add("Todos los roles");
        for (Rol r : Rol.values()) {
            roles.add(r.etiqueta());
        }
        filtroRol.getItems().setAll(roles);
        filtroRol.getSelectionModel().selectFirst();
        filtroRol.getSelectionModel().selectedIndexProperty().addListener((obs, anterior, idx) -> {
            int i = idx.intValue();
            rolFiltro = i <= 0 ? null : Rol.values()[i - 1];
            recargar();
        });

        pausa.setOnFinished(e -> recargar());
        buscar.textProperty().addListener((obs, a, b) -> pausa.playFromStart());

        // formulario
        comboRol.getItems().setAll(lectores ? List.of(Rol.LECTOR) : List.of(Rol.values()));
        comboRol.setConverter(Convertidores.de(Rol::etiqueta));
        comboRol.setDisable(lectores);
        comboRol.valueProperty().addListener((obs, anterior, rol) -> {
            boolean esLector = rol == Rol.LECTOR;
            numeroSocio.setDisable(!esLector);
            btnSugerir.setDisable(!esLector);
        });
        comboEstado.getItems().setAll(EstadoUsuario.values());
        comboEstado.setConverter(Convertidores.de(EstadoUsuario::etiqueta));

        seleccionar(null);
        recargar();
    }

    private void agregarColumna(String titulo, double ancho, Function<Usuario, String> valor) {
        TableColumn<Usuario, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(f -> new ReadOnlyStringWrapper(valor.apply(f.getValue())));
        c.setPrefWidth(ancho);
        tabla.getColumns().add(c);
    }

    private static String sinNulo(String valor) {
        return valor == null ? "" : valor;
    }

    // ---------- carga de datos ----------

    private void recargar() {
        String texto = buscar.getText();
        Rol rol = modo == Modo.LECTORES ? Rol.LECTOR : rolFiltro;
        EstadoUsuario estado = estadoFiltro;
        int token = ++ultimaCarga; // descarta respuestas viejas si el usuario siguió escribiendo
        Tareas.ejecutar(
                () -> servicio.listar(new FiltroUsuarios(texto, rol, estado)),
                lista -> {
                    if (token == ultimaCarga) {
                        tabla.getItems().setAll(lista);
                    }
                },
                ex -> {
                    if (token == ultimaCarga) {
                        error.setText(Tareas.mensaje(ex));
                    }
                });
    }

    private void cargarHistorial(long idLector) {
        int token = ++ultimaCargaHistorial;
        Tareas.ejecutar(
                () -> historial.deLector(idLector),
                lista -> {
                    if (token == ultimaCargaHistorial) {
                        tablaHistorial.getItems().setAll(lista);
                    }
                },
                ex -> {
                    if (token == ultimaCargaHistorial) {
                        error.setText(Tareas.mensaje(ex));
                    }
                });
    }

    // ---------- formulario ----------

    private void seleccionar(Usuario u) {
        seleccionado = u;
        error.setText("");
        aviso.setText("");
        password.clear();
        if (u == null) {
            comboRol.setValue(Rol.LECTOR);
            numeroSocio.clear();
            nombre.clear();
            apellido.clear();
            email.clear();
            telefono.clear();
            comboEstado.setValue(EstadoUsuario.ACTIVO);
            tituloFormulario.setText(modo == Modo.LECTORES ? "Nuevo lector" : "Nueva cuenta");
            btnReset.setDisable(true);
            ultimaCargaHistorial++; // invalida cualquier carga de historial en curso
            tablaHistorial.getItems().clear();
            return;
        }
        comboRol.setValue(u.rol());
        numeroSocio.setText(sinNulo(u.numeroSocio()));
        nombre.setText(u.nombre());
        apellido.setText(u.apellido());
        email.setText(u.email());
        telefono.setText(sinNulo(u.telefono()));
        comboEstado.setValue(u.estado());
        tituloFormulario.setText(modo == Modo.LECTORES ? "Editar lector" : "Editar cuenta");
        btnReset.setDisable(false);
        if (modo == Modo.LECTORES) {
            cargarHistorial(u.id());
        }
    }

    @FXML
    private void nuevo() {
        tabla.getSelectionModel().clearSelection();
        seleccionar(null);
    }

    @FXML
    private void sugerirSocio() {
        Tareas.ejecutar(servicio::siguienteNumeroSocio, numeroSocio::setText,
                ex -> error.setText(Tareas.mensaje(ex)));
    }

    @FXML
    private void generarPassword() {
        password.setText(GeneradorClaves.generar(12));
    }

    @FXML
    private void guardar() {
        if (ocupado) {
            return; // evita doble clic = doble registro
        }
        DatosUsuario datos = new DatosUsuario(comboRol.getValue(), numeroSocio.getText(), nombre.getText(),
                apellido.getText(), email.getText(), telefono.getText(), comboEstado.getValue());
        char[] clave = password.getText().toCharArray();
        Usuario sel = seleccionado;
        ocupado = true;
        error.setText("");
        aviso.setText("");
        Tareas.ejecutar(
                () -> {
                    try {
                        if (sel == null) {
                            servicio.crear(datos, clave);
                        } else {
                            servicio.actualizar(sel.id(), datos);
                        }
                    } finally {
                        Arrays.fill(clave, '\0');
                    }
                    return null;
                },
                resultado -> {
                    ocupado = false;
                    nuevo();
                    recargar();
                    aviso.setText("Guardado.");
                },
                ex -> {
                    ocupado = false;
                    error.setText(Tareas.mensaje(ex));
                });
    }

    @FXML
    private void restablecer() {
        Usuario sel = seleccionado;
        if (sel == null || ocupado) {
            return;
        }
        char[] clave = password.getText().toCharArray();
        ocupado = true;
        error.setText("");
        aviso.setText("");
        Tareas.ejecutar(
                () -> {
                    try {
                        servicio.restablecerPassword(sel.id(), clave);
                    } finally {
                        Arrays.fill(clave, '\0');
                    }
                    return null;
                },
                resultado -> {
                    ocupado = false;
                    password.clear();
                    aviso.setText("Contraseña restablecida para " + sel.nombreCompleto() + ".");
                },
                ex -> {
                    ocupado = false;
                    error.setText(Tareas.mensaje(ex));
                });
    }
}