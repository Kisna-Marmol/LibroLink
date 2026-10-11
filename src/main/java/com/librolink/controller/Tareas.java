package com.librolink.controller;

import com.librolink.service.ServicioException;
import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Ejecuta trabajo bloqueante (BD, BCrypt) fuera del hilo de JavaFX. Los callbacks corren en el hilo de JavaFX. */
public final class Tareas {

    private static final System.Logger LOG = System.getLogger("librolink");

    private static final ExecutorService EJECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "librolink-bg");
        t.setDaemon(true);
        return t;
    });

    private Tareas() { }

    public static <T> void ejecutar(Callable<T> trabajo, Consumer<T> alExito, Consumer<Throwable> alError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return trabajo.call();
            }
        };
        task.setOnSucceeded(e -> alExito.accept(task.getValue()));
        task.setOnFailed(e -> alError.accept(task.getException()));
        EJECUTOR.execute(task);
    }

    /** Texto apto para mostrar al usuario. Los errores técnicos se registran con su causa. */
    public static String mensaje(Throwable t) {
        if (!(t instanceof ServicioException.Validacion) && !(t instanceof ServicioException.Autenticacion)) {
            LOG.log(System.Logger.Level.ERROR, "Error en tarea de fondo", t);
        }
        if (t instanceof ServicioException) {
            return t.getMessage();
        }
        return "Ocurrió un error inesperado. Intenta de nuevo.";
    }
}