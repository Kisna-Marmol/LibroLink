# Lista de verificación de LibroLink

Úsala antes de entregar o de publicar una versión. Marca cada punto cuando el resultado coincida con lo esperado.

## 0. Preparación

- [ ] `mvn test` termina en verde (los tests de base de datos necesitan MariaDB/MySQL encendida).
- [ ] La base `bibliolink_db` existe y `config/db.properties` apunta a ella con el usuario `librolink`.
- [ ] `git status` no muestra `config/db.properties`, `target/` ni archivos `.sql` o `.csv` exportados.

## 1. Acceso y seguridad

- [ ] Con la base sin administradores aparece "Crear administrador"; rechaza contraseñas cortas, sin números o distintas.
- [ ] Tras crear el administrador, "Cerrar sesión" lleva al login (no a la pantalla de creación).
- [ ] Un correo inexistente y una contraseña incorrecta muestran el mismo mensaje.
- [ ] Tras 5 intentos fallidos con una cuenta, el sexto muestra "Demasiados intentos fallidos…" aunque la clave sea correcta.
- [ ] Las contraseñas no aparecen en ninguna consulta de `auditoria` ni en `usuarios` (solo hashes que empiezan con `$2`).
- [ ] "Cambiar contraseña" rechaza una contraseña actual incorrecta y acepta una correcta.

## 2. Roles

- [ ] Administrador: ve todas las secciones.
- [ ] Bibliotecario: no ve "Usuarios y roles" ni "Configuración"; en "Lectores" solo ve lectores.
- [ ] Lector: solo ve "Catálogo" (sin formulario ni libros inactivos) y "Mi historial".
- [ ] El bibliotecario no puede crear cuentas de administrador ni de bibliotecario.
- [ ] Nadie puede cambiar su propio rol o estado, ni dejar al sistema sin un administrador activo.

## 3. Catálogos y libros

- [ ] Autores, categorías y editoriales: crear, editar, desactivar y reactivar.
- [ ] Nombres repetidos de categoría o editorial se rechazan; una URL sin `https://` se rechaza.
- [ ] Libro: ISBN válido se acepta (con o sin guiones); inválido o repetido se rechaza.
- [ ] Un libro admite varios autores; sin categoría no se guarda.
- [ ] Búsqueda por título, ISBN, autor, categoría y editorial; filtro por categoría.
- [ ] Un libro con ejemplares prestados no se puede desactivar.

## 4. Inventario

- [ ] "Sugerir" propone el siguiente código (`LL-000001`…); un código repetido se rechaza.
- [ ] El estado "Prestado" no se puede asignar a mano; un ejemplar prestado no cambia de libro ni de estado.
- [ ] La columna "Disp." del catálogo coincide con el inventario.

## 5. Lectores y cuentas

- [ ] Crear lector con número de socio, correo y contraseña inicial; correo o socio repetido se rechaza.
- [ ] Restablecer contraseña de un lector; el lector entra con la nueva.
- [ ] Un lector Bloqueado no puede ingresar; con ejemplares sin devolver no se puede pasar a Inactivo.
- [ ] No se cambia el rol de un usuario que ya tiene préstamos.

## 6. Préstamos

- [ ] Registrar un préstamo de 2 ejemplares: vencimiento = hoy + días configurados; los ejemplares quedan "Prestado".
- [ ] Se rechazan: ejemplar no disponible, código inexistente, ejemplar repetido, libro inactivo, lector inactivo.
- [ ] Se rechaza superar el máximo de ejemplares y el máximo de préstamos activos.
- [ ] Devolución parcial: el préstamo sigue activo; al devolver el último pasa a Finalizado.
- [ ] "Marcar perdido" deja el ejemplar en Perdido; "Cancelar" solo funciona si no hubo devoluciones.
- [ ] Un préstamo vencido aparece "Atrasado" (el día de vencimiento no cuenta) y bloquea nuevos préstamos al lector.
- [ ] Dos pantallas intentando prestar el mismo ejemplar a la vez: solo una lo logra.

## 7. Reportes

- [ ] Los 6 reportes generan resultados coherentes con los datos.
- [ ] "Préstamos por rango" rechaza "hasta" anterior a "desde".
- [ ] El CSV abre en Excel con tildes correctas; una celda que empieza con `=` aparece con apóstrofo delante.
- [ ] Cada exportación queda en la auditoría (`EXPORTAR`).

## 8. Configuración, auditoría y respaldo

- [ ] Cambiar "Días de préstamo" afecta a los préstamos nuevos; valores fuera de rango se rechazan.
- [ ] Cada cambio de configuración queda en auditoría con valor anterior y nuevo.
- [ ] La auditoría filtra por fecha, usuario, acción y entidad, y pagina de 50 en 50.
- [ ] El respaldo genera un `.sql` que contiene las tablas; restaurarlo en una base vacía funciona.

## 9. Cierre

- [ ] `mvn -DskipTests package` genera `target/librolink-1.0.0-shaded.jar` y `java -jar` abre la aplicación.
- [ ] (Windows) `scripts\crear-app.ps1` genera `LibroLink.exe`, que abre con `db.properties` en `%APPDATA%\LibroLink`.
- [ ] `git tag v1.0.0` sobre el commit final.

## Trazabilidad: reglas de negocio e implementación

| Regla | Dónde se cumple | Prueba |
|---|---|---|
| RN-01 Solo lectores activos y ejemplares disponibles | `PrestamoService.crear`, `problemaDePrestamo` | `PrestamoServiceReglasTest`, `PrestamoDaoTest` |
| RN-02 Un ejemplar no tiene dos préstamos activos | `SELECT … FOR UPDATE` del ejemplar y conteo de detalles pendientes | `PrestamoServiceReglasTest`, `EjemplarDaoTest` |
| RN-03 Devoluciones individuales | `PrestamoService.devolver` | `PrestamoDaoTest` |
| RN-04 Atraso calculado, no manual | `Prestamo.atrasado` y SQL con `CURRENT_DATE` | `PrestamoTest`, `PrestamoDaoTest` |
| RN-05 Historial íntegro | No existe borrado de préstamos; cancelar conserva los detalles | `PrestamoDaoTest` |
| RN-06 Transacciones | `Database.enTransaccion` en crear y devolver | `PrestamoDaoTest` |
| RN-07 Contraseñas con hash | `PasswordHasher` (BCrypt costo 12) | `PasswordHasherTest` |
| RN-08 Validación de datos | `Validaciones` | `Validaciones*Test` |
| Autorización en la capa de servicio | `Sesion.requerirRol` en cada método | `AutorizacionEnServiciosTest` y las pruebas por servicio |
| Desactivar en vez de borrar | Los DAO no borran registros con historial | `LibroDaoTest`, `UsuarioDaoTest`, `AuxiliaresDaoTest` |