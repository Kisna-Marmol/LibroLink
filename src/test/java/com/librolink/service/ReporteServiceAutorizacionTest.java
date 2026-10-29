package com.librolink.service;

import com.librolink.model.EstadoUsuario;
import com.librolink.model.ResultadoReporte;
import com.librolink.model.Rol;
import com.librolink.model.TipoReporte;
import com.librolink.model.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** La autorización y la validación ocurren antes de tocar la BD, por eso basta pasar null como Database. */
class ReporteServiceAutorizacionTest {

    private static Sesion sesion(Rol rol) {
        Sesion s = new Sesion();
        s.iniciar(new Usuario(1L, rol, null, "Ana", "Pérez", "ana@correo.com", null, EstadoUsuario.ACTIVO));
        return s;
    }

    private static ResultadoReporte reporteConDatos() {
        return new ResultadoReporte(TipoReporte.ATRASOS, "Atrasos", List.of("A"), List.of(List.of("x")), false);
    }

    @Test
    void lectorYSinSesionNoPuedenOperar(@TempDir Path carpeta) {
        LocalDate hoy = LocalDate.now();
        for (Sesion s : new Sesion[]{sesion(Rol.LECTOR), new Sesion()}) {
            ReporteService svc = new ReporteService(null, s);

            assertThrows(ServicioException.Autorizacion.class, svc::atrasos);
            assertThrows(ServicioException.Autorizacion.class, () -> svc.historialDeLector(1L));
            assertThrows(ServicioException.Autorizacion.class, () -> svc.inventarioPorEjemplar(null));
            assertThrows(ServicioException.Autorizacion.class, svc::inventarioPorTitulo);
            assertThrows(ServicioException.Autorizacion.class,
                    () -> svc.prestamosPorRango(hoy.minusDays(5), hoy));
            assertThrows(ServicioException.Autorizacion.class, () -> svc.librosMasSolicitados(null, null, 10));
            assertThrows(ServicioException.Autorizacion.class,
                    () -> svc.exportarCsv(reporteConDatos(), carpeta.resolve("x.csv")));
        }
    }

    @Test
    void seValidanLosParametrosAntesDeTocarLaBd(@TempDir Path carpeta) {
        ReporteService svc = new ReporteService(null, sesion(Rol.BIBLIOTECARIO));
        LocalDate hoy = LocalDate.now();

        assertThrows(ServicioException.Validacion.class, () -> svc.historialDeLector(null));

        assertThrows(ServicioException.Validacion.class, () -> svc.prestamosPorRango(null, hoy));
        assertThrows(ServicioException.Validacion.class, () -> svc.prestamosPorRango(hoy, null));
        assertThrows(ServicioException.Validacion.class, () -> svc.prestamosPorRango(hoy, hoy.minusDays(1)));
        assertThrows(ServicioException.Validacion.class,
                () -> svc.prestamosPorRango(hoy.minusYears(11), hoy));                           // más de 10 años

        assertThrows(ServicioException.Validacion.class, () -> svc.librosMasSolicitados(null, null, 0));
        assertThrows(ServicioException.Validacion.class, () -> svc.librosMasSolicitados(null, null, 101));
        assertThrows(ServicioException.Validacion.class,
                () -> svc.librosMasSolicitados(hoy, hoy.minusDays(1), 10));

        Path destino = carpeta.resolve("x.csv");
        assertThrows(ServicioException.Validacion.class, () -> svc.exportarCsv(null, destino));
        assertThrows(ServicioException.Validacion.class,
                () -> svc.exportarCsv(new ResultadoReporte(TipoReporte.ATRASOS, "Vacío", List.of("A"),
                        List.of(), false), destino));
    }
}
