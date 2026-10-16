package com.librolink.controller;

import com.librolink.model.DatosEjemplar;
import com.librolink.model.Ejemplar;
import com.librolink.model.EstadoEjemplar;
import com.librolink.model.FiltroEjemplares;
import com.librolink.model.FiltroLibros;
import com.librolink.model.Libro;
import com.librolink.service.EjemplarService;
import com.librolink.service.LibroService;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class EjemplaresController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SIN_LIBRO = "Ningún libro seleccionado";
    private static final int MAX_LIBROS_EN_LISTA = 30;

    private final EjemplarService servicio;
    private final LibroService libros;
    private final PauseTransition pausaTabla = new PauseTransition(Duration.millis(300));
    private final PauseTransition pausaLibros = new PauseTransition(Duration.millis(300));

    private int ultimaCarga;
    private int ultimaBusquedaLibro;
    private boolean ocupado;
    private Ejemplar seleccionado;
    private Long idLibroElegido;
    private EstadoEjemplar estadoFiltro;

    @FXML private TextField buscar;
    @FXML private ComboBox<String> filtroEstado;
    @FXML private TableView<Ejemplar> tabla;
    @FXML private Label aviso;
    @FXML private Label error;

    @FXML private Label tituloFormulario;
    @FXML private Label libroElegido;
    @FXML private TextField buscarLibro;
    @FXML private ListView<Libro> listaLibros;
    @FXML private TextField codigo;
    @FXML private TextField ubicacion;
    @FXML private DatePicker fechaAdquisicion;
    @FXML private ComboBox<EstadoEjemplar> comboEstado;
    @FXML private TextArea observaciones;

    public EjemplaresController(EjemplarService servicio, LibroService libros) {
        this.servicio = servicio;
        this.libros = libros;
    }

    @FXML
    private void initialize() {
        // tabla
        agregarColumna("Código", 110, Ejemplar::codigo);
        agregarColumna("Libro", 220, Ejemplar::tituloLibro);
        agregarColumna("Ubicación", 120, e -> sinNulo(e.ubicacion()));
        agregarColumna("Estado", 100, e -> e.estado().etiqueta());
        agregarColumna("Adquisición", 100,
                e -> e.fechaAdquisicion() == null ? "" : FECHA.format(e.fechaAdquisicion()));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("No hay ejemplares."));
        tabla.setRowFactory(t -> new TableRow<Ejemplar>() {
            @Override
            protected void updateItem(Ejemplar e, boolean vacio) {
                super.updateItem(e, vacio);
                getStyleClass().remove("fila-inactiva");
                if (!vacio && e != null && e.estado() == EstadoEjemplar.PERDIDO) {
                    getStyleClass().add("fila-inactiva");
                }
            }
        });
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, anterior, e) -> seleccionar(e));

        // filtro por estado: la posición 0 es "todos", el resto sigue el orden del enum
        List<String> opciones = new ArrayList<>();
        opciones.add("Todos los estados");
        for (EstadoEjemplar e : EstadoEjemplar.values()) {
            opciones.add(e.etiqueta());
        }
        filtroEstado.getItems().setAll(opciones);
        filtroEstado.getSelectionModel().selectFirst();
        filtroEstado.getSelectionModel().selectedIndexProperty().addListener((obs, anterior, idx) -> {
            int i = idx.intValue();
            estadoFiltro = i <= 0 ? null : EstadoEjemplar.values()[i - 1];
            recargar();
        });

        pausaTabla.setOnFinished(e -> recargar());
        buscar.textProperty().addListener((obs, a, b) -> pausaTabla.playFromStart());

        // formulario
        comboEstado.getItems().setAll(EstadoEjemplar.values());
        comboEstado.setConverter(new StringConverter<>() {
            @Override
            public String toString(EstadoEjemplar e) {
                return e == null ? "" : e.etiqueta();
            }

            @Override
            public EstadoEjemplar fromString(String s) {
                return null;
            }
        });
        fechaAdquisicion.setPromptText("dd/mm/aaaa");
        fechaAdquisicion.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate f) {
                return f == null ? "" : FECHA.format(f);
            }

            @Override
            public LocalDate fromString(String s) {
                if (s == null || s.isBlank()) {
                    return null;
                }
                try {
                    return LocalDate.parse(s.trim(), FECHA);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });

        // selector de libro: búsqueda en vivo + lista corta
        listaLibros.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Libro l, boolean vacio) {
                super.updateItem(l, vacio);
                if (vacio || l == null) {
                    setText(null);
                } else {
                    String autores = l.autoresTexto();
                    setText(autores.isEmpty() ? l.titulo() : l.titulo() + "  ·  " + autores);
                }
            }
        });
        listaLibros.getSelectionModel().selectedItemProperty().addListener((obs, anterior, l) -> {
            if (l != null) {
                elegirLibro(l.id(), l.titulo());
            }
        });
        pausaLibros.setOnFinished(e -> cargarLibros());
        buscarLibro.textProperty().addListener((obs, a, b) -> pausaLibros.playFromStart());

        seleccionar(null);
        recargar();
        cargarLibros();
    }

    private void agregarColumna(String titulo, double ancho, Function<Ejemplar, String> valor) {
        TableColumn<Ejemplar, String> c = new TableColumn<>(titulo);
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
        EstadoEjemplar estado = estadoFiltro;
        int token = ++ultimaCarga; // descarta respuestas viejas si el usuario siguió escribiendo
        Tareas.ejecutar(
                () -> servicio.listar(new FiltroEjemplares(texto, estado)),
                lista -> {
                    if (token == ultimaCarga) {
                        tabla.getItems().setAll(lista);
                        aviso.setText(lista.size() >= servicio.limiteResultados()
                                ? "Se muestran los primeros " + servicio.limiteResultados()
                                + " resultados. Afina la búsqueda."
                                : "");
                    }
                },
                ex -> {
                    if (token == ultimaCarga) {
                        error.setText(Tareas.mensaje(ex));
                    }
                });
    }

    private void cargarLibros() {
        String texto = buscarLibro.getText();
        int token = ++ultimaBusquedaLibro;
        Tareas.ejecutar(
                () -> libros.buscar(new FiltroLibros(texto, null, false)),
                lista -> {
                    if (token == ultimaBusquedaLibro) {
                        listaLibros.getItems().setAll(lista.size() > MAX_LIBROS_EN_LISTA
                                ? lista.subList(0, MAX_LIBROS_EN_LISTA) : lista);
                    }
                },
                ex -> {
                    if (token == ultimaBusquedaLibro) {
                        error.setText(Tareas.mensaje(ex));
                    }
                });
    }

    // ---------- formulario ----------

    private void elegirLibro(long id, String titulo) {
        idLibroElegido = id;
        libroElegido.setText(titulo);
    }

    private void seleccionar(Ejemplar e) {
        seleccionado = e;
        error.setText("");
        if (e == null) {
            idLibroElegido = null;
            libroElegido.setText(SIN_LIBRO);
            codigo.clear();
            ubicacion.clear();
            fechaAdquisicion.setValue(null);
            comboEstado.setValue(EstadoEjemplar.DISPONIBLE);
            observaciones.clear();
            tituloFormulario.setText("Nuevo ejemplar");
            bloquearLibroYEstado(false);
            return;
        }
        elegirLibro(e.idLibro(), e.tituloLibro());
        codigo.setText(e.codigo());
        ubicacion.setText(sinNulo(e.ubicacion()));
        fechaAdquisicion.setValue(e.fechaAdquisicion());
        comboEstado.setValue(e.estado());
        observaciones.setText(sinNulo(e.observaciones()));
        tituloFormulario.setText("Editar ejemplar");
        // un ejemplar prestado no cambia de libro ni de estado hasta su devolución (el servicio también lo exige)
        bloquearLibroYEstado(e.estado() == EstadoEjemplar.PRESTADO);
    }

    private void bloquearLibroYEstado(boolean bloquear) {
        buscarLibro.setDisable(bloquear);
        listaLibros.setDisable(bloquear);
        comboEstado.setDisable(bloquear);
    }

    @FXML
    private void nuevo() {
        tabla.getSelectionModel().clearSelection();
        seleccionar(null);
    }

    @FXML
    private void sugerirCodigo() {
        Tareas.ejecutar(servicio::siguienteCodigo, codigo::setText, ex -> error.setText(Tareas.mensaje(ex)));
    }

    @FXML
    private void guardar() {
        if (ocupado) {
            return; // evita doble clic = doble registro
        }
        DatosEjemplar datos = new DatosEjemplar(idLibroElegido, codigo.getText(), ubicacion.getText(),
                fechaAdquisicion.getValue(), comboEstado.getValue(), observaciones.getText());
        Ejemplar sel = seleccionado;
        ocupado = true;
        error.setText("");
        Tareas.ejecutar(
                () -> {
                    if (sel == null) {
                        servicio.crear(datos);
                    } else {
                        servicio.actualizar(sel.id(), datos);
                    }
                    return null;
                },
                resultado -> {
                    ocupado = false;
                    nuevo();
                    recargar();
                },
                ex -> {
                    ocupado = false;
                    error.setText(Tareas.mensaje(ex));
                });
    }
}