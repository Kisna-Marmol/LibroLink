package com.librolink.service;

/** Errores de la capa de servicio. Los mensajes de las subclases Validacion/Autenticacion/Autorizacion son aptos para mostrar al usuario. */
public class ServicioException extends RuntimeException {

    public ServicioException(String mensaje) { super(mensaje); }
    public ServicioException(String mensaje, Throwable causa) { super(mensaje, causa); }

    public static class Validacion extends ServicioException {
        public Validacion(String mensaje) { super(mensaje); }
    }

    public static class Autenticacion extends ServicioException {
        public Autenticacion(String mensaje) { super(mensaje); }
    }

    public static class Autorizacion extends ServicioException {
        public Autorizacion(String mensaje) { super(mensaje); }
    }

    /** Falla técnica de datos (conexión, SQL). */
    public static class Datos extends ServicioException {
        public Datos(String mensaje) { super(mensaje); }
        public Datos(String mensaje, Throwable causa) { super(mensaje, causa); }
    }
}