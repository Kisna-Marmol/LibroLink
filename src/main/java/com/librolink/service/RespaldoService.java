package com.librolink.service;

import com.librolink.config.AppConfig;
import com.librolink.config.Database;
import com.librolink.dao.AuditoriaDao;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Respaldo de la base con mysqldump / mariadb-dump (programa externo). Solo ADMINISTRADOR.
 * La contraseña se pasa por la variable de entorno MYSQL_PWD del proceso hijo, nunca en la línea de comandos.
 */
public final class RespaldoService {

    public record ConexionDb(String host, int puerto, String base) { }

    private static final Pattern URL_JDBC =
            Pattern.compile("^jdbc:(?:mysql|mariadb)://([^:/?#]+)(?::(\\d{1,5}))?/([^?#]+).*$");
    private static final Pattern NOMBRE_BASE = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_$]*");
    private static final long TIEMPO_MAXIMO_SEGUNDOS = 180;
    private static final int MAX_SALIDA = 500;

    private static final List<String> NOMBRES_PROGRAMA =
            List.of("mysqldump.exe", "mariadb-dump.exe", "mysqldump", "mariadb-dump");
    private static final List<Path> CARPETAS_HABITUALES = List.of(
            Path.of("C:\\xampp\\mysql\\bin\\mysqldump.exe"),
            Path.of("C:\\xampp\\mysql\\bin\\mariadb-dump.exe"),
            Path.of("C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysqldump.exe"),
            Path.of("/usr/bin/mysqldump"),
            Path.of("/usr/bin/mariadb-dump"),
            Path.of("/usr/local/bin/mysqldump"));

    private final Database db;
    private final Sesion sesion;
    private final AppConfig config;
    private final AuditoriaDao auditoria = new AuditoriaDao();

    public RespaldoService(Database db, Sesion sesion, AppConfig config) {
        this.db = db;
        this.sesion = sesion;
        this.config = config;
    }

    /** Texto para la pantalla: qué programa se usará o cómo configurarlo. */
    public String estadoHerramienta() {
        sesion.requerirRol(Rol.ADMINISTRADOR);
        String configurado = config.backupDump();
        if (configurado != null && !configurado.isBlank()) {
            return localizarDump(configurado, List.of(), List.of()).isPresent()
                    ? "Se usará: " + configurado.trim()
                    : "No existe el archivo indicado en backup.dump: " + configurado.trim();
        }
        return localizarPorDefecto(null)
                .map(p -> "Se usará: " + p)
                .orElse(mensajeNoEncontrado());
    }

    /** Genera el respaldo en destino y deja constancia en auditoría. */
    public Path respaldar(Path destino) {
        Usuario actor = sesion.requerirRol(Rol.ADMINISTRADOR);
        if (destino == null) {
            throw new ServicioException.Validacion("Elige dónde guardar el respaldo.");
        }
        Path programa = localizarPorDefecto(config.backupDump())
                .orElseThrow(() -> new ServicioException.Validacion(mensajeNoEncontrado()));
        ConexionDb conexion = analizarUrl(config.dbUrl());
        long bytes = ejecutar(comandoDump(programa, conexion, config.dbUser(), destino),
                config.dbPassword(), destino);
        try {
            db.enTransaccion(cn -> {
                auditoria.registrar(cn, actor.id(), "RESPALDO", "base_de_datos", conexion.base(),
                        "{\"bytes\":" + bytes + "}");
                return null;
            });
        } catch (SQLException e) {
            throw new ServicioException.Datos(
                    "El respaldo se guardó, pero no se pudo registrar en auditoría.", e);
        }
        return destino;
    }

    // ---------- piezas verificables por separado ----------

    static ConexionDb analizarUrl(String url) {
        Matcher m = URL_JDBC.matcher(url == null ? "" : url.trim());
        if (!m.matches()) {
            throw new ServicioException.Validacion("No se pudo interpretar db.url para hacer el respaldo.");
        }
        int puerto = m.group(2) == null ? 3306 : Integer.parseInt(m.group(2));
        String base = m.group(3);
        if (puerto < 1 || puerto > 65535 || !NOMBRE_BASE.matcher(base).matches()) {
            throw new ServicioException.Validacion("db.url tiene un puerto o un nombre de base no válidos.");
        }
        return new ConexionDb(m.group(1), puerto, base);
    }

    /** Argumentos del programa (sin shell). La contraseña NO va aquí. */
    static List<String> comandoDump(Path programa, ConexionDb conexion, String usuario, Path destino) {
        return List.of(
                programa.toString(),
                "--host=" + conexion.host(),
                "--port=" + conexion.puerto(),
                "--user=" + usuario,
                "--single-transaction",
                "--default-character-set=utf8mb4",
                "--result-file=" + destino.toAbsolutePath(),
                conexion.base());
    }

    /** Busca el programa: ruta configurada, luego el PATH y por último las carpetas habituales. */
    static Optional<Path> localizarDump(String configurado, List<String> directoriosPath, List<Path> candidatos) {
        if (configurado != null && !configurado.isBlank()) {
            try {
                Path p = Path.of(configurado.trim());
                return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
            } catch (InvalidPathException e) {
                return Optional.empty();
            }
        }
        for (String directorio : directoriosPath) {
            for (String nombre : NOMBRES_PROGRAMA) {
                try {
                    Path p = Path.of(directorio, nombre);
                    if (Files.isRegularFile(p)) {
                        return Optional.of(p);
                    }
                } catch (InvalidPathException ignorada) {
                    // una entrada rara del PATH no debe impedir seguir buscando
                }
            }
        }
        for (Path p : candidatos) {
            if (Files.isRegularFile(p)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    static Optional<Path> localizarPorDefecto(String configurado) {
        String path = System.getenv("PATH");
        List<String> directorios = path == null ? List.of() : Arrays.asList(path.split(File.pathSeparator));
        return localizarDump(configurado, directorios, CARPETAS_HABITUALES);
    }

    /** Ejecuta el programa y devuelve el tamaño del archivo generado. Si falla, borra el archivo parcial. */
    static long ejecutar(List<String> comando, String password, Path destino) {
        Path registro = null;
        try {
            registro = Files.createTempFile("librolink-respaldo", ".log");
            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.redirectErrorStream(true);
            pb.redirectOutput(registro.toFile());
            if (password != null && !password.isEmpty()) {
                pb.environment().put("MYSQL_PWD", password);
            }
            Process proceso = pb.start();
            if (!proceso.waitFor(TIEMPO_MAXIMO_SEGUNDOS, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();
                Files.deleteIfExists(destino);
                throw new ServicioException.Datos("El respaldo tardó demasiado y se canceló.");
            }
            int codigo = proceso.exitValue();
            if (codigo != 0) {
                Files.deleteIfExists(destino);
                throw new ServicioException.Datos("El respaldo falló (código " + codigo + "): " + resumen(registro));
            }
            if (!Files.isRegularFile(destino) || Files.size(destino) == 0) {
                throw new ServicioException.Datos("El respaldo terminó sin generar el archivo.");
            }
            return Files.size(destino);
        } catch (IOException e) {
            throw new ServicioException.Datos("No se pudo ejecutar el respaldo: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServicioException.Datos("El respaldo fue interrumpido.", e);
        } finally {
            if (registro != null) {
                try {
                    Files.deleteIfExists(registro);
                } catch (IOException ignorada) {
                    // el archivo temporal no es crítico
                }
            }
        }
    }

    private static String resumen(Path registro) throws IOException {
        String texto = new String(Files.readAllBytes(registro), Charset.defaultCharset()).trim();
        return texto.length() > MAX_SALIDA ? texto.substring(0, MAX_SALIDA) + "…" : texto;
    }

    private static String mensajeNoEncontrado() {
        return "No se encontró mysqldump ni mariadb-dump. Agrega la línea "
                + "backup.dump=C:/xampp/mysql/bin/mysqldump.exe (ajusta la ruta) en config/db.properties "
                + "y reinicia la aplicación.";
    }
}