package com.librolink.controller;

import com.librolink.model.MovimientoHistorial;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/** Columnas comunes de las tablas de historial de préstamos. */
final class TablasHistorial {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private TablasHistorial() { }

    static void configurar(TableView<MovimientoHistorial> tabla) {
        columna(tabla, "Préstamo", 80, m -> "#" + m.idPrestamo());
        columna(tabla, "Fecha", 90, m -> FECHA.format(m.fechaPrestamo()));
        columna(tabla, "Vence", 90, m -> FECHA.format(m.fechaVencimiento()));
        columna(tabla, "Libro", 220, MovimientoHistorial::titulo);
        columna(tabla, "Ejemplar", 100, MovimientoHistorial::codigoEjemplar);
        columna(tabla, "Devuelto", 90,
                m -> m.fechaDevolucion() == null ? "" : FECHA.format(m.fechaDevolucion()));
        columna(tabla, "Estado", 140, m -> m.etiqueta(LocalDate.now()));

        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Sin préstamos registrados."));
        tabla.setRowFactory(t -> new TableRow<MovimientoHistorial>() {
            @Override
            protected void updateItem(MovimientoHistorial m, boolean vacio) {
                super.updateItem(m, vacio);
                getStyleClass().remove("fila-alerta");
                if (!vacio && m != null && m.atrasado(LocalDate.now())) {
                    getStyleClass().add("fila-alerta");
                }
            }
        });
    }

    private static void columna(TableView<MovimientoHistorial> tabla, String titulo, double ancho,
                                Function<MovimientoHistorial, String> valor) {
        TableColumn<MovimientoHistorial, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(f -> new ReadOnlyStringWrapper(valor.apply(f.getValue())));
        c.setPrefWidth(ancho);
        tabla.getColumns().add(c);
    }
}