package com.librolink.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;


public final class Validaciones {

    public static final int PASSWORD_MIN = 10;
    /** BCrypt solo procesa los primeros 72 bytes; se rechaza más en vez de truncar en silencio. */
    public static final int PASSWORD_MAX_BYTES = 72;

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    private Validaciones() { }

    /** Devuelve el email recortado y en minúsculas. */
    public static String email(String valor) {
        String v = valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) {
            throw new ServicioException.Validacion("El correo es obligatorio.");
        }
        if (v.length() > 190 || !EMAIL.matcher(v).matches()) {
            throw new ServicioException.Validacion("El correo no tiene un formato válido.");
        }
        return v;
    }

    public static String textoObligatorio(String valor, String campo, int maxLongitud) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty()) {
            throw new ServicioException.Validacion(campo + " es obligatorio.");
        }
        if (v.length() > maxLongitud) {
            throw new ServicioException.Validacion(
                    campo + " no puede superar " + maxLongitud + " caracteres.");
        }
        return v;
    }

    public static void password(char[] pw) {
        if (pw == null || pw.length < PASSWORD_MIN) {
            throw new ServicioException.Validacion(
                    "La contraseña debe tener al menos " + PASSWORD_MIN + " caracteres.");
        }
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(pw));
        if (bytes.remaining() > PASSWORD_MAX_BYTES) {
            throw new ServicioException.Validacion(
                    "La contraseña es demasiado larga (máximo " + PASSWORD_MAX_BYTES + " bytes).");
        }
        boolean letra = false;
        boolean digito = false;
        for (char c : pw) {
            letra |= Character.isLetter(c);
            digito |= Character.isDigit(c);
        }
        if (!letra || !digito) {
            throw new ServicioException.Validacion("La contraseña debe incluir letras y números.");
        }
    }

    /** Devuelve null si el valor está vacío. */
    public static String textoOpcional(String valor, String campo, int maxLongitud) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty()) {
            return null;
        }
        if (v.length() > maxLongitud) {
            throw new ServicioException.Validacion(
                    campo + " no puede superar " + maxLongitud + " caracteres.");
        }
        return v;
    }

    /** URL opcional: solo http o https con host. */
    public static String urlOpcional(String valor, String campo) {
        String v = textoOpcional(valor, campo, 255);
        if (v == null) {
            return null;
        }
        try {
            URI uri = new URI(v);
            String esquema = uri.getScheme();
            boolean valida = esquema != null
                    && (esquema.equalsIgnoreCase("http") || esquema.equalsIgnoreCase("https"))
                    && uri.getHost() != null;
            if (!valida) {
                throw new URISyntaxException(v, "URL no válida");
            }
            return v;
        } catch (URISyntaxException e) {
            throw new ServicioException.Validacion(
                    campo + " debe ser una URL que empiece con http:// o https://.");
        }
    }
}