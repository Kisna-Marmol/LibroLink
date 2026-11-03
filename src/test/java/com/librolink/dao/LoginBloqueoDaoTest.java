package com.librolink.dao;

import com.librolink.config.AppConfig;
import com.librolink.config.Database;
import com.librolink.model.DatosUsuario;
import com.librolink.model.EstadoUsuario;
import com.librolink.model.Rol;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integración: usa una transacción y hace rollback, así que no deja registros en la base. */
class LoginBloqueoDaoTest {

    private static AppConfig config;

    @BeforeAll
    static void cargar() throws IOException {
        assumeTrue(Files.isRegularFile(AppConfig.DEFAULT_PATH),
                "Sin config/db.properties: prueba de integración omitida");
        config = AppConfig.load();
    }

    @Test
    void cuentaSoloLosFallosRecientesDesdeElUltimoIngreso() throws Exception {
        try (Database db = new Database(config); Connection cn = db.getConnection()) {
            cn.setAutoCommit(false);
            try {
                String clave = "prueba" + System.nanoTime();
                UsuarioDao usuarios = new UsuarioDao();
                long rol = usuarios.idRol(cn, Rol.LECTOR);
                long id = usuarios.insertarCompleto(cn, rol, new DatosUsuario(Rol.LECTOR, "T" + System.nanoTime(),
                        "Ana" + clave, "Prueba", clave + "@test.local", null, EstadoUsuario.ACTIVO), "h");
                long otro = usuarios.insertarCompleto(cn, rol, new DatosUsuario(Rol.LECTOR, "U" + System.nanoTime(),
                        "Beto" + clave, "Prueba", clave + "b@test.local", null, EstadoUsuario.ACTIVO), "h");

                AuditoriaDao auditoria = new AuditoriaDao();
                for (int i = 0; i < 5; i++) {
                    auditoria.registrar(cn, id, "LOGIN_FALLIDO", "usuarios", String.valueOf(id), null);
                }
                auditoria.registrar(cn, otro, "LOGIN_FALLIDO", "usuarios", String.valueOf(otro), null);

                assertEquals(5, auditoria.contarLoginsFallidosRecientes(cn, id, 15));
                assertEquals(1, auditoria.contarLoginsFallidosRecientes(cn, otro, 15));

                // un intento bloqueado queda registrado pero no amplía el bloqueo
                auditoria.registrar(cn, id, "LOGIN_BLOQUEADO", "usuarios", String.valueOf(id), null);
                assertEquals(5, auditoria.contarLoginsFallidosRecientes(cn, id, 15));

                // un ingreso correcto reinicia la cuenta
                auditoria.registrar(cn, id, "LOGIN", "usuarios", String.valueOf(id), null);
                assertEquals(0, auditoria.contarLoginsFallidosRecientes(cn, id, 15));

                // y los fallos posteriores vuelven a contar desde cero
                auditoria.registrar(cn, id, "LOGIN_FALLIDO", "usuarios", String.valueOf(id), null);
                auditoria.registrar(cn, id, "LOGIN_FALLIDO", "usuarios", String.valueOf(id), null);
                assertEquals(2, auditoria.contarLoginsFallidosRecientes(cn, id, 15));
            } finally {
                cn.rollback();
            }
        }
    }
}