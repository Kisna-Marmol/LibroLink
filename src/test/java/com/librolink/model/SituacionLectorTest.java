package com.librolink.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SituacionLectorTest {

    @Test
    void motivosDeBloqueo() {
        assertNull(new SituacionLector(2, 0, 3, false).motivoBloqueo());

        String limite = new SituacionLector(3, 0, 3, false).motivoBloqueo();
        assertNotNull(limite);
        assertTrue(limite.contains("máximo 3"));

        String atrasos = new SituacionLector(1, 1, 3, false).motivoBloqueo();
        assertNotNull(atrasos);
        assertTrue(atrasos.contains("atrasado"));

        assertNull(new SituacionLector(1, 2, 3, true).motivoBloqueo()); // atrasos permitidos por configuración
        assertNotNull(new SituacionLector(3, 2, 3, true).motivoBloqueo()); // el límite se aplica siempre
    }
}