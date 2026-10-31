package com.librolink.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Parámetros de circulación leídos de la tabla configuracion. Un valor ausente o inválido usa el valor inicial. */
public record PoliticaPrestamo(int diasPrestamo, int maxEjemplaresPorPrestamo, int maxPrestamosActivos,
                               boolean permitirPrestamoConAtrasos, int diasAvisoVencimiento) {

    public static final String CLAVE_DIAS_PRESTAMO = "dias_prestamo";
    public static final String CLAVE_MAX_EJEMPLARES = "max_ejemplares_por_prestamo";
    public static final String CLAVE_MAX_PRESTAMOS_ACTIVOS = "max_prestamos_activos";
    public static final String CLAVE_PERMITIR_CON_ATRASOS = "permitir_prestamo_con_atrasos";
    public static final String CLAVE_DIAS_AVISO = "dias_aviso_vencimiento";

    public static final int DIAS_PRESTAMO_MIN = 1;
    public static final int DIAS_PRESTAMO_MAX = 365;
    public static final int MAX_EJEMPLARES_MIN = 1;
    public static final int MAX_EJEMPLARES_MAX = 20;
    public static final int MAX_PRESTAMOS_ACTIVOS_MIN = 1;
    public static final int MAX_PRESTAMOS_ACTIVOS_MAX = 50;
    public static final int DIAS_AVISO_MIN = 0;
    public static final int DIAS_AVISO_MAX = 30;

    public static PoliticaPrestamo porDefecto() {
        return new PoliticaPrestamo(14, 3, 3, false, 2);
    }

    public static PoliticaPrestamo desde(Map<String, String> valores) {
        String conAtrasos = valores.get(CLAVE_PERMITIR_CON_ATRASOS);
        return new PoliticaPrestamo(
                entero(valores, CLAVE_DIAS_PRESTAMO, 14, DIAS_PRESTAMO_MIN, DIAS_PRESTAMO_MAX),
                entero(valores, CLAVE_MAX_EJEMPLARES, 3, MAX_EJEMPLARES_MIN, MAX_EJEMPLARES_MAX),
                entero(valores, CLAVE_MAX_PRESTAMOS_ACTIVOS, 3, MAX_PRESTAMOS_ACTIVOS_MIN, MAX_PRESTAMOS_ACTIVOS_MAX),
                conAtrasos != null && conAtrasos.trim().equalsIgnoreCase("true"),
                entero(valores, CLAVE_DIAS_AVISO, 2, DIAS_AVISO_MIN, DIAS_AVISO_MAX));
    }

    /** Valores tal como se guardan en la tabla, en un orden estable. */
    public Map<String, String> valoresPorClave() {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put(CLAVE_DIAS_PRESTAMO, String.valueOf(diasPrestamo));
        valores.put(CLAVE_MAX_EJEMPLARES, String.valueOf(maxEjemplaresPorPrestamo));
        valores.put(CLAVE_MAX_PRESTAMOS_ACTIVOS, String.valueOf(maxPrestamosActivos));
        valores.put(CLAVE_PERMITIR_CON_ATRASOS, String.valueOf(permitirPrestamoConAtrasos));
        valores.put(CLAVE_DIAS_AVISO, String.valueOf(diasAvisoVencimiento));
        return valores;
    }

    private static int entero(Map<String, String> valores, String clave, int porDefecto, int minimo, int maximo) {
        String texto = valores.get(clave);
        if (texto == null) {
            return porDefecto;
        }
        try {
            int n = Integer.parseInt(texto.trim());
            return n >= minimo && n <= maximo ? n : porDefecto;
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }
}