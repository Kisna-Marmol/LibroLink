package com.librolink.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Un reporte ya formateado como tabla de texto: lo que se muestra es lo que se exporta. */
public record ResultadoReporte(TipoReporte tipo, String titulo, List<String> columnas,
                               List<List<String>> filas, boolean limitado) {

    public String nombreArchivoSugerido(LocalDate hoy) {
        return "librolink_" + tipo.codigo() + "_" + DateTimeFormatter.ofPattern("yyyyMMdd").format(hoy) + ".csv";
    }
}
