package com.librolink.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashVerificaYNoContieneTextoPlano() {
        char[] pw = "Biblioteca2026".toCharArray();
        String hash = hasher.hash(pw);

        assertTrue(hash.startsWith("$2"));
        assertFalse(hash.contains("Biblioteca2026"));
        assertTrue(hasher.verificar(pw, hash));
        assertFalse(hasher.verificar("OtraClave2026".toCharArray(), hash));
    }

    @Test
    void hashMalformadoNoLanzaExcepcion() {
        assertFalse(hasher.verificar("x".toCharArray(), "no-es-un-hash"));
    }
}