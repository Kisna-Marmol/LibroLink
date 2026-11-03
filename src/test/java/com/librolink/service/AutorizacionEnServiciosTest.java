package com.librolink.service;

import com.librolink.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba guardián: sin sesión iniciada, ningún método público de los servicios puede responder.
 * Si agregas un método nuevo a un servicio y olvidas el chequeo de rol, esta prueba falla y lo nombra.
 * (AuthService queda fuera: es el servicio de ingreso. Los métodos de la lista son solo de presentación.)
 */
class AutorizacionEnServiciosTest {

    private static final Set<String> SIN_AUTORIZACION = Set.of("limiteResultados", "limiteFilas", "puedeGestionar");

    private static Object valorPorDefecto(Class<?> tipo) {
        if (tipo == int.class) return 0;
        if (tipo == long.class) return 0L;
        if (tipo == boolean.class) return false;
        if (tipo == double.class) return 0d;
        if (tipo == float.class) return 0f;
        if (tipo == short.class) return (short) 0;
        if (tipo == byte.class) return (byte) 0;
        if (tipo == char.class) return '\0';
        return null;
    }

    @Test
    void todoMetodoPublicoDeLosServiciosExigeSesion() throws Exception {
        Sesion sinSesion = new Sesion();
        Properties p = new Properties();
        p.setProperty("db.url", "jdbc:mysql://localhost:3306/bibliolink_db");
        p.setProperty("db.user", "root");
        AppConfig config = AppConfig.fromProperties(p);

        List<Object> servicios = List.of(
                new AutorService(null, sinSesion),
                new CategoriaService(null, sinSesion),
                new EditorialService(null, sinSesion),
                new LibroService(null, sinSesion),
                new EjemplarService(null, sinSesion),
                new UsuarioService(null, sinSesion),
                new HistorialService(null, sinSesion),
                new PrestamoService(null, sinSesion),
                new ReporteService(null, sinSesion),
                new ConfiguracionService(null, sinSesion),
                new AuditoriaService(null, sinSesion),
                new DashboardService(null, sinSesion),
                new RespaldoService(null, sinSesion, config));

        List<String> sinProteccion = new ArrayList<>();
        int revisados = 0;
        for (Object servicio : servicios) {
            for (Method m : servicio.getClass().getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers())
                        || m.isSynthetic() || SIN_AUTORIZACION.contains(m.getName())) {
                    continue;
                }
                String nombre = servicio.getClass().getSimpleName() + "." + m.getName();
                Object[] args = Arrays.stream(m.getParameterTypes())
                        .map(AutorizacionEnServiciosTest::valorPorDefecto)
                        .toArray();
                revisados++;
                try {
                    m.invoke(servicio, args);
                    sinProteccion.add(nombre + " respondió sin sesión");
                } catch (InvocationTargetException e) {
                    if (!(e.getCause() instanceof ServicioException.Autorizacion)) {
                        sinProteccion.add(nombre + " lanzó " + e.getCause());
                    }
                }
            }
        }

        assertTrue(revisados > 40, "Se esperaba revisar más de 40 métodos y fueron " + revisados);
        assertEquals(List.of(), sinProteccion, "Métodos de servicio sin chequeo de autorización");
    }
}