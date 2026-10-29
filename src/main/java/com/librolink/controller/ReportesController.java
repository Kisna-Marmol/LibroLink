package com.librolink.controller;

import com.librolink.model.EstadoEjemplar;
import com.librolink.model.FiltroUsuarios;
import com.librolink.model.ResultadoReporte;
import com.librolink.model.Rol;
import com.librolink.model.TipoReporte;
import com.librolink.model.Usuario;
import com.librolink.service.ReporteService;
import com.librolink.service.UsuarioService;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ReportesController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MAX_LECTORES_EN_LISTA = 30;

    private final ReporteService servicio;
    private final UsuarioService usuarios;
    private final PauseTransition pausaLector = new PauseTransition(Duration.millis(300));

    private ResultadoReporte ultimo;
    private boolean ocupado;
    private int ultimaBusquedaLector;
    private Usuario lectorElegido;
    private EstadoEjemplar estadoInventario;

    @FXML private ComboBox<TipoReporte> comboReporte;
    @FXML private Button btnExportar;
    @FXML private Node paramsFechas;
    @FXML private DatePicker desde;
    @FXML private DatePicker hasta;
    @FXML private Label notaFechas;
    @FXML private Node paramTop;
    @FXML private ComboBox<Integer> comboTop;
    @FXML private Node paramEstado;
    @FXML private ComboBox<String> comboEstadoInv;
    @FXML private Node panelLector;
    @FXML private Label lectorElegidoLabel;
    @FXML private TextField buscarLector;
    @FXML private ListView<Usuario> listaLectores;
    @FXML private TableView<List<String>> tabla;
    @FXML private Label resumen;
    @FXML private Label aviso;
    @FXML private Label error;

    public ReportesController(ReporteService servicio, UsuarioService usuarios) {
        this.servicio = servicio;
        this.usuarios = usuarios;
    }

    @FXML
    private void initialize() {
        comboReporte.getItems().setAll(TipoReporte.values());
        comboReporte.setConverter(Convertidores.de(TipoReporte::etiqueta));
        comboReporte.valueProperty().addListener((obs, anterior, tipo) -> cambiarReporte(tipo));

        desde.setConverter(convertidorFecha());
        hasta.setConverter(convertidorFecha());
        desde.setPromptText("dd/mm/aaaa");
        hasta.setPromptText("dd/mm/aaaa");

        comboTop.getItems().setAll(10, 20, 50, 100);
        comboTop.setValue(10);

        // filtro de inventario: la posición 0 es "todos", el resto sigue el orden del enum
        List<String> estados = new ArrayList<>();
        estados.add("Todos los estados");
        for (EstadoEjemplar e : EstadoEjemplar.values()) {
            estados.add(e.etiqueta());
        }
        comboEstadoInv.getItems().setAll(estados);
        comboEstadoInv.getSelectionModel().selectFirst();
        comboEstadoInv.getSelectionModel().selectedIndexProperty().addListener((obs, anterior, idx) -> {
            int i = idx.intValue();
            estadoInventario = i <= 0 ? null : EstadoEjemplar.values()[i - 1];
        });

        // selector de lector (solo para el historial)
        listaLectores.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Usuario u, boolean vacio) {
                super.updateItem(u, vacio);
                setText(vacio || u == null ? null
                        : u.nombreCompleto() + "  ·  " + (u.numeroSocio() == null ? "" : u.numeroSocio()));
            }
        });
        listaLectores.getSelectionModel().selectedItemProperty().addListener((obs, anterior, u) -> {
            if (u != null) {
                lectorElegido = u;
                lectorElegidoLabel.setText(u.nombreCompleto());
            }
        });
        pausaLector.setOnFinished(e -> cargarLectores());
        buscarLector.textProperty().addListener((obs, a, b) -> pausaLector.playFromStart());

        tabla.setPlaceholder(new Label("Elige un reporte y pulsa «Generar»."));
        btnExportar.setDisable(true);

        comboReporte.setValue(TipoReporte.ATRASOS); // dispara cambiarReporte
        cargarLectores();
    }

    private static StringConverter<LocalDate> convertidorFecha() {
        return new StringConverter<>() {
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
        };
    }

    private static void visible(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    private void cambiarReporte(TipoReporte tipo) {
        if (tipo == null) {
            return;
        }
        visible(paramsFechas, tipo == TipoReporte.PRESTAMOS_RANGO || tipo == TipoReporte.LIBROS_SOLICITADOS);
        visible(paramTop, tipo == TipoReporte.LIBROS_SOLICITADOS);
        visible(paramEstado, tipo == TipoReporte.INVENTARIO_EJEMPLARES);
        visible(panelLector, tipo == TipoReporte.HISTORIAL_LECTOR);

        if (tipo == TipoReporte.PRESTAMOS_RANGO) {
            desde.setValue(LocalDate.now().minusDays(30));
            hasta.setValue(LocalDate.now());
            notaFechas.setText("Fechas obligatorias; el día «hasta» se incluye.");
        } else if (tipo == TipoReporte.LIBROS_SOLICITADOS) {
            desde.setValue(null);
            hasta.setValue(null);
            notaFechas.setText("Fechas opcionales: sin fechas se cuentan todos los préstamos.");
        }

        // el resultado anterior era de otro reporte: no se puede exportar por error
        ultimo = null;
        tabla.getColumns().clear();
        tabla.getItems().clear();
        resumen.setText("");
        aviso.setText("");
        error.setText("");
        btnExportar.setDisable(true);
    }

    private void cargarLectores() {
        String texto = buscarLector.getText();
        int token = ++ultimaBusquedaLector;
        Tareas.ejecutar(
                () -> usuarios.listar(new FiltroUsuarios(texto, Rol.LECTOR, null)),
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

    // ---------- generar ----------

    @FXML
    private void generar() {
        if (ocupado) {
            return;
        }
        TipoReporte tipo = comboReporte.getValue();
        Long idLector = lectorElegido == null ? null : lectorElegido.id();
        LocalDate d = desde.getValue();
        LocalDate h = hasta.getValue();
        EstadoEjemplar estado = estadoInventario;
        int top = comboTop.getValue() == null ? 10 : comboTop.getValue();

        ocupado = true;
        error.setText("");
        aviso.setText("");
        btnExportar.setDisable(true);
        Tareas.ejecutar(
                () -> ejecutar(tipo, idLector, d, h, estado, top),
                r -> {
                    ocupado = false;
                    mostrar(r);
                },
                ex -> {
                    ocupado = false;
                    error.setText(Tareas.mensaje(ex));
                });
    }

    private ResultadoReporte ejecutar(TipoReporte tipo, Long idLector, LocalDate desde, LocalDate hasta,
                                      EstadoEjemplar estado, int top) {
        return switch (tipo) {
            case ATRASOS -> servicio.atrasos();
            case HISTORIAL_LECTOR -> servicio.historialDeLector(idLector);
            case INVENTARIO_EJEMPLARES -> servicio.inventarioPorEjemplar(estado);
            case INVENTARIO_TITULOS -> servicio.inventarioPorTitulo();
            case PRESTAMOS_RANGO -> servicio.prestamosPorRango(desde, hasta);
            case LIBROS_SOLICITADOS -> servicio.librosMasSolicitados(desde, hasta, top);
        };
    }

    private void mostrar(ResultadoReporte r) {
        ultimo = r;
        tabla.getColumns().clear();
        for (int i = 0; i < r.columnas().size(); i++) {
            int indice = i;
            TableColumn<List<String>, String> c = new TableColumn<>(r.columnas().get(i));
            c.setCellValueFactory(f -> new ReadOnlyStringWrapper(f.getValue().get(indice)));
            c.setPrefWidth(ancho(r.columnas().get(i), r.filas(), indice));
            tabla.getColumns().add(c);
        }
        tabla.setPlaceholder(new Label("Sin resultados para este reporte."));
        tabla.getItems().setAll(r.filas());
        resumen.setText(r.titulo() + "  ·  " + r.filas().size() + " fila(s)"
                + (r.limitado() ? "  ·  Resultado limitado a " + servicio.limiteFilas()
                + " filas: afina los filtros." : ""));
        btnExportar.setDisable(r.filas().isEmpty());
    }

    private static double ancho(String encabezado, List<List<String>> filas, int indice) {
        int max = encabezado.length() + 2;
        for (int i = 0; i < Math.min(filas.size(), 50); i++) {
            max = Math.max(max, filas.get(i).get(indice).length());
        }
        return Math.max(70, Math.min(320, max * 7.5 + 24));
    }

    // ---------- exportar ----------

    @FXML
    private void exportar() {
        ResultadoReporte r = ultimo;
        if (r == null || ocupado) {
            return;
        }
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar reporte como CSV");
        selector.setInitialFileName(r.nombreArchivoSugerido(LocalDate.now()));
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo CSV (*.csv)", "*.csv"));
        File archivo = selector.showSaveDialog(tabla.getScene().getWindow());
        if (archivo == null) {
            return; // el usuario canceló
        }
        Path destino = archivo.getName().toLowerCase(Locale.ROOT).endsWith(".csv")
                ? archivo.toPath()
                : archivo.toPath().resolveSibling(archivo.getName() + ".csv");

        ocupado = true;
        error.setText("");
        aviso.setText("");
        Tareas.ejecutar(
                () -> {
                    servicio.exportarCsv(r, destino);
                    return null;
                },
                resultado -> {
                    ocupado = false;
                    aviso.setText("Se exportaron " + r.filas().size() + " fila(s) a " + destino);
                },
                ex -> {
                    ocupado = false;
                    error.setText(Tareas.mensaje(ex));
                });
    }
}