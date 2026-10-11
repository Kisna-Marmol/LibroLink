package com.librolink.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Configuración local de la aplicación (conexión a MySQL). Sin valores por defecto sensibles. */
public final class AppConfig {

    public static final String ENV_CONFIG_PATH = "LIBROLINK_CONFIG";
    public static final Path DEFAULT_PATH = Path.of("config", "db.properties");

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    private AppConfig(String dbUrl, String dbUser, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public static AppConfig load() throws IOException {
        String env = System.getenv(ENV_CONFIG_PATH);
        Path path = (env != null && !env.isBlank()) ? Path.of(env) : DEFAULT_PATH;
        if (!Files.isRegularFile(path)) {
            throw new IOException("No se encontró el archivo de configuración: "
                    + path.toAbsolutePath()
                    + ". Copiá config/db.properties.example a config/db.properties.");
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        }
        return fromProperties(props);
    }

    public static AppConfig fromProperties(Properties props) {
        return new AppConfig(
                require(props, "db.url"),
                require(props, "db.user"),
                props.getProperty("db.password", ""));
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

    @Override
    public String toString() {
        return "AppConfig[dbUrl=" + dbUrl + ", dbUser=" + dbUser + ", dbPassword=***]";
    }
}