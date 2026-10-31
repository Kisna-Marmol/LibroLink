package com.librolink.controller;

import com.librolink.model.FiltroAuditoria;
import com.librolink.model.PaginaAuditoria;
import com.librolink.model.RegistroAuditoria;
import com.librolink.service.AuditoriaService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class AuditoriaController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String TODAS = "Todas las acciones";
    private static final String TODAS_ENTIDADES = "Todas las entidades";

    private final AuditoriaService servicio;

    /** El filtro se fija al pulsar «Buscar»: anterior/siguiente no cambian aunque se editen los campos. */
    private FiltroAuditoria filtroAplicado = new FiltroAuditoria(null, null, null, null, null);
    private int paginaActual = 1;
    private int ultimaCarga;

    @FXML private DatePicker desde;
    @FXML private DatePicker hasta;
    @FXML private TextField usuario;
    @FXML private ComboBox<String> comboAccion;
    @FXML private ComboBox<String> comboEntidad;
    @FXML private TableView<RegistroAuditoria> tabla;
    @FXML private Button btnAnterior;
    @FXML private Button btnSiguiente;
    @FXML private Label paginaLabel;
    @FXML private Label error;

    public AuditoriaController(AuditoriaService servicio) {
        this.servicio = servicio;
    }

    @FXML
    private void initialize() {
        desde.setConverter(convertidorFecha());
        hasta.setConverter(convertidorFecha());
        desde.setPromptText("dd/mm/aaaa");
        hasta.setPromptText("dd/mm/aaaa");

        agregarColumna("Fecha", 150, r -> FECHA_HORA.format(r.fecha()));
        agregarColumna("Usuario", 190, r -> r.usuario() == null ? "(sin usuario)" : r.usuario());
        agregarColumna("Acción", 190, RegistroAuditoria::accion);
        agregarColumna("Entidad", 120, RegistroAuditoria::entidad);
        agregarColumna("Registro", 90, r -> r.idRegistro() == null ? "" : r.idRegistro());
        agregarColumna("Detalle", 380, r -> r.detalle() == null ? "" : r.detalle());
        tabla.setPlaceholder(new Label("No hay registros con estos filtros."));

        comboAccion.getItems().setAll(TODAS);
        comboAccion.getSelectionModel().selectFirst();
        comboEntidad.getItems().setAll(TODAS_ENTIDADES);
        comboEntidad.getSelectionModel().selectFirst();
        Tareas.ejecutar(servicio::acciones, lista -> {
            List<String> items = new ArrayList<>();
            items.add(TODAS);
            items.addAll(lista);
            comboAccion.getItems().setAll(items);
            comboAccion.getSelectionModel().selectFirst();
        }, ex -> error.setText(Tareas.mensaje(ex)));
        Tareas.ejecutar(servicio::entidades, lista -> {
            List<String> items = new ArrayList<>();
            items.add(TODAS_ENTIDADES);
            items.addAll(lista);
            comboEntidad.getItems().setAll(items);
            comboEntidad.getSelectionModel().selectFirst();
        }, ex -> error.setText(Tareas.mensaje(ex)));

        mostrarPagina(1);
    }

    private void agregarColumna(String titulo, double ancho, Function<RegistroAuditoria, String> valor) {
        TableColumn<RegistroAuditoria, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(f -> new ReadOnlyStringWrapper(valor.apply(f.getValue())));
        c.setPrefWidth(ancho);
        tabla.getColumns().add(c);
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

    @FXML
    private void buscar() {
        int accion = comboAccion.getSelectionModel().getSelectedIndex();
        int entidad = comboEntidad.getSelectionModel().getSelectedIndex();
        filtroAplicado = new FiltroAuditoria(desde.getValue(), hasta.getValue(), usuario.getText(),
                accion <= 0 ? null : comboAccion.getValue(),
                entidad <= 0 ? null : comboEntidad.getValue());
        mostrarPagina(1);
    }

    @FXML
    private void anterior() {
        if (paginaActual > 1) {
            mostrarPagina(paginaActual - 1);
        }
    }

    @FXML
    private void siguiente() {
        mostrarPagina(paginaActual + 1);
    }

    private void mostrarPagina(int pagina) {
        error.setText("");
        FiltroAuditoria filtro = filtroAplicado;
        int token = ++ultimaCarga;
        Tareas.ejecutar(
                () -> servicio.pagina(filtro, pagina),
                p -> {
                    if (token == ultimaCarga) {
                        mostrar(p);
                    }
                },
                ex -> {
                    if (token == ultimaCarga) {
                        error.setText(Tareas.mensaje(ex));
                    }
                });
    }

    private void mostrar(PaginaAuditoria p) {
        paginaActual = p.pagina();
        tabla.getItems().setAll(p.registros());
        paginaLabel.setText("Página " + p.pagina() + "  ·  " + p.registros().size() + " registro(s)");
        btnAnterior.setDisable(p.pagina() <= 1);
        btnSiguiente.setDisable(!p.hayMas());
    }
}