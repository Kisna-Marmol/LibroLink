package com.librolink.model;

import java.time.LocalDateTime;
import java.util.List;

public record ResumenPanel(long titulosActivos, long ejemplaresDisponibles,
                           long prestamosActivos, long prestamosAtrasados,
                           List<Actividad> actividadReciente) {

    /** usuario es null cuando la acción no tiene usuario asociado (p. ej. login con correo inexistente). */
    public record Actividad(LocalDateTime fecha, String accion, String entidad, String usuario) { }
}