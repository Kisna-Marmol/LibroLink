package com.librolink.service;

import com.librolink.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Ejecuta el volcado real (solo lectura sobre la base). Se omite si falta la configuración o el programa. */
class RespaldoDumpIntegracionTest {

    @Test
    void generaUnArchivoSqlConLasTablas(@TempDir Path carpeta) throws Exception {
        assumeTrue(Files.isRegularFile(AppConfig.DEFAULT_PATH),
                "Sin config/db.properties: prueba de integración omitida");
        AppConfig cfg = AppConfig.load();
        Optional<Path> programa = RespaldoService.localizarPorDefecto(cfg.backupDump());
        assumeTrue(programa.isPresent(), "mysqldump/mariadb-dump no encontrado: prueba omitida");

        Path destino = carpeta.resolve("respaldo.sql");
        long bytes = RespaldoService.ejecutar(
                RespaldoService.comandoDump(programa.get(), RespaldoService.analizarUrl(cfg.dbUrl()),
                        cfg.dbUser(), destino),
                cfg.dbPassword(), destino);

        assertTrue(bytes > 0);
        String sql = Files.readString(destino, StandardCharsets.UTF_8);
        assertTrue(sql.contains("CREATE TABLE"));
        assertTrue(sql.contains("usuarios"));
        assertTrue(sql.contains("auditoria"));
    }
}