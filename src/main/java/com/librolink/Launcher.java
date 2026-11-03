package com.librolink;

/**
 * Punto de entrada del JAR ejecutable. JavaFX no arranca desde una clase que extiende Application
 * cuando va en el classpath (JAR con dependencias), por eso se entra por esta clase.
 */
public final class Launcher {

    private Launcher() { }

    public static void main(String[] args) {
        Main.main(args);
    }
}