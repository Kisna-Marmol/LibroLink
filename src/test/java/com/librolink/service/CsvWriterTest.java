package com.librolink.service;

import com.librolink.model.ResultadoReporte;
import com.librolink.model.TipoReporte;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvWriterTest {

    private static ResultadoReporte reporte(List<String> columnas, List<List<String>> filas) {
        return new ResultadoReporte(TipoReporte.ATRASOS, "Prueba", columnas, filas, false);
    }

    @Test
    void escapaComasComillasYSaltosDeLinea() {
        ResultadoReporte r = reporte(List.of("A", "B"),
                List.of(List.of("x,y", "z"), List.of("\"q\"", "línea1\nlínea2")));
        assertEquals("A,B\r\n\"x,y\",z\r\n\"\"\"q\"\"\",\"línea1\nlínea2\"\r\n", CsvWriter.generar(r));
    }

    @Test
    void neutralizaFormulasPeroRespetaTelefonosYNumeros() {
        assertEquals("'=SUM(A1)", CsvWriter.celda("=SUM(A1)"));
        assertEquals("'@usuario", CsvWriter.celda("@usuario"));
        assertEquals("'+cmd|' /C calc'!A0", CsvWriter.celda("+cmd|' /C calc'!A0"));
        assertEquals("'-cmd", CsvWriter.celda("-cmd"));
        assertEquals("'\t=x", CsvWriter.celda("\t=x"));

        assertEquals("+504 9999-8888", CsvWriter.celda("+504 9999-8888"));
        assertEquals("-5", CsvWriter.celda("-5"));
        assertEquals("(504) 9999-8888", CsvWriter.celda("(504) 9999-8888"));
        assertEquals("Don Quijote", CsvWriter.celda("Don Quijote"));
        assertEquals("", CsvWriter.celda(""));
        assertEquals("", CsvWriter.celda(null));
    }

    @Test
    void escribeUtf8ConBom(@TempDir Path carpeta) throws Exception {
        ResultadoReporte r = reporte(List.of("Libro"), List.of(List.of("Cien años de soledad")));
        Path archivo = carpeta.resolve("reporte.csv");

        CsvWriter.escribir(archivo, r);

        byte[] bytes = Files.readAllBytes(archivo);
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
        String contenido = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertEquals("Libro\r\nCien años de soledad\r\n", contenido);
    }
}