package com.librolink.service;

import com.librolink.model.ResultadoReporte;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** CSV (RFC 4180) con coma, saltos CRLF y protección contra inyección de fórmulas en Excel/LibreOffice. */
public final class CsvWriter {

    private static final String BOM = "\uFEFF";

    private CsvWriter() { }

    public static String generar(ResultadoReporte reporte) {
        StringBuilder sb = new StringBuilder();
        escribirFila(sb, reporte.columnas());
        for (List<String> fila : reporte.filas()) {
            escribirFila(sb, fila);
        }
        return sb.toString();
    }

    /** UTF-8 con BOM: es lo que Excel necesita para mostrar bien las tildes. */
    public static void escribir(Path destino, ResultadoReporte reporte) throws IOException {
        Files.writeString(destino, BOM + generar(reporte), StandardCharsets.UTF_8);
    }

    private static void escribirFila(StringBuilder sb, List<String> fila) {
        for (int i = 0; i < fila.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(celda(fila.get(i)));
        }
        sb.append("\r\n");
    }

    static String celda(String valor) {
        String v = valor == null ? "" : valor;
        if (pareceFormula(v)) {
            v = "'" + v; // el apóstrofo evita que la hoja de cálculo ejecute el contenido
        }
        boolean comillas = v.indexOf(',') >= 0 || v.indexOf('"') >= 0
                || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0
                || v.startsWith(" ") || v.endsWith(" ");
        return comillas ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    /** = y @ siempre; + y - salvo que el resto parezca un número o un teléfono (p. ej. "+504 9999-8888"). */
    private static boolean pareceFormula(String v) {
        if (v.isEmpty()) {
            return false;
        }
        char c = v.charAt(0);
        if (c == '=' || c == '@' || c == '\t' || c == '\r') {
            return true;
        }
        if (c == '+' || c == '-') {
            return !v.matches("[0-9+()\\-.\\s]+");
        }
        return false;
    }
}