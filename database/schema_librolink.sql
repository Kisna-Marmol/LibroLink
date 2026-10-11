-- ============================================================
-- LibroLink - Esquema inicial de base de datos
-- MySQL 8.0+ | InnoDB | utf8mb4
-- Ejecutar en una instancia local de MySQL.
-- ============================================================

CREATE DATABASE IF NOT EXISTS bibliolink_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE bibliolink_db;

SET NAMES utf8mb4;

-- Para una instalación nueva, ejecutar este script una sola vez.
-- No contiene DROP TABLE para evitar borrar datos existentes.

CREATE TABLE IF NOT EXISTS roles (
    id_rol BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(30) NOT NULL,
    descripcion VARCHAR(255) NULL,
    PRIMARY KEY (id_rol),
    CONSTRAINT uq_roles_nombre UNIQUE (nombre)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS usuarios (
    id_usuario BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_rol BIGINT UNSIGNED NOT NULL,
    numero_socio VARCHAR(30) NULL,
    nombre VARCHAR(80) NOT NULL,
    apellido VARCHAR(80) NOT NULL,
    email VARCHAR(190) NOT NULL,
    telefono VARCHAR(30) NULL,
    password_hash VARCHAR(255) NOT NULL,
    fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado ENUM('ACTIVO','INACTIVO','BLOQUEADO') NOT NULL DEFAULT 'ACTIVO',
    PRIMARY KEY (id_usuario),
    CONSTRAINT uq_usuarios_email UNIQUE (email),
    CONSTRAINT uq_usuarios_numero_socio UNIQUE (numero_socio),
    CONSTRAINT fk_usuarios_roles
      FOREIGN KEY (id_rol) REFERENCES roles(id_rol)
      ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_usuarios_nombre (apellido, nombre),
    INDEX idx_usuarios_rol_estado (id_rol, estado)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS autores (
    id_autor BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NULL,
    nacionalidad VARCHAR(80) NULL,
    biografia TEXT NULL,
    estado ENUM('ACTIVO','INACTIVO') NOT NULL DEFAULT 'ACTIVO',
    PRIMARY KEY (id_autor),
    INDEX idx_autores_nombre (apellido, nombre)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS categorias (
    id_categoria BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255) NULL,
    estado ENUM('ACTIVA','INACTIVA') NOT NULL DEFAULT 'ACTIVA',
    PRIMARY KEY (id_categoria),
    CONSTRAINT uq_categorias_nombre UNIQUE (nombre)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS editoriales (
    id_editorial BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(150) NOT NULL,
    pais VARCHAR(80) NULL,
    sitio_web VARCHAR(255) NULL,
    estado ENUM('ACTIVA','INACTIVA') NOT NULL DEFAULT 'ACTIVA',
    PRIMARY KEY (id_editorial),
    CONSTRAINT uq_editoriales_nombre UNIQUE (nombre)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS libros (
    id_libro BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    isbn VARCHAR(20) NULL,
    titulo VARCHAR(255) NOT NULL,
    subtitulo VARCHAR(255) NULL,
    id_categoria BIGINT UNSIGNED NOT NULL,
    id_editorial BIGINT UNSIGNED NULL,
    anio_publicacion SMALLINT UNSIGNED NULL,
    idioma VARCHAR(60) NULL,
    descripcion TEXT NULL,
    fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado ENUM('ACTIVO','INACTIVO') NOT NULL DEFAULT 'ACTIVO',
    PRIMARY KEY (id_libro),
    CONSTRAINT uq_libros_isbn UNIQUE (isbn),
    CONSTRAINT fk_libros_categorias
      FOREIGN KEY (id_categoria) REFERENCES categorias(id_categoria)
      ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_libros_editoriales
      FOREIGN KEY (id_editorial) REFERENCES editoriales(id_editorial)
      ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_libros_anio
      CHECK (anio_publicacion IS NULL OR anio_publicacion BETWEEN 1 AND 9999),
    INDEX idx_libros_titulo (titulo),
    INDEX idx_libros_categoria (id_categoria),
    INDEX idx_libros_editorial (id_editorial)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS libro_autor (
    id_libro BIGINT UNSIGNED NOT NULL,
    id_autor BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id_libro, id_autor),
    CONSTRAINT fk_libro_autor_libros
      FOREIGN KEY (id_libro) REFERENCES libros(id_libro)
      ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_libro_autor_autores
      FOREIGN KEY (id_autor) REFERENCES autores(id_autor)
      ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_libro_autor_autor (id_autor)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ejemplares (
    id_ejemplar BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_libro BIGINT UNSIGNED NOT NULL,
    codigo_inventario VARCHAR(50) NOT NULL,
    ubicacion VARCHAR(100) NULL,
    fecha_adquisicion DATE NULL,
    estado ENUM('DISPONIBLE','PRESTADO','RESERVADO','PERDIDO','MANTENIMIENTO')
      NOT NULL DEFAULT 'DISPONIBLE',
    observaciones VARCHAR(500) NULL,
    PRIMARY KEY (id_ejemplar),
    CONSTRAINT uq_ejemplares_codigo UNIQUE (codigo_inventario),
    CONSTRAINT fk_ejemplares_libros
      FOREIGN KEY (id_libro) REFERENCES libros(id_libro)
      ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_ejemplares_libro_estado (id_libro, estado),
    INDEX idx_ejemplares_estado (estado)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS prestamos (
    id_prestamo BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_lector BIGINT UNSIGNED NOT NULL,
    id_usuario_responsable BIGINT UNSIGNED NOT NULL,
    fecha_prestamo DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_vencimiento DATE NOT NULL,
    estado ENUM('ACTIVO','FINALIZADO','CANCELADO') NOT NULL DEFAULT 'ACTIVO',
    observaciones VARCHAR(500) NULL,
    PRIMARY KEY (id_prestamo),
    CONSTRAINT fk_prestamos_lector
      FOREIGN KEY (id_lector) REFERENCES usuarios(id_usuario)
      ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_prestamos_responsable
      FOREIGN KEY (id_usuario_responsable) REFERENCES usuarios(id_usuario)
      ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_prestamos_vencimiento
      CHECK (fecha_vencimiento >= DATE(fecha_prestamo)),
    INDEX idx_prestamos_lector_estado (id_lector, estado),
    INDEX idx_prestamos_vencimiento_estado (fecha_vencimiento, estado),
    INDEX idx_prestamos_fecha (fecha_prestamo)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS detalle_prestamo (
    id_detalle BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_prestamo BIGINT UNSIGNED NOT NULL,
    id_ejemplar BIGINT UNSIGNED NOT NULL,
    fecha_devolucion_real DATETIME NULL,
    estado ENUM('PRESTADO','DEVUELTO','PERDIDO') NOT NULL DEFAULT 'PRESTADO',
    observaciones VARCHAR(500) NULL,
    PRIMARY KEY (id_detalle),
    CONSTRAINT fk_detalle_prestamo
      FOREIGN KEY (id_prestamo) REFERENCES prestamos(id_prestamo)
      ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_detalle_ejemplar
      FOREIGN KEY (id_ejemplar) REFERENCES ejemplares(id_ejemplar)
      ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_detalle_devolucion
      CHECK (
        (estado = 'PRESTADO' AND fecha_devolucion_real IS NULL)
        OR
        (estado IN ('DEVUELTO','PERDIDO') AND fecha_devolucion_real IS NOT NULL)
      ),
    INDEX idx_detalle_prestamo_estado (id_prestamo, estado),
    INDEX idx_detalle_ejemplar_estado (id_ejemplar, estado),
    INDEX idx_detalle_devolucion (fecha_devolucion_real)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS configuracion (
    clave VARCHAR(100) NOT NULL,
    valor VARCHAR(255) NOT NULL,
    descripcion VARCHAR(255) NULL,
    actualizado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
      ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (clave)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS auditoria (
    id_auditoria BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_usuario BIGINT UNSIGNED NULL,
    accion VARCHAR(80) NOT NULL,
    entidad VARCHAR(80) NOT NULL,
    id_registro VARCHAR(80) NULL,
    fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    detalle JSON NULL,
    PRIMARY KEY (id_auditoria),
    CONSTRAINT fk_auditoria_usuarios
      FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario)
      ON UPDATE RESTRICT ON DELETE SET NULL,
    INDEX idx_auditoria_fecha (fecha),
    INDEX idx_auditoria_usuario_fecha (id_usuario, fecha),
    INDEX idx_auditoria_entidad (entidad, id_registro)
) ENGINE=InnoDB;

-- Roles iniciales. No se crean cuentas de usuario ni contraseñas por defecto.
INSERT INTO roles (nombre, descripcion) VALUES
  ('ADMINISTRADOR', 'Administra usuarios, permisos y configuración general.'),
  ('BIBLIOTECARIO', 'Gestiona catálogo, ejemplares, préstamos y devoluciones.'),
  ('LECTOR', 'Consulta el catálogo y su propio historial.')
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

-- Configuración inicial propuesta; el administrador podrá ajustarla.
INSERT INTO configuracion (clave, valor, descripcion) VALUES
  ('dias_prestamo', '14', 'Duración inicial del préstamo en días.'),
  ('max_ejemplares_por_prestamo', '3', 'Máximo de ejemplares por operación.'),
  ('max_prestamos_activos', '3', 'Máximo de préstamos activos por lector.'),
  ('permitir_prestamo_con_atrasos', 'false', 'Permite prestar si el lector tiene atrasos.'),
  ('dias_aviso_vencimiento', '2', 'Días de anticipación para avisar vencimientos.')
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

-- Vista de ejemplares disponibles por título.
CREATE OR REPLACE VIEW v_inventario_libros AS
SELECT
    l.id_libro,
    l.titulo,
    l.isbn,
    c.nombre AS categoria,
    e.nombre AS editorial,
    COUNT(ep.id_ejemplar) AS total_ejemplares,
    SUM(CASE WHEN ep.estado = 'DISPONIBLE' THEN 1 ELSE 0 END) AS ejemplares_disponibles,
    SUM(CASE WHEN ep.estado = 'PRESTADO' THEN 1 ELSE 0 END) AS ejemplares_prestados
FROM libros l
JOIN categorias c ON c.id_categoria = l.id_categoria
LEFT JOIN editoriales e ON e.id_editorial = l.id_editorial
LEFT JOIN ejemplares ep ON ep.id_libro = l.id_libro
GROUP BY l.id_libro, l.titulo, l.isbn, c.nombre, e.nombre;

-- Vista de préstamos con ejemplares pendientes de devolución.
CREATE OR REPLACE VIEW v_prestamos_pendientes AS
SELECT
    p.id_prestamo,
    p.id_lector,
    CONCAT(u.nombre, ' ', u.apellido) AS lector,
    p.fecha_prestamo,
    p.fecha_vencimiento,
    p.estado AS estado_prestamo,
    COUNT(dp.id_detalle) AS ejemplares_pendientes,
    CASE
      WHEN p.estado = 'ACTIVO' AND p.fecha_vencimiento < CURRENT_DATE()
        THEN 'ATRASADO'
      ELSE p.estado
    END AS estado_calculado
FROM prestamos p
JOIN usuarios u ON u.id_usuario = p.id_lector
JOIN detalle_prestamo dp ON dp.id_prestamo = p.id_prestamo
WHERE p.estado = 'ACTIVO'
  AND dp.estado = 'PRESTADO'
GROUP BY
    p.id_prestamo, p.id_lector, u.nombre, u.apellido,
    p.fecha_prestamo, p.fecha_vencimiento, p.estado;

-- NOTAS DE IMPLEMENTACIÓN:
-- 1. Crear préstamos y actualizar ejemplares dentro de una transacción JDBC.
-- 2. Antes de prestar, bloquear el ejemplar (SELECT ... FOR UPDATE) y comprobar
--    que está DISPONIBLE y no tiene otro detalle pendiente.
-- 3. Al devolver, actualizar detalle_prestamo y ejemplares en la misma transacción.
-- 4. Al finalizar/cancelar un préstamo, recalcular su estado a partir de sus detalles.
-- 5. Validar que id_lector corresponde a un usuario con rol LECTOR y que el
--    responsable tiene rol ADMINISTRADOR o BIBLIOTECARIO en la capa de servicio.
-- 6. Crear la primera cuenta de administrador mediante un flujo seguro que genere
--    un hash de contraseña; nunca insertar una contraseña en texto plano.
