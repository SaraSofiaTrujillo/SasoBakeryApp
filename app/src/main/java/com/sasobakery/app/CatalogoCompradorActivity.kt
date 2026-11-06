package com.sasobakery.app

import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CatalogoCompradorActivity : AppCompatActivity() {

    // Dato de ejemplo (cuando exista el inicio de sesión vendrá del comprador real)
    private val nombreComprador = "Valentina"

    private val adapter = CatalogoAdapter(
        onProductoClick = { producto -> abrirDetalle(producto) },
        onAgregarClick = { producto -> agregarAlCarrito(producto) },
        onFavoritoClick = { producto -> FavoritosRepositorio.alternar(producto.id) }
    )

    private var categoriaActual: String? = null // null = Todo
    private var mostrarTodo = false // true = "Ver todas" (todo el catálogo)

    private lateinit var scroll: ScrollView
    private lateinit var scrollChips: View
    private lateinit var etBuscar: android.widget.EditText
    private lateinit var tvBadgeCarrito: TextView
    private lateinit var tvTituloSeccion: TextView
    private lateinit var tvVerTodas: TextView
    private lateinit var tvVacio: TextView
    private lateinit var bannerEspecial: View
    private lateinit var chips: List<Pair<TextView, String?>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_catalogo_comprador)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        scroll = findViewById(R.id.scrollCatalogo)
        scrollChips = findViewById(R.id.scrollChips)
        etBuscar = findViewById(R.id.etBuscar)
        tvBadgeCarrito = findViewById(R.id.tvBadgeCarrito)
        tvTituloSeccion = findViewById(R.id.tvTituloSeccion)
        tvVerTodas = findViewById(R.id.tvVerTodas)
        tvVacio = findViewById(R.id.tvVacio)
        bannerEspecial = findViewById(R.id.bannerEspecial)

        findViewById<TextView>(R.id.tvSaludo).text = "Hola, $nombreComprador"

        val rv = findViewById<RecyclerView>(R.id.rvCatalogo)
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = adapter

        etBuscar.doAfterTextChanged { refrescar() }

        // Chips de categoría (null = Todo)
        chips = listOf(
            findViewById<TextView>(R.id.chipTodo) to null,
            findViewById<TextView>(R.id.chipTortas) to "Tortas",
            findViewById<TextView>(R.id.chipPostres) to "Postres",
            findViewById<TextView>(R.id.chipBrownies) to "Brownies",
            findViewById<TextView>(R.id.chipGalletas) to "Galletas"
        )
        chips.forEach { (chip, categoria) ->
            chip.setOnClickListener {
                categoriaActual = categoria
                mostrarTodo = false
                refrescar()
            }
        }

        // "Ver todas" / "Ver menos"
        tvVerTodas.setOnClickListener {
            mostrarTodo = !mostrarTodo
            refrescar()
        }

        // Banner: lleva a las tortas
        findViewById<View>(R.id.btnBannerVer).setOnClickListener {
            categoriaActual = "Tortas"
            mostrarTodo = false
            refrescar()
            irALosChips()
        }

        // Carrito (encabezado y menú): todavía no existe la pantalla del carrito
        findViewById<View>(R.id.btnCarrito).setOnClickListener { abrirCarrito() }
        findViewById<View>(R.id.navCarrito).setOnClickListener { abrirCarrito() }

        // Menú inferior
        findViewById<View>(R.id.navInicio).setOnClickListener { scroll.smoothScrollTo(0, 0) }
        findViewById<View>(R.id.navCategorias).setOnClickListener { irALosChips() }
        findViewById<View>(R.id.navPerfil).setOnClickListener {
            startActivity(android.content.Intent(this, PerfilCompradorActivity::class.java))
        }
    }
    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.tvSaludo).text =
            "Hola, ${PerfilCompradorRepositorio.obtener().nombre.substringBefore(" ")}"
        refrescar()
    }

    private fun refrescar() {
        val texto = normalizarTexto(etBuscar.text.toString())
        val buscando = texto.isNotEmpty()
        val enInicio = categoriaActual == null && !buscando

        chips.forEach { (chip, categoria) ->
            if (categoria == categoriaActual) {
                chip.setBackgroundResource(R.drawable.bg_users_chip_selected)
                chip.setTextColor(0xFFFFFFFF.toInt())
            } else {
                chip.setBackgroundResource(R.drawable.bg_users_chip)
                chip.setTextColor(0xFF5D477F.toInt())
            }
        }

        val base = if (enInicio && !mostrarTodo) {
            InventarioRepositorio.obtenerFavoritosDeSaso() // solo los favoritos de la casa
        } else {
            InventarioRepositorio.obtenerCatalogo() // todos los disponibles
        }
        val lista = base.filter { p ->
            (categoriaActual == null || p.categoria == categoriaActual) &&
                    (texto.isEmpty() ||
                            normalizarTexto(p.nombre).contains(texto) ||
                            normalizarTexto(p.categoria).contains(texto))
        }
        adapter.submit(lista)
        tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE

        tvTituloSeccion.text = when {
            buscando -> "Resultados"
            categoriaActual != null -> categoriaActual
            mostrarTodo -> "Todo el catálogo"
            else -> "Favoritos de Saso"
        }
        tvVerTodas.visibility = if (enInicio) View.VISIBLE else View.GONE
        tvVerTodas.text = if (mostrarTodo) "Ver menos" else "Ver todas"
        bannerEspecial.visibility = if (enInicio) View.VISIBLE else View.GONE

        actualizarGlobito()
    }

    private fun actualizarGlobito() {
        val total = CarritoRepositorio.cantidadTotal()
        tvBadgeCarrito.visibility = if (total == 0) View.GONE else View.VISIBLE
        tvBadgeCarrito.text = if (total > 9) "9+" else total.toString()
    }

    private fun abrirDetalle(p: ProductoInventario) {
        val intent = android.content.Intent(this, ProductoDetalleActivity::class.java)
        intent.putExtra(ProductoDetalleActivity.EXTRA_PRODUCTO_ID, p.id)
        startActivity(intent)
    }
    private fun abrirCarrito() {
        startActivity(android.content.Intent(this, CarritoCompradorActivity::class.java))
    }

    private fun agregarAlCarrito(p: ProductoInventario) {
        val mensaje = when (CarritoRepositorio.agregar(p.id)) {
            ResultadoAgregar.AGREGADO -> "${p.nombre} agregado al carrito"
            ResultadoAgregar.AGOTADO -> "${p.nombre} está agotado"
            ResultadoAgregar.LIMITE_DE_STOCK -> "Solo hay ${p.stock} disponibles de ${p.nombre}"
            ResultadoAgregar.NO_DISPONIBLE -> "Este producto ya no está disponible"
        }
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        actualizarGlobito()
    }

    private fun irALosChips() {
        scroll.post {
            val margen = (8 * resources.displayMetrics.density).toInt()
            scroll.smoothScrollTo(0, scrollChips.top - margen)
        }
    }

    private fun proximamente() {
        Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
    }
}