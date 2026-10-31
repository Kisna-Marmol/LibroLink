package com.librolink.service;

import com.librolink.config.AppConfig;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class RespaldoServiceTest {

    private static AppConfig config() {
        Properties p = new Properties();
        p.setProperty("db.url", "jdbc:mysql://localhost:3306/bibliolink_db?serverTimezone=America/Tegucigalpa");
        p.setProperty("db.user", "root");
        p.setProperty("db.password", "secreto-123");
        return AppConfig.fromProperties(p);
    }

    private static Sesion sesion(Rol rol) {
        Sesion s = new Sesion();
        s.iniciar(new Usuario(1L, rol, null, "Ana", "Pérez", "ana@correo.com", null, EstadoUsuario.ACTIVO));
        return s;
    }

    @Test
    void analizaLaUrlJdbc() {
        RespaldoService.ConexionDb c = RespaldoService.analizarUrl(
                "jdbc:mysql://localhost:3306/bibliolink_db?serverTimezone=America/Tegucigalpa&characterEncoding=UTF-8");
        assertEquals("localhost", c.host());
        assertEquals(3306, c.puerto());
        assertEquals("bibliolink_db", c.base());

        RespaldoService.ConexionDb sinPuerto = RespaldoService.analizarUrl("jdbc:mariadb://10.0.0.5/mi_base");
        assertEquals("10.0.0.5", sinPuerto.host());
        assertEquals(3306, sinPuerto.puerto());
        assertEquals("mi_base", sinPuerto.base());

        for (String mala : new String[]{"", "http://x/y", "jdbc:mysql://host:abc/db", "jdbc:mysql://host/-db",
                "jdbc:mysql://host/db name", "jdbc:mysql://host:99999/db"}) {
            assertThrows(ServicioException.Validacion.class, () -> RespaldoService.analizarUrl(mala), mala);
        }
        assertThrows(ServicioException.Validacion.class, () -> RespaldoService.analizarUrl(null));
    }

    @Test
    void elComandoNoLlevaLaContrasena() {
        List<String> comando = RespaldoService.comandoDump(
                Path.of("C:/xampp/mysql/bin/mysqldump.exe"),
                new RespaldoService.ConexionDb("localhost", 3306, "bibliolink_db"),
                "root", Path.of("salida.sql"));

        assertTrue(comando.get(0).endsWith("mysqldump.exe"));
        assertTrue(comando.contains("--host=localhost"));
        assertTrue(comando.contains("--port=3306"));
        assertTrue(comando.contains("--user=root"));
        assertTrue(comando.contains("--single-transaction"));
        assertTrue(comando.stream().anyMatch(a -> a.startsWith("--result-file=")));
        assertEquals("bibliolink_db", comando.get(comando.size() - 1));
        assertTrue(comando.stream().noneMatch(a -> a.contains("secreto") || a.toLowerCase().contains("password")));
    }

    @Test
    void localizaElProgramaDeRespaldo(@TempDir Path carpeta) throws Exception {
        Path programa = Files.createFile(carpeta.resolve("mariadb-dump.exe"));

        // en el PATH
        assertEquals(programa, RespaldoService.localizarDump(null, List.of(carpeta.toString()), List.of()).orElseThrow());
        // en las carpetas habituales
        assertEquals(programa, RespaldoService.localizarDump(null, List.of(), List.of(programa)).orElseThrow());
        // ruta configurada: manda sobre todo lo demás
        assertEquals(programa, RespaldoService.localizarDump(programa.toString(), List.of(), List.of()).orElseThrow());
        // ruta configurada que no existe: no se usa otra en silencio
        assertTrue(RespaldoService.localizarDump(carpeta.resolve("nada.exe").toString(),
                List.of(carpeta.toString()), List.of(programa)).isEmpty());
        // nada que encontrar
        assertTrue(RespaldoService.localizarDump(null, List.of(), List.of(carpeta.resolve("nada"))).isEmpty());
    }

    @Test
    void soloElAdministradorRespalda(@TempDir Path carpeta) {
        Path destino = carpeta.resolve("respaldo.sql");
        for (Sesion s : new Sesion[]{sesion(Rol.BIBLIOTECARIO), sesion(Rol.LECTOR), new Sesion()}) {
            RespaldoService svc = new RespaldoService(null, s, config());
            assertThrows(ServicioException.Autorizacion.class, () -> svc.respaldar(destino));
            assertThrows(ServicioException.Autorizacion.class, svc::estadoHerramienta);
        }
        RespaldoService admin = new RespaldoService(null, sesion(Rol.ADMINISTRADOR), config());
        assertThrows(ServicioException.Validacion.class, () -> admin.respaldar(null));
    }
}