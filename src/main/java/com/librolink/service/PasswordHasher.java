package com.librolink.service;

import at.favre.lib.crypto.bcrypt.BCrypt;

public final class PasswordHasher {

    private static final int COSTO = 12;

    public String hash(char[] password) {
        return BCrypt.withDefaults().hashToString(COSTO, password);
    }

    public boolean verificar(char[] password, String hash) {
        try {
            return BCrypt.verifyer().verify(password, hash).verified;
        } catch (IllegalArgumentException e) {
            return false; // hash malformado o contraseña fuera de rango
        }
    }
}