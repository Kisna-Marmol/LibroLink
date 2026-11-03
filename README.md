# LibroLink — Gestor de Biblioteca

*Conectando personas con grandes historias.*

LibroLink es una aplicación de escritorio para administrar una biblioteca: catálogo bibliográfico, ejemplares físicos,
lectores, préstamos y devoluciones, reportes, roles, configuración y auditoría.

- **Interfaz:** JavaFX 21 (FXML + CSS) · **Lenguaje:** Java 21
- **Base de datos:** MariaDB 10.4+ (XAMPP) o MySQL 8, base `bibliolink_db`, acceso por JDBC
- **Arquitectura:** MVC + DAO + Service · **Build y pruebas:** Maven, JUnit 5

## Funcionalidades

| Módulo | Qué permite |
|---|---|
| Panel | Estadísticas reales: títulos, ejemplares disponibles, préstamos activos y atrasados, actividad reciente |
| Catálogo | Libros con autores, categoría y editorial; búsqueda por título, ISBN, autor, categoría o editorial; desactivación lógica |
| Inventario | Ejemplares con código único, ubicación, fecha de adquisición y estado |
| Lectores | Datos de contacto, número de socio, estado e historial de préstamos |
| Préstamos | Varios ejemplares por préstamo, vencimientos, devoluciones parciales, ejemplares perdidos y cancelación |
| Catálogos auxiliares | Autores, categorías y editoriales |
| Reportes | Atrasos, historial por lector, inventario, préstamos por fechas y libros más solicitados, con exportación a CSV |
| Usuarios y roles | Cuentas del personal, restablecimiento de contraseñas |
| Configuración | Parámetros de circulación, consulta de auditoría y respaldo de la base de datos |

## Roles y permisos

| Operación | Administrador | Bibliotecario | Lector |
|---|:-:|:-:|:-:|
| Consultar el catálogo | Sí | Sí | Sí |
| Gestionar libros, ejemplares, autores, categorías y editoriales | Sí | Sí | No |
| Gestionar lectores | Sí | Sí | No |
| Préstamos y devoluciones | Sí | Sí | No |
| Consultar reportes | Sí | Sí | No |
| Gestionar cuentas del personal y roles | Sí | No | No |
| Configuración, auditoría y respaldo | Sí | No | No |
| Consultar su propio historial | — | — | Sí |

Los permisos se verifican en la capa de servicio, no solo ocultando botones.

## Requisitos

- JDK 21
- Maven 3.9 o superior
- MariaDB 10.4+ o MySQL 8 en `localhost:3306`

## Instalación

### 1. Obtener el código

```bash
git clone <url-del-repositorio> LibroLink
cd LibroLink
```

### 2. Crear la base de datos

Ejecuta el script `database/schema_librolink.sql` con tu cliente (MySQL Workbench, phpMyAdmin o consola). Crea la base
`bibliolink_db`, las tablas, las vistas, los roles iniciales y la configuración inicial. No crea usuarios ni contraseñas.

### 3. Crear un usuario de base de datos dedicado (recomendado)

Con una cuenta administradora de MySQL/MariaDB:

```sql
CREATE USER 'librolink'@'localhost' IDENTIFIED BY 'una_clave_segura';
GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW, TRIGGER ON bibliolink_db.* TO 'librolink'@'localhost';
FLUSH PRIVILEGES;
```

`SHOW VIEW` y `TRIGGER` hacen falta para el respaldo con `mysqldump`.

### 4. Configurar la conexión

```bash
cp config/db.properties.example config/db.properties
```

Edita `config/db.properties` con tu usuario y contraseña. **Ese archivo no se sube a Git.** La aplicación lo busca, en
este orden: la variable de entorno `LIBROLINK_CONFIG`, `config/db.properties`, `%APPDATA%\LibroLink\db.properties` y
`~/.librolink/db.properties`.

### 5. Ejecutar

```bash
mvn javafx:run
```

La primera vez no hay administradores: la aplicación muestra una pantalla para crear el primero, con tu propia
contraseña (mínimo 10 caracteres, con letras y números). No existen credenciales por defecto.

## Pruebas

```bash
mvn test
```

Las pruebas de base de datos (`*DaoTest`, `DatabaseTest`, `RespaldoDumpIntegracionTest`) usan `config/db.properties` y
**no dejan datos**: trabajan dentro de una transacción que se revierte. Sin ese archivo se omiten solas. Si la base está
apagada fallan; para correr solo las pruebas que no la necesitan:

```powershell
mvn test "-Dtest=!*DaoTest,!DatabaseTest,!RespaldoDumpIntegracionTest"
```

La lista de verificación manual de punta a punta está en [`docs/VERIFICACION.md`](docs/VERIFICACION.md).

## Empaquetado

```bash
mvn -DskipTests package
java -jar target/librolink-1.0.0-shaded.jar
```

Genera un JAR ejecutable con todas las dependencias (incluye los componentes de JavaFX de tu sistema operativo: compila
en el sistema en el que lo vas a usar). Para crear además una carpeta con `LibroLink.exe` y Java incluido (Windows,
JDK 21 con `jpackage`):

```powershell
.\scripts\crear-app.ps1
```

Antes de abrir la aplicación empaquetada, copia tu `db.properties` a `%APPDATA%\LibroLink\db.properties`.

## Respaldo y restauración

**Configuración > Respaldo > Crear respaldo ahora** genera un archivo `.sql` con `mysqldump` o `mariadb-dump`
(se busca en el PATH y en las carpetas habituales de XAMPP y MySQL; si está en otro lugar, define `backup.dump` en
`db.properties`). Para restaurar, crea una base vacía y ejecuta en una consola:

```bash
mysql -u USUARIO -p NOMBRE_BASE < librolink_respaldo_AAAAMMDD_HHMM.sql
```

Los respaldos contienen datos personales de los lectores y los hashes de contraseñas: guárdalos en un lugar privado.

## Estructura del proyecto

```
LibroLink/
├── pom.xml
├── README.md
├── config/db.properties.example      # plantilla sin secretos (db.properties queda fuera de Git)
├── database/schema_librolink.sql     # esquema de la base de datos
├── docs/VERIFICACION.md              # lista de verificación y trazabilidad de reglas
├── scripts/crear-app.ps1             # empaquetado en Windows
└── src/
    ├── main/java/com/librolink/
    │   ├── Main.java, Launcher.java
    │   ├── config/      # AppConfig, Database (pool HikariCP y transacciones)
    │   ├── model/       # registros y enumeraciones
    │   ├── dao/         # acceso a datos con JDBC (consultas parametrizadas)
    │   ├── service/     # reglas de negocio, autorización y auditoría
    │   └── controller/  # controladores JavaFX y navegación
    ├── main/resources/  # fxml/, css/, images/
    └── test/java/       # pruebas unitarias y de integración
```

## Reglas de negocio principales

- Solo lectores activos y ejemplares disponibles pueden recibir préstamos; un ejemplar nunca tiene dos préstamos activos.
- Un préstamo admite varios ejemplares y se devuelve en partes; el préstamo finaliza al devolver el último.
- El atraso se calcula por fecha (vencimiento anterior a hoy con ejemplares sin devolver); no hay tareas diarias.
- Los parámetros de circulación (días de préstamo, máximos, atrasos, aviso) se editan en Configuración.
- Los préstamos nunca se borran; los registros con historial se desactivan en lugar de eliminarse.
- Crear un préstamo, devolver y cambiar estados ocurren en una única transacción, con bloqueo de filas.

## Seguridad

- Contraseñas con BCrypt (costo 12), nunca en texto plano ni en la auditoría.
- Bloqueo temporal tras 5 intentos fallidos de ingreso (15 minutos).
- Consultas parametrizadas; autorización por rol en los servicios (con una prueba que lo verifica).
- Auditoría de operaciones relevantes; las exportaciones y los respaldos quedan registrados.
- Los CSV neutralizan fórmulas de hoja de cálculo.

## Solución de problemas

| Mensaje | Causa y solución |
|---|---|
| `Communications link failure` / `Connection refused` | La base de datos está apagada. Inicia MySQL (XAMPP) y revisa el puerto de `db.url`. |
| `Access denied for user` | Usuario o contraseña de `db.properties` incorrectos. |
| `Public Key Retrieval is not allowed` (MySQL 8) | Agrega `&allowPublicKeyRetrieval=true` a `db.url` solo en una instalación local. |
| `Falta la propiedad obligatoria: db.url` | `db.properties` incompleto o con otro nombre de clave. |
| `No se encontró el archivo de configuración` | Revisa las ubicaciones que lista el mensaje. |
| `SHOW VIEW command denied` al respaldar | Ejecuta el `GRANT` del paso 3. |
| `No se encontró mysqldump` | Define `backup.dump=C:/xampp/mysql/bin/mysqldump.exe` en `db.properties`. |
| Las tildes del CSV se ven mal | Abre el archivo con "Datos > Desde texto/CSV" y codificación UTF-8. |
| Aviso `Unsupported JavaFX configuration` al usar el JAR | Es informativo; la aplicación funciona. |

## Convenciones de desarrollo

- Un commit por etapa o módulo: `feat: etapa N - descripción corta`.
- El SQL debe funcionar tanto en MariaDB como en MySQL 8 (por ejemplo, `JSON_VALUE` y no `->>`).
- Toda operación nueva de un servicio debe llamar primero a `sesion.requerirRol(...)` o `sesion.requerirUsuario()`.