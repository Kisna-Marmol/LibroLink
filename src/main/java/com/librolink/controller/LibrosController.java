package com.librolink.controller;

import com.librolink.model.Autor;
import com.librolink.model.Categoria;
import com.librolink.model.DatosLibro;
import com.librolink.model.Editorial;
import com.librolink.model.FiltroLibros;
import com.librolink.model.Libro;
import com.librolink.model.OpcionesLibro;
import com.librolink.service.LibroService;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class LibrosController extends GestorAuxiliar<Libro> {

    private static final Categoria TODAS = new Categoria(0L, "Todas las categorías", null, true);
    private static final Editorial SIN_EDITORIAL = new Editorial(0L, "— Sin editorial —", null, null, true);

    private final LibroService servicio;
    /** Se escribe en el hilo de JavaFX y se lee en el hilo de fondo de la consulta. */
    private volatile Long categoriaFiltro;

    @FXML private ScrollPane panelFormulario;
    @FXML private ComboBox<Categoria> filtroCategoria;
    @FXML private Label aviso;
    @FXML private TextField isbn;
    @FXML private TextField titulo;
    @FXML private TextField subtitulo;
    @FXML private ComboBox<Categoria> comboCategoria;
    @FXML private ComboBox<Editorial> comboEditorial;
    @FXML private TextField anio;
    @FXML private TextField idioma;
    @FXML private TextArea descripcion;
    @FXML private ComboBox<Autor> autorCombo;
    @FXML private ListView<Autor> autoresLista;

    public LibrosController(LibroService servicio) {
        this.servicio = servicio;
    }

    @FXML
    private void initialize() {
        boolean gestiona = servicio.puedeGestionar();
        for (Node n : List.<Node>of(panelFormulario, incluirInactivos)) {
            n.setVisible(gestiona);
            n.setManaged(gestiona);
        }

        filtroCategoria.setConverter(convertidor(Categoria::nombre));
        comboCategoria.setConverter(convertidor(Categoria::nombre));
        comboEditorial.setConverter(convertidor(Editorial::nombre));
        autorCombo.setConverter(convertidor(Autor::nombreCompleto));
        autoresLista.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Autor a, boolean vacio) {
                super.updateItem(a, vacio);
                setText(vacio || a == null ? null : a.nombreCompleto() + (a.activo() ? "" : " (inactivo)"));
            }
        });

        filtroCategoria.valueProperty().addListener((obs, anterior, nueva) -> {
            Long id = (nueva == null || nueva.id() == TODAS.id()) ? null : nueva.id();
            if (!Objects.equals(id, categoriaFiltro)) {
                categoriaFiltro = id;
                recargar();
            }
        });

        iniciar();
        cargarOpciones();
    }

    private static <T> StringConverter<T> convertidor(Function<T, String> texto) {
        return new StringConverter<>() {
            @Override
            public String toString(T t) {
                return t == null ? "" : texto.apply(t);
            }

            @Override
            public T fromString(String s) {
                return null;
            }
        };
    }

    private void cargarOpciones() {
        Tareas.ejecutar(servicio::categoriasActivas, lista -> {
            List<Categoria> items = new ArrayList<>();
            items.add(TODAS);
            items.addAll(lista);
            filtroCategoria.getItems().setAll(items);
            filtroCategoria.setValue(TODAS);
        }, ex -> error.setText(Tareas.mensaje(ex)));

        if (servicio.puedeGestionar()) {
            Tareas.ejecutar(servicio::opcionesFormulario, this::aplicarOpciones,
                    ex -> error.setText(Tareas.mensaje(ex)));
        }
    }

    private void aplicarOpciones(OpcionesLibro o) {
        comboCategoria.getItems().setAll(o.categorias());
        List<Editorial> editoriales = new ArrayList<>();
        editoriales.add(SIN_EDITORIAL);
        editoriales.addAll(o.editoriales());
        comboEditorial.getItems().setAll(editoriales);
        autorCombo.getItems().setAll(o.autores());

        if (seleccionado != null) {
            mostrarEnFormulario(seleccionado); // por si el usuario eligió una fila antes de que llegaran las opciones
        } else if (comboEditorial.getValue() == null) {
            comboEditorial.setValue(SIN_EDITORIAL);
        }
    }

    // ---------- GestorAuxiliar ----------

    @Override
    protected List<Libro> consultar(String texto, boolean inactivos) {
        return servicio.buscar(new FiltroLibros(texto, categoriaFiltro, inactivos));
    }

    @Override
    protected void alCargar(List<Libro> lista) {
        aviso.setText(lista.size() >= servicio.limiteResultados()
                ? "Se muestran los primeros " + servicio.limiteResultados() + " resultados. Afina la búsqueda."
                : "");
    }

    @Override
    protected void columnas() {
        agregarColumna("Título", 220,
                l -> l.subtitulo() == null ? l.titulo() : l.titulo() + ": " + l.subtitulo());
        agregarColumna("Autores", 150, Libro::autoresTexto);
        agregarColumna("Categoría", 110, Libro::categoria);
        agregarColumna("ISBN", 120, l -> sinNulo(l.isbn()));
        agregarColumna("Disp.", 60, l -> l.ejemplaresDisponibles() + "/" + l.ejemplaresTotal());
    }

    private void agregarColumna(String titulo, double ancho, Function<Libro, String> valor) {
        TableColumn<Libro, String> c = columna(titulo, valor);
        c.setPrefWidth(ancho);
        tabla.getColumns().add(c);
    }

    @Override
    protected boolean estaActivo(Libro l) {
        return l.activo();
    }

    @Override
    protected void mostrarEnFormulario(Libro l) {
        if (l == null) {
            isbn.clear();
            titulo.clear();
            subtitulo.clear();
            anio.clear();
            idioma.clear();
            descripcion.clear();
            comboCategoria.setValue(null);
            comboEditorial.setValue(SIN_EDITORIAL);
            autorCombo.setValue(null);
            autoresLista.getItems().clear();
            return;
        }
        isbn.setText(sinNulo(l.isbn()));
        titulo.setText(l.titulo());
        subtitulo.setText(sinNulo(l.subtitulo()));
        anio.setText(l.anio() == null ? "" : String.valueOf(l.anio()));
        idioma.setText(sinNulo(l.idioma()));
        descripcion.setText(sinNulo(l.descripcion()));

        // Si la categoría o la editorial del libro están inactivas no están en las opciones: se agregan marcadas.
        Categoria cat = comboCategoria.getItems().stream()
                .filter(c -> c.id() == l.idCategoria()).findFirst().orElse(null);
        if (cat == null) {
            cat = new Categoria(l.idCategoria(), l.categoria() + " (inactiva)", null, false);
            comboCategoria.getItems().add(cat);
        }
        comboCategoria.setValue(cat);

        Editorial ed = SIN_EDITORIAL;
        if (l.idEditorial() != null) {
            Long idEd = l.idEditorial();
            ed = comboEditorial.getItems().stream()
                    .filter(e -> e.id() == idEd).findFirst().orElse(null);
            if (ed == null) {
                ed = new Editorial(idEd, l.editorial() + " (inactiva)", null, null, false);
                comboEditorial.getItems().add(ed);
            }
        }
        comboEditorial.setValue(ed);

        autorCombo.setValue(null);
        autoresLista.getItems().setAll(l.autores());
    }

    @Override
    protected void guardarFormulario() {
        Categoria cat = comboCategoria.getValue();
        Editorial ed = comboEditorial.getValue();
        DatosLibro datos = new DatosLibro(
                isbn.getText(), titulo.getText(), subtitulo.getText(),
                cat == null ? null : cat.id(),
                (ed == null || ed.id() == SIN_EDITORIAL.id()) ? null : ed.id(),
                anio.getText(), idioma.getText(), descripcion.getText(),
                autoresLista.getItems().stream().map(Autor::id).toList());
        Libro sel = seleccionado;
        ejecutar(() -> {
            if (sel == null) {
                servicio.crear(datos);
            } else {
                servicio.actualizar(sel.id(), datos);
            }
        });
    }

    @Override
    protected void cambiarEstado(Libro l, boolean activo) {
        servicio.cambiarEstado(l.id(), activo);
    }

    // ---------- autores del formulario ----------

    @FXML
    private void agregarAutor() {
        Autor a = autorCombo.getValue();
        if (a == null) {
            return;
        }
        boolean yaEsta = autoresLista.getItems().stream().anyMatch(x -> x.id() == a.id());
        if (!yaEsta) {
            autoresLista.getItems().add(a);
        }
        autorCombo.setValue(null);
    }

    @FXML
    private void quitarAutor() {
        Autor sel = autoresLista.getSelectionModel().getSelectedItem();
        if (sel != null) {
            autoresLista.getItems().remove(sel);
        }
    }
}