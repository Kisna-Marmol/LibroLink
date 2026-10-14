package com.librolink.service;

import com.librolink.config.Database;
import com.librolink.dao.AuditoriaDao;
import com.librolink.dao.AutorDao;
import com.librolink.dao.CategoriaDao;
import com.librolink.dao.EditorialDao;
import com.librolink.dao.LibroDao;
import com.librolink.dao.LibroDao.Referencias;
import com.librolink.model.Categoria;
import com.librolink.model.DatosLibro;
import com.librolink.model.FiltroLibros;
import com.librolink.model.Libro;
import com.librolink.model.OpcionesLibro;
import com.librolink.model.Rol;
import com.librolink.model.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LibroService {

    private static final int LIMITE_RESULTADOS = 500;
    private static final int MAX_AUTORES = 20;
    private static final int MAX_TEXTO_BUSQUEDA = 100;

    private final Database db;
    private final Sesion sesion;
    private final LibroDao dao = new LibroDao();
    private final CategoriaDao categoriaDao = new CategoriaDao();
    private final EditorialDao editorialDao = new EditorialDao();
    private final AutorDao autorDao = new AutorDao();
    private final AuditoriaDao auditoria = new AuditoriaDao();

    public LibroService(Database db, Sesion sesion) {
        this.db = db;
        this.sesion = sesion;
    }

    public int limiteResultados() {
        return LIMITE_RESULTADOS;
    }

    /** Solo para decidir qué mostrar en pantalla; cada operación vuelve a autorizar por su cuenta. */
    public boolean puedeGestionar() {
        return sesion.usuario()
                .map(u -> u.rol() == Rol.ADMINISTRADOR || u.rol() == Rol.BIBLIOTECARIO)
                .orElse(false);
    }

    /** Cualquier usuario autenticado consulta; el LECTOR nunca ve libros inactivos. */
    public List<Libro> buscar(FiltroLibros filtro) {
        Usuario u = sesion.requerirUsuario();
        boolean verInactivos = filtro.incluirInactivos() && u.rol() != Rol.LECTOR;
        FiltroLibros efectivo = new FiltroLibros(recortar(filtro.texto()), filtro.idCategoria(), verInactivos);
        try (Connection cn = db.getConnection()) {
            return dao.listar(cn, efectivo, LIMITE_RESULTADOS);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudo consultar el catálogo.", e);
        }
    }

    /** Categorías activas para el filtro de búsqueda; disponible para cualquier usuario autenticado. */
    public List<Categoria> categoriasActivas() {
        sesion.requerirUsuario();
        try (Connection cn = db.getConnection()) {
            return categoriaDao.listar(cn, null, false);
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudieron cargar las categorías.", e);
        }
    }

    public OpcionesLibro opcionesFormulario() {
        sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        try (Connection cn = db.getConnection()) {
            return new OpcionesLibro(
                    categoriaDao.listar(cn, null, false),
                    editorialDao.listar(cn, null, false),
                    autorDao.listar(cn, null, false));
        } catch (SQLException e) {
            throw new ServicioException.Datos("No se pudieron cargar las opciones del formulario.", e);
        }
    }

    public long crear(DatosLibro datos) {
        Usuario u = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        LibroDao.Datos d = normalizar(datos);
        List<Long> idsAutores = idsAutores(datos);
        try {
            return db.enTransaccion(cn -> {
                exigirCategoriaActiva(cn, d.idCategoria());
                if (d.idEditorial() != null) {
                    exigirEditorialActiva(cn, d.idEditorial());
                }
                exigirAutoresActivos(cn, idsAutores);

                long id = dao.insertar(cn, d);
                dao.reemplazarAutores(cn, id, idsAutores);
                auditoria.registrar(cn, u.id(), "CREAR", "libros", String.valueOf(id), null);
                return id;
            });
        } catch (SQLException e) {
            throw traducir(e, "No se pudo crear el libro.");
        }
    }

    public void actualizar(long id, DatosLibro datos) {
        Usuario u = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        LibroDao.Datos d = normalizar(datos);
        List<Long> idsAutores = idsAutores(datos);
        try {
            db.enTransaccion(cn -> {
                Referencias actual = dao.bloquear(cn, id)
                        .orElseThrow(() -> new ServicioException.Validacion("El libro ya no existe."));
                Set<Long> autoresActuales = new HashSet<>(dao.idsAutores(cn, id));

                // Solo lo que cambia debe estar activo: un libro puede conservar referencias ya inactivas.
                if (d.idCategoria() != actual.idCategoria()) {
                    exigirCategoriaActiva(cn, d.idCategoria());
                }
                if (d.idEditorial() != null && !d.idEditorial().equals(actual.idEditorial())) {
                    exigirEditorialActiva(cn, d.idEditorial());
                }
                exigirAutoresActivos(cn, idsAutores.stream().filter(a -> !autoresActuales.contains(a)).toList());

                dao.actualizar(cn, id, d);
                dao.reemplazarAutores(cn, id, idsAutores);
                auditoria.registrar(cn, u.id(), "ACTUALIZAR", "libros", String.valueOf(id), null);
                return null;
            });
        } catch (SQLException e) {
            throw traducir(e, "No se pudo actualizar el libro.");
        }
    }

    public void cambiarEstado(long id, boolean activo) {
        Usuario u = sesion.requerirRol(Rol.ADMINISTRADOR, Rol.BIBLIOTECARIO);
        try {
            db.enTransaccion(cn -> {
                if (dao.bloquear(cn, id).isEmpty()) {
                    throw new ServicioException.Validacion("El libro ya no existe.");
                }
                if (!activo) {
                    long prestados = dao.contarEjemplaresPrestados(cn, id);
                    if (prestados > 0) {
                        throw new ServicioException.Validacion(
                                "No se puede desactivar: hay " + prestados + " ejemplar(es) prestado(s).");
                    }
                }
                dao.cambiarEstado(cn, id, activo);
                auditoria.registrar(cn, u.id(), activo ? "ACTIVAR" : "DESACTIVAR",
                        "libros", String.valueOf(id), null);
                return null;
            });
        } catch (SQLException e) {
            throw traducir(e, "No se pudo cambiar el estado del libro.");
        }
    }

    // ---------- validación ----------

    private LibroDao.Datos normalizar(DatosLibro d) {
        String titulo = Validaciones.textoObligatorio(d.titulo(), "El título", 255);
        String subtitulo = Validaciones.textoOpcional(d.subtitulo(), "El subtítulo", 255);
        String isbn = Validaciones.isbnOpcional(d.isbn());
        if (d.idCategoria() == null) {
            throw new ServicioException.Validacion("Selecciona una categoría.");
        }
        Integer anio = Validaciones.anioOpcional(d.anio(), LocalDate.now().getYear() + 1);
        String idioma = Validaciones.textoOpcional(d.idioma(), "El idioma", 60);
        String descripcion = Validaciones.textoOpcional(d.descripcion(), "La descripción", 5000);
        return new LibroDao.Datos(isbn, titulo, subtitulo, d.idCategoria(), d.idEditorial(),
                anio, idioma, descripcion);
    }

    private List<Long> idsAutores(DatosLibro d) {
        Set<Long> ids = new LinkedHashSet<>();
        if (d.idsAutores() != null) {
            ids.addAll(d.idsAutores());
        }
        if (ids.size() > MAX_AUTORES) {
            throw new ServicioException.Validacion(
                    "Un libro no puede tener más de " + MAX_AUTORES + " autores.");
        }
        return List.copyOf(ids);
    }

    private void exigirCategoriaActiva(Connection cn, long id) throws SQLException {
        if (!dao.categoriaActiva(cn, id).orElse(false)) {
            throw new ServicioException.Validacion("La categoría seleccionada no existe o está inactiva.");
        }
    }

    private void exigirEditorialActiva(Connection cn, long id) throws SQLException {
        if (!dao.editorialActiva(cn, id).orElse(false)) {
            throw new ServicioException.Validacion("La editorial seleccionada no existe o está inactiva.");
        }
    }

    private void exigirAutoresActivos(Connection cn, List<Long> ids) throws SQLException {
        if (ids.isEmpty()) {
            return;
        }
        if (!dao.autoresActivos(cn, ids).containsAll(ids)) {
            throw new ServicioException.Validacion(
                    "Alguno de los autores seleccionados no existe o está inactivo.");
        }
    }

    private static ServicioException traducir(SQLException e, String mensajeTecnico) {
        if (e.getErrorCode() == 1062 && String.valueOf(e.getMessage()).contains("uq_libros_isbn")) {
            return new ServicioException.Validacion("Ya existe un libro con ese ISBN.");
        }
        return new ServicioException.Datos(mensajeTecnico, e);
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        return t.length() > MAX_TEXTO_BUSQUEDA ? t.substring(0, MAX_TEXTO_BUSQUEDA) : t;
    }
}