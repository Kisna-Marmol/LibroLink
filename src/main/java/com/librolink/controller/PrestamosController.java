package com.librolink.controller;

import com.librolink.model.DatosPrestamo;
import com.librolink.model.DetallePrestamo;
import com.librolink.model.EjemplarPrestable;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.FiltroPrestamos;
import com.librolink.model.FiltroUsuarios;
import com.librolink.model.PoliticaPrestamo;
import com.librolink.model.Prestamo;
import com.librolink.model.Rol;
import com.librolink.model.SituacionLector;
import com.librolink.model.Usuario;
import com.librolink.service.PrestamoService;
import com.librolink.service.UsuarioService;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class PrestamosController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MAX_LECTORES_EN_LISTA = 30;

    private final PrestamoService servicio;
    private final UsuarioService usuarios;
    private final PauseTransition pausaTabla = new PauseTransition(Duration.millis(300));
    private final PauseTransition pausaLector = new PauseTransition(Duration.millis(300));

    private int ultimaCarga;
    private int ultimoDetalle;
    private int ultimaBusquedaLector;
    private int ultimaSituacion;
    private boolean ocupado;
    private Prestamo prestamoSeleccionado;
    private Long idParaSeleccionar;
    private FiltroPrestamos.Estado estadoFiltro = FiltroPrestamos.Estado.TODOS;
    private Usuario lectorElegido;
    private PoliticaPrestamo politica;

    @FXML private TextField buscar;
    @FXML private ComboBox<String> filtroEstado;
    @FXML private TableView<Prestamo> tabla;
    @FXML private TableView<DetallePrestamo> tablaDetalle;
    @FXML private TextField obsDevolucion;
    @FXML private Button btnDevolver;
    @FXML private Button btnPerdido;
    @FXML private Button btnCancelar;
    @FXML private Label aviso;
    @FXML private Label error;

    @FXML private Label lectorElegidoLabel;
    @FXML private TextField buscarLector;
    @FXML private ListView<Usuario> listaLectores;
    @FXML private Label situacionLabel;
    @FXML private TextField codigoEjemplar;
    @FXML private ListView<EjemplarPrestable> listaEjemplares;
    @FXML private Label venceLabel;
    @FXML private TextArea observaciones;

    public PrestamosController(PrestamoService servicio, UsuarioService usuarios) {
        this.servicio = servicio;
        this.usuarios = usuarios;
    }

    @FXML
    private void initialize() {
        // tabla de préstamos
        columna(tabla, "Nº", 60, p -> "#" + p.id());
        columna(tabla, "Lector", 170, Prestamo::lector);
        columna(tabla, "Socio", 80, p -> sinNulo(p.numeroSocio()));
        columna(tabla, "Fecha", 90, p -> FECHA.format(p.fechaPrestamo()));
        columna(tabla, "Vence", 90, p -> FECHA.format(p.fechaVencimiento()));
        columna(tabla, "Pend.", 60, p -> p.pendientes() + "/" + p.totalEjemplares());
        columna(tabla, "Estado", 100, p -> p.etiquetaEstado(LocalDate.now(), diasAviso()));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("No hay préstamos."));
        tabla.setRowFactory(t -> new TableRow<Prestamo>() {
            @Override
            protected void updateItem(Prestamo p, boolean vacio) {
                super.updateItem(p, vacio);
                getStyleClass().removeAll("fila-alerta", "fila-inactiva");
                if (!vacio && p != null) {
                    if (p.atrasado(LocalDate.now())) {
                        getStyleClass().add("fila-alerta");
                    } else if (p.estado() == Prestamo.Estado.CANCELADO) {
                        getStyleClass().add("fila-inactiva");
                    }
                }
            }
        });
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, anterior, p) -> seleccionarPrestamo(p));

        // ejemplares del préstamo (selección múltiple: Ctrl+clic o Ctrl+A)
        columna(tablaDetalle, "Ejemplar", 100, DetallePrestamo::codigo);
        columna(tablaDetalle, "Libro", 220, DetallePrestamo::titulo);
        columna(tablaDetalle, "Estado", 90, d -> d.estado().etiqueta());
        columna(tablaDetalle, "Devuelto", 100,
                d -> d.fechaDevolucion() == null ? "" : FECHA.format(d.fechaDevolucion()));
        tablaDetalle.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaDetalle.setPlaceholder(new Label("Selecciona un préstamo."));
        tablaDetalle.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tablaDetalle.setRowFactory(t -> new TableRow<DetallePrestamo>() {
            @Override
            protected void updateItem(DetallePrestamo d, boolean vacio) {
                super.updateItem(d, vacio);
                getStyleClass().removeAll("fila-alerta", "fila-inactiva");
                if (!vacio && d != null) {
                    if (d.estado() == DetallePrestamo.Estado.PERDIDO) {
                        getStyleClass().add("fila-alerta");
                    } else if (d.estado() == DetallePrestamo.Estado.DEVUELTO) {
                        getStyleClass().add("fila-inactiva");
                    }
                }
            }
        });

        // filtro por estado
        List<String> estados = new ArrayList<>();
        for (FiltroPrestamos.Estado e : FiltroPrestamos.Estado.values()) {
            estados.add(e.etiqueta());
        }
        filtroEstado.getItems().setAll(estados);
        filtroEstado.getSelectionModel().selectFirst();
        filtroEstado.getSelectionModel().selectedIndexProperty().addListener((obs, anterior, idx) -> {
            estadoFiltro = FiltroPrestamos.Estado.values()[Math.max(0, idx.intValue())];
            recargar();
        });
        pausaTabla.setOnFinished(e -> recargar());
        buscar.textProperty().addListener((obs, a, b) -> pausaTabla.playFromStart());

        // panel "nuevo préstamo"
        listaLectores.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Usuario u, boolean vacio) {
                super.updateItem(u, vacio);
                setText(vacio || u == null ? null : u.nombreCompleto() + "  ·  " + sinNulo(u.numeroSocio()));
            }
        });
        listaLectores.getSelectionModel().selectedItemProperty().addListener((obs, anterior, u) -> {
            if (u != null) {
                elegirLector(u);
            }
        });
        pausaLector.setOnFinished(e -> cargarLectores());
        buscarLector.textProperty().addListener((obs, a, b) -> pausaLector.playFromStart());

        listaEjemplares.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(EjemplarPrestable e, boolean vacio) {
                super.updateItem(e, vacio);
                setText(vacio || e == null ? null : e.codigo() + "  ·  " + e.titulo());
            }
        });

        seleccionarPrestamo(null);
        recargar();
        cargarLectores();
        Tareas.ejecutar(servicio::politica, p -> {
            politica = p;
            actualizarVence();
            tabla.refresh();
        }, ex -> error.setText(Tareas.mensaje(ex)));
    }

    private static <T> void columna(TableView<T> tabla, String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(f -> new ReadOnlyStringWrapper(valor.apply(f.getValue())));
        c.setPrefWidth(ancho);
        tabla.getColumns().add(c);
    }

    private static String sinNulo(String valor) {
        return valor == null ? "" : valor;
    }

    private int diasAviso() {
        PoliticaPrestamo p = politica;
        return p == null ? 0 : p.diasAvisoVencimiento();
    }

    // ---------- lista de préstamos ----------

    private void recargar() {
        String texto = buscar.getText();
        FiltroPrestamos.Estado estado = estadoFiltro;
        int token = ++ultimaCarga; // descarta respuestas viejas si el usuario siguió escribiendo
        Tareas.ejecutar(
                () -> servicio.listar(new FiltroPrestamos(texto, estado)),
                lista -> {
                    if (token != ultimaCarga) {
                        return;
                    }
                    tabla.getItems().setAll(lista);
                    Long id = idParaSeleccionar;
                    idParaSeleccionar = null;
                    if (id != null) {
                        for (Prestamo p : lista) {
                            if (p.id() == id) {
                                tabla.getSelectionModel().select(p);
                                break;
                            }
                        }
                    }
                },
                ex -> {
                    if (token == ultimaCarga) {
                        error.setText(Tareas.mensaje(ex));
                    }
                });
    }

    private void seleccionarPrestamo(Prestamo p) {
        prestamoSeleccionado = p;
        error.setText("");
        tablaDetalle.getItems().clear();
        boolean activo = p != null && p.estado() == Prestamo.Estado.ACTIVO;
        btnDevolver.setDisable(!activo);
        btnPerdido.setDisable(!activo);
        btnCancelar.setDisable(!activo);
        obsDevolucion.setDisable(!activo);
        int token = ++ultimoDetalle;
        if (p == null) {
            return;
        }
        Tareas.ejecutar(
                () -> servicio.detalles(p.id()),
                lista -> {
                    if (token == ultimoDetalle) {
                        tablaDetalle.getItems().setAll(lista);
                    }
                },
                ex -> {
                    if (token == ultimoDetalle) {
                        error.setText(Tareas.mensaje(ex));
                    }
                });
    }

    // ---------- devoluciones y cancelación ----------

    @FXML
    private void devolver() {
        cerrarSeleccionados(DetallePrestamo.Estado.DEVUELTO, "Devolución registrada.");
    }

    @FXML
    private void marcarPerdido() {
        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION,
                "Los ejemplares seleccionados pasarán a estado Perdido. ¿Continuar?", ButtonType.OK, ButtonType.CANCEL);
        confirmar.setHeaderText("Marcar como perdido");
        if (confirmar.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        cerrarSeleccionados(DetallePrestamo.Estado.PERDIDO, "Ejemplar(es) marcado(s) como perdido(s).");
    }

    private void cerrarSeleccionados(DetallePrestamo.Estado resultado, String mensajeOk) {
        Prestamo p = prestamoSeleccionado;
        if (p == null) {
            return;
        }
        List<Long> ids = tablaDetalle.getSelectionModel().getSelectedItems().stream()
                .filter(d -> d.estado() == DetallePrestamo.Estado.PRESTADO)
                .map(DetallePrestamo::id)
                .toList();
        if (ids.isEmpty()) {
            error.setText("Selecciona al menos un ejemplar pendiente.");
            return;
        }
        String obs = obsDevolucion.getText();
        operar(p, () -> servicio.devolver(p.id(), ids, resultado, obs), mensajeOk);
    }

    @FXML
    private void cancelar() {
        Prestamo p = prestamoSeleccionado;
        if (p == null) {
            return;
        }
        TextInputDialog dialogo = new TextInputDialog();
        dialogo.setTitle("Cancelar préstamo");
        dialogo.setHeaderText("Cancelar el préstamo #" + p.id());
        dialogo.setContentText("Motivo:");
        Optional<String> motivo = dialogo.showAndWait();
        if (motivo.isEmpty()) {
            return;
        }
        String m = motivo.get();
        operar(p, () -> servicio.cancelar(p.id(), m), "Préstamo cancelado.");
    }

    private void operar(Prestamo p, Runnable trabajo, String mensajeOk) {
        if (ocupado) {
            return; // evita doble clic = doble operación
        }
        ocupado = true;
        error.setText("");
        aviso.setText("");
        Tareas.ejecutar(
                () -> {
                    trabajo.run();
                    return null;
                },
                resultado -> {
                    ocupado = false;
                    idParaSeleccionar = p.id();
                    obsDevolucion.clear();
                    recargar();
                    aviso.setText(mensajeOk);
                },
                ex -> {
                    ocupado = false;
                    error.setText(Tareas.mensaje(ex));
                });
    }

    // ---------- nuevo préstamo ----------

    private void cargarLectores() {
        String texto = buscarLector.getText();
        int token = ++ultimaBusquedaLector;
        Tareas.ejecutar(
                () -> usuarios.listar(new FiltroUsuarios(texto, Rol.LECTOR, EstadoUsuario.ACTIVO)),
                lista -> {
                    if (token == ultimaBusquedaLector) {
                        listaLectores.getItems().setAll(lista.size() > MAX_LECTORES_EN_LISTA
                                ? lista.subList(0, MAX_LECTORES_EN_LISTA) : lista);
                    }
                },
                ex -> {
                    if (token == ultimaBusquedaLector) {
                        error.setText(Tareas.mensaje(ex));
                    }
                });
    }

    private void elegirLector(Usuario u) {
        lectorElegido = u;
        lectorElegidoLabel.setText(u.nombreCompleto() + "  ·  " + sinNulo(u.numeroSocio()));
        situacionLabel.setText("");
        situacionLabel.getStyleClass().removeAll("mensaje-error", "mensaje-ok");
        int token = ++ultimaSituacion;
        Tareas.ejecutar(
                () -> servicio.situacionLector(u.id()),
                s -> {
                    if (token == ultimaSituacion) {
                        mostrarSituacion(s);
                    }
                },
                ex -> {
                    if (token == ultimaSituacion) {
                        error.setText(Tareas.mensaje(ex));
                    }
                });
    }

    private void mostrarSituacion(SituacionLector s) {
        String motivo = s.motivoBloqueo();
        String base = "Préstamos activos: " + s.prestamosActivos() + " de " + s.maxPrestamosActivos()
                + "  ·  Atrasados: " + s.prestamosAtrasados();
        situacionLabel.getStyleClass().removeAll("mensaje-error", "mensaje-ok");
        if (motivo != null) {
            situacionLabel.setText(base + "\n" + motivo);
            situacionLabel.getStyleClass().add("mensaje-error");
        } else {
            situacionLabel.setText(base);
            situacionLabel.getStyleClass().add("mensaje-ok");
        }
    }

    private void actualizarVence() {
        PoliticaPrestamo p = politica;
        if (p == null) {
            return;
        }
        venceLabel.setText("Vencería el " + FECHA.format(LocalDate.now().plusDays(p.diasPrestamo()))
                + " (" + p.diasPrestamo() + " días). Máximo " + p.maxEjemplaresPorPrestamo() + " ejemplar(es).");
    }

    @FXML
    private void agregarEjemplar() {
        String codigo = codigoEjemplar.getText();
        if (codigo == null || codigo.isBlank()) {
            return;
        }
        error.setText("");
        if (politica != null && listaEjemplares.getItems().size() >= politica.maxEjemplaresPorPrestamo()) {
            error.setText("Un préstamo admite como máximo " + politica.maxEjemplaresPorPrestamo() + " ejemplar(es).");
            return;
        }
        Tareas.ejecutar(
                () -> servicio.consultarEjemplar(codigo),
                e -> {
                    boolean repetido = listaEjemplares.getItems().stream()
                            .anyMatch(x -> x.idEjemplar() == e.idEjemplar());
                    if (repetido) {
                        error.setText("El ejemplar " + e.codigo() + " ya está en la lista.");
                    } else {
                        listaEjemplares.getItems().add(e);
                        codigoEjemplar.clear();
                    }
                },
                ex -> error.setText(Tareas.mensaje(ex)));
    }

    @FXML
    private void quitarEjemplar() {
        EjemplarPrestable sel = listaEjemplares.getSelectionModel().getSelectedItem();
        if (sel != null) {
            listaEjemplares.getItems().remove(sel);
        }
    }

    @FXML
    private void registrar() {
        if (ocupado) {
            return; // evita doble clic = doble préstamo
        }
        DatosPrestamo datos = new DatosPrestamo(
                lectorElegido == null ? null : lectorElegido.id(),
                listaEjemplares.getItems().stream().map(EjemplarPrestable::codigo).toList(),
                observaciones.getText());
        ocupado = true;
        error.setText("");
        aviso.setText("");
        Tareas.ejecutar(
                () -> servicio.crear(datos),
                id -> {
                    ocupado = false;
                    limpiar();
                    idParaSeleccionar = id;
                    recargar();
                    aviso.setText("Préstamo #" + id + " registrado.");
                },
                ex -> {
                    ocupado = false;
                    error.setText(Tareas.mensaje(ex));
                });
    }

    @FXML
    private void limpiar() {
        lectorElegido = null;
        ultimaSituacion++;
        lectorElegidoLabel.setText("Ningún lector seleccionado");
        situacionLabel.setText("");
        situacionLabel.getStyleClass().removeAll("mensaje-error", "mensaje-ok");
        buscarLector.clear();
        listaLectores.getSelectionModel().clearSelection();
        codigoEjemplar.clear();
        listaEjemplares.getItems().clear();
        observaciones.clear();
        actualizarVence();
    }
}