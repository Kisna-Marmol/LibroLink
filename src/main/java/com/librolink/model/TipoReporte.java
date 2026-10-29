package com.librolink.model;

public enum TipoReporte {
    ATRASOS("atrasos", "Préstamos atrasados"),
    HISTORIAL_LECTOR("historial_lector", "Historial por lector"),
    INVENTARIO_EJEMPLARES("inventario_ejemplares", "Inventario por ejemplar"),
    INVENTARIO_TITULOS("inventario_titulos", "Inventario resumido por título"),
    PRESTAMOS_RANGO("prestamos_rango", "Préstamos por rango de fechas"),
    LIBROS_SOLICITADOS("libros_solicitados", "Libros más solicitados");

    private final String codigo;
    private final String etiqueta;

    TipoReporte(String codigo, String etiqueta) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
    }

    /** Identificador estable, apto para nombres de archivo y para la auditoría. */
    public String codigo() {
        return codigo;
    }

    public String etiqueta() {
        return etiqueta;
    }
}