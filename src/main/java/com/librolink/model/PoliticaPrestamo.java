package com.librolink.model;

import java.util.Map;

/** Parámetros de circulación leídos de la tabla configuracion. Un valor ausente o inválido usa el valor inicial. */
public record PoliticaPrestamo(int diasPrestamo, int maxEjemplaresPorPrestamo, int maxPrestamosActivos,
                               boolean permitirPrestamoConAtrasos, int diasAvisoVencimiento) {

    public static PoliticaPrestamo desde(Map<String, String> valores) {
        String conAtrasos = valores.get("permitir_prestamo_con_atrasos");
        return new PoliticaPrestamo(
                entero(valores, "dias_prestamo", 14, 1, 365),
                entero(valores, "max_ejemplares_por_prestamo", 3, 1, 20),
                entero(valores, "max_prestamos_activos", 3, 1, 50),
                conAtrasos != null && conAtrasos.trim().equalsIgnoreCase("true"),
                entero(valores, "dias_aviso_vencimiento", 2, 0, 30));
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