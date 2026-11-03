package com.librolink.config;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/** Configuración local de la aplicación (conexión a la base de datos). Sin valores por defecto sensibles. */
public final class AppConfig {

    public static final String ENV_CONFIG_PATH = "LIBROLINK_CONFIG";
    public static final Path DEFAULT_PATH = Path.of("config", "db.properties");

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final String backupDump;

    private AppConfig(String dbUrl, String dbUser, String dbPassword, String backupDump) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.backupDump = backupDump;
    }

    /**
     * Si LIBROLINK_CONFIG está definida, se usa exclusivamente ese archivo. Si no, se busca en
     * config/db.properties (carpeta de trabajo), luego en %APPDATA%\LibroLink y por último en ~/.librolink.
     */
    public static AppConfig load() throws IOException {
        String env = System.getenv(ENV_CONFIG_PATH);
        if (env != null && !env.isBlank()) {
            return cargarDesde(Path.of(env.trim()));
        }
        List<Path> candidatas = ubicaciones(System.getenv("APPDATA"), System.getProperty("user.home"));
        for (Path p : candidatas) {
            if (Files.isRegularFile(p)) {
                return cargarDesde(p);
            }
        }
        throw new IOException("No se encontró el archivo de configuración. Se buscó en:\n"
                + candidatas.stream().map(p -> " - " + p.toAbsolutePath()).collect(Collectors.joining("\n"))
                + "\nCopiá config/db.properties.example a alguna de esas ubicaciones y completá tus datos.");
    }

    /** Ubicaciones donde se busca el archivo, en orden de prioridad. */
    static List<Path> ubicaciones(String appData, String userHome) {
        List<Path> lista = new ArrayList<>();
        lista.add(DEFAULT_PATH);
        if (appData != null && !appData.isBlank()) {
            lista.add(Path.of(appData.trim(), "LibroLink", "db.properties"));
        }
        if (userHome != null && !userHome.isBlank()) {
            lista.add(Path.of(userHome.trim(), ".librolink", "db.properties"));
        }
        return lista;
    }

    static AppConfig cargarDesde(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("No se encontró el archivo de configuración: " + path.toAbsolutePath()
                    + ". Copiá config/db.properties.example a esa ubicación.");
        }
        String text = decodificar(Files.readAllBytes(path));
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1); // quitar BOM
        }
        Properties props = new Properties();
        props.load(new StringReader(text));
        return fromProperties(props);
    }

    /** UTF-8 si el archivo es UTF-8 válido; si no (p. ej. guardado como ISO-8859-1 por un editor de Windows), ISO-8859-1. */
    static String decodificar(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    public static AppConfig fromProperties(Properties props) {
        String dump = props.getProperty("backup.dump");
        return new AppConfig(
                require(props, "db.url"),
                require(props, "db.user"),
                props.getProperty("db.password", ""),
                dump == null || dump.isBlank() ? null : dump.trim());
    }

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta la propiedad obligatoria: " + key);
        }
        return value.trim();
    }

    public String dbUrl() { return dbUrl; }
    public String dbUser() { return dbUser; }
    public String dbPassword() { return dbPassword; }

    /** Ruta opcional de mysqldump/mariadb-dump (null si no se configuró). */
    public String backupDump() { return backupDump; }

    @Override
    public String toString() {
        return "AppConfig[dbUrl=" + dbUrl + ", dbUser=" + dbUser + ", dbPassword=***]";
    }
}