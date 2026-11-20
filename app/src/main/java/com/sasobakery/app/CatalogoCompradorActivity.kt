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
import com.google.firebase.firestore.ListenerRegistration

// pantalla principal del comprador, catalogo con busqueda, categorias y carrito
class CatalogoCompradorActivity : AppCompatActivity() {
    // nombre de relleno, el real se pone en onResume
    private val nombreComprador = "Valentina"

    private val adapter = CatalogoAdapter(
        onProductoClick = { producto -> abrirDetalle(producto) },
        onAgregarClick = { producto -> agregarAlCarrito(producto) },
        onFavoritoClick = { producto -> FavoritosRepositorio.alternar(producto.id) }
    )

    private var categoriaActual: String? = null
    // true cuando eligio ver todo el catalogo en vez de los favoritos
    private var mostrarTodo = false
    private var registro: ListenerRegistration? = null
    // escucha de los datos del negocio, asi la tienda ya los tiene para los pedidos
    private var registroNegocio: ListenerRegistration? = null

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

        // filtra mientras se escribe
        etBuscar.doAfterTextChanged { refrescar() }

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

        tvVerTodas.setOnClickListener {
            mostrarTodo = !mostrarTodo
            refrescar()
        }

        // el banner lleva a la categoria tortas
        findViewById<View>(R.id.btnBannerVer).setOnClickListener {
            categoriaActual = "Tortas"
            mostrarTodo = false
            refrescar()
            irALosChips()
        }

        findViewById<View>(R.id.btnCarrito).setOnClickListener { abrirCarrito() }
        findViewById<View>(R.id.navCarrito).setOnClickListener { abrirCarrito() }

        findViewById<View>(R.id.navInicio).setOnClickListener { scroll.smoothScrollTo(0, 0) }
        findViewById<View>(R.id.navPerfil).setOnClickListener {
            startActivity(android.content.Intent(this, PerfilCompradorActivity::class.java))
        }
    }
    // al volver se recarga saludo, perfil y carrito de la cuenta
    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.tvSaludo).text =
            "Hola, ${PerfilCompradorRepositorio.obtener().nombre.substringBefore(" ")}"
        refrescar()
        PerfilCompradorRepositorio.cargarDeSesion {
            if (!isFinishing && !isDestroyed) {
                findViewById<TextView>(R.id.tvSaludo).text =
                    "Hola, ${PerfilCompradorRepositorio.obtener().nombre.substringBefore(" ")}"
            }
        }
        CarritoRepositorio.cargarDeSesion {
            if (!isFinishing && !isDestroyed) refrescar()
        }
    }

    // productos en tiempo real, cada cambio revisa el carrito contra el stock
    override fun onStart() {
        super.onStart()
        registro = InventarioRepositorio.escuchar(
            alCambiar = {
                CarritoRepositorio.reconciliar()
                refrescar()
            },
            alFallar = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        )
        registroNegocio = NegocioRepositorio.escuchar(
            alCambiar = { },
            alFallar = { }
        )
    }

    override fun onStop() {
        super.onStop()
        registro?.remove()
        registro = null
        registroNegocio?.remove()
        registroNegocio = null
    }

    // arma la lista segun busqueda, categoria y si esta en el inicio
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

        // en el inicio salen solo los favoritos, en otro caso el catalogo completo
        val base = if (enInicio && !mostrarTodo) {
            InventarioRepositorio.obtenerFavoritosDeSaso()
        } else {
            InventarioRepositorio.obtenerCatalogo()
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

    // numero del carrito, mas de 9 se muestra 9+
    private fun actualizarGlobito() {
        val total = CarritoRepositorio.cantidadTotal()
        tvBadgeCarrito.visibility = if (total == 0) View.GONE else View.VISIBLE
        tvBadgeCarrito.text = if (total > 9) "9+" else total.toString()
    }

    // abre el detalle del producto
    private fun abrirDetalle(p: ProductoInventario) {
        val intent = android.content.Intent(this, ProductoDetalleActivity::class.java)
        intent.putExtra(ProductoDetalleActivity.EXTRA_PRODUCTO_ID, p.id)
        startActivity(intent)
    }
    private fun abrirCarrito() {
        startActivity(android.content.Intent(this, CarritoCompradorActivity::class.java))
    }

    // agrega una unidad y avisa el resultado
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

    // baja el scroll hasta las categorias
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