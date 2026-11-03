package com.librolink.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppConfigUbicacionesTest {

    @Test
    void ubicacionesEnOrdenDePrioridad() {
        List<Path> u = AppConfig.ubicaciones("C:\\Users\\ana\\AppData\\Roaming", "C:\\Users\\ana");

        assertEquals(3, u.size());
        assertEquals(Path.of("config", "db.properties"), u.get(0));
        assertEquals(Path.of("C:\\Users\\ana\\AppData\\Roaming", "LibroLink", "db.properties"), u.get(1));
        assertEquals(Path.of("C:\\Users\\ana", ".librolink", "db.properties"), u.get(2));
    }

    @Test
    void omiteLasVariablesAusentesOVacias() {
        assertEquals(List.of(Path.of("config", "db.properties")), AppConfig.ubicaciones(null, " "));
        assertEquals(2, AppConfig.ubicaciones("", "/home/ana").size());
    }
}