package com.librolink.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigCodificacionTest {

    private static final String CONTENIDO = """
            # Copiá este archivo y completá tus valores: ñandú
            db.url=jdbc:mysql://localhost:3306/bibliolink_db
            db.user=root
            db.password=contraseña123
            """;

    @Test
    void leeUtf8(@TempDir Path carpeta) throws Exception {
        Path archivo = carpeta.resolve("db.properties");
        Files.write(archivo, CONTENIDO.getBytes(StandardCharsets.UTF_8));

        AppConfig cfg = AppConfig.cargarDesde(archivo);

        assertEquals("root", cfg.dbUser());
        assertEquals("contraseña123", cfg.dbPassword());
    }

    @Test
    void leeUtf8ConBom(@TempDir Path carpeta) throws Exception {
        Path archivo = carpeta.resolve("db.properties");
        Files.write(archivo, ("\uFEFF" + CONTENIDO).getBytes(StandardCharsets.UTF_8));

        AppConfig cfg = AppConfig.cargarDesde(archivo);

        assertEquals("jdbc:mysql://localhost:3306/bibliolink_db", cfg.dbUrl()); // sin BOM pegado a la primera clave
        assertEquals("contraseña123", cfg.dbPassword());
    }

    @Test
    void leeIso88591CuandoNoEsUtf8Valido(@TempDir Path carpeta) throws Exception {
        Path archivo = carpeta.resolve("db.properties");
        Files.write(archivo, CONTENIDO.getBytes(StandardCharsets.ISO_8859_1));

        AppConfig cfg = AppConfig.cargarDesde(archivo);

        assertEquals("root", cfg.dbUser());
        assertEquals("contraseña123", cfg.dbPassword());
    }

    @Test
    void archivoInexistenteDaUnErrorClaro(@TempDir Path carpeta) {
        assertThrows(IOException.class, () -> AppConfig.cargarDesde(carpeta.resolve("no-existe.properties")));
    }
}