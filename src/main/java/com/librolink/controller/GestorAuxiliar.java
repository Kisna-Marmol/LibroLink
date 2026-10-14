package com.librolink.controller;

import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Base de las pestañas de catálogos auxiliares: tabla + búsqueda + formulario lateral.
 * Los botones se enlazan por código; las subclases solo necesitan un initialize() que llame a iniciar().
 */
public abstract class GestorAuxiliar<T> {

    private final PauseTransition pausa = new PauseTransition(Duration.millis(300));
    private int ultimaCarga;
    private boolean ocupado;
    protected T seleccionado;

    @FXML protected TextField buscar;
    @FXML protected CheckBox incluirInactivos;
    @FXML protected TableView<T> tabla;
    @FXML protected Label tituloFormulario;
    @FXML protected Label error;
    @FXML protected Button btnGuardar;
    @FXML protected Button btnNuevo;
    @FXML protected Button btnEstado;

    // ---- lo que define cada gestor ----
    protected abstract List<T> consultar(String texto, boolean inactivos);
    protected abstract void columnas();
    protected abstract boolean estaActivo(T item);
    /** item == null significa formulario vacío (registro nuevo). */
    protected abstract void mostrarEnFormulario(T item);
    /** Debe leer los campos y llamar a ejecutar(...) con crear o actualizar según 'seleccionado'. */
    protected abstract void guardarFormulario();
    protected abstract void cambiarEstado(T item, boolean activo);
    /** Gancho opcional: se llama en el hilo de JavaFX cada vez que la tabla recibe resultados. */
    protected void alCargar(List<T> lista) { }

    protected final void iniciar() {
        columnas();
        tabla.getColumns().add(columna("Estado", i -> estaActivo(i) ? "Activo" : "Inactivo"));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("No hay registros."));
        tabla.setRowFactory(t -> new TableRow<T>() {
            @Override
            protected void updateItem(T item, boolean vacio) {
                super.updateItem(item, vacio);
                getStyleClass().remove("fila-inactiva");
                if (!vacio && item != null && !estaActivo(item)) {
                    getStyleClass().add("fila-inactiva");
                }
            }
        });
        tabla.getSelectionModel().selectedItemProperty()
                .addListener((obs, anterior, item) -> seleccionar(item));

        pausa.setOnFinished(e -> recargar());
        buscar.textProperty().addListener((obs, a, b) -> pausa.playFromStart());
        incluirInactivos.selectedProperty().addListener((obs, a, b) -> recargar());

        btnGuardar.setOnAction(e -> guardarFormulario());
        btnNuevo.setOnAction(e -> nuevo());
        btnEstado.setOnAction(e -> alternarEstado());

        seleccionar(null);
        recargar();
    }

    protected final TableColumn<T, String> columna(String titulo, Function<T, String> valor) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(f -> new ReadOnlyStringWrapper(Objects.toString(valor.apply(f.getValue()), "")));
        return c;
    }

    protected static String sinNulo(String valor) {
        return valor == null ? "" : valor;
    }

    protected final void recargar() {
        String texto = buscar.getText();
        boolean inactivos = incluirInactivos.isSelected();
        int token = ++ultimaCarga; // descarta respuestas viejas si el usuario siguió escribiendo
        Tareas.ejecutar(
                () -> consultar(texto, inactivos),
                lista -> {
                    if (token == ultimaCarga) {
                        tabla.getItems().setAll(lista);
                        alCargar(lista);
                    }
                },
                ex -> {
                    if (token == ultimaCarga) {
                        error.setText(Tareas.mensaje(ex));
                    }
                });
    }

    /** Corre la operación fuera del hilo de JavaFX; si sale bien limpia el formulario y recarga. */
    protected final void ejecutar(Runnable trabajo) {
        if (ocupado) {
            return; // evita doble clic = doble registro
        }
        ocupado = true;
        error.setText("");
        Tareas.ejecutar(
                () -> {
                    trabajo.run();
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

    private void alternarEstado() {
        T item = seleccionado;
        if (item == null) {
            return;
        }
        boolean nuevoEstado = !estaActivo(item);
        ejecutar(() -> cambiarEstado(item, nuevoEstado));
    }

    private void nuevo() {
        tabla.getSelectionModel().clearSelection();
        seleccionar(null);
    }

    private void seleccionar(T item) {
        seleccionado = item;
        error.setText("");
        mostrarEnFormulario(item);
        tituloFormulario.setText(item == null ? "Nuevo registro" : "Editar registro");
        btnEstado.setDisable(item == null);
        if (item != null) {
            btnEstado.setText(estaActivo(item) ? "Desactivar" : "Activar");
        }
    }
}