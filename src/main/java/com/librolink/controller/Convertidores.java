package com.librolink.controller;

import javafx.util.StringConverter;

import java.util.function.Function;

final class Convertidores {

    private Convertidores() { }

    /** Convertidor de solo lectura para ComboBox: muestra el texto que devuelve la función. */
    static <T> StringConverter<T> de(Function<T, String> texto) {
        return new StringConverter<>() {
            @Override
            public String toString(T t) {
                return t == null ? "" : texto.apply(t);
            }

            @Override
            public T fromString(String s) {
                return null;
            }
        };
    }
}