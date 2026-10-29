package com.librolink.model;

/** texto es opcional (null = sin filtro). */
public record FiltroPrestamos(String texto, Estado estado) {

    public enum Estado {
        TODOS("Todos los préstamos"),
        ACTIVOS("Activos"),
        ATRASADOS("Atrasados"),
        FINALIZADOS("Finalizados"),
        CANCELADOS("Cancelados");

        private final String etiqueta;

        Estado(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }
}