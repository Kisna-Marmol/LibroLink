package com.librolink.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Genera contraseñas temporales sin caracteres ambiguos (0/O, 1/l/I). Siempre incluye letra y número. */
public final class GeneradorClaves {

    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String DIGITOS = "23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private GeneradorClaves() { }

    public static String generar(int longitud) {
        if (longitud < Validaciones.PASSWORD_MIN) {
            throw new IllegalArgumentException("La longitud mínima es " + Validaciones.PASSWORD_MIN);
        }
        String todo = LETRAS + DIGITOS;
        List<Character> caracteres = new ArrayList<>();
        caracteres.add(LETRAS.charAt(ALEATORIO.nextInt(LETRAS.length())));
        caracteres.add(DIGITOS.charAt(ALEATORIO.nextInt(DIGITOS.length())));
        while (caracteres.size() < longitud) {
            caracteres.add(todo.charAt(ALEATORIO.nextInt(todo.length())));
        }
        Collections.shuffle(caracteres, ALEATORIO);
        StringBuilder sb = new StringBuilder(longitud);
        for (char c : caracteres) {
            sb.append(c);
        }
        return sb.toString();
    }
}
