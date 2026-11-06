package com.sasobakery.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Carrito del comprador. Todo sale de CarritoRepositorio (nada duplicado).
class CarritoCompradorActivity : AppCompatActivity() {

    private var modoEdicion = false

    private val adapter = CarritoAdapter(
        onMas = { sumar(it) },
        onMenos = { restar(it) },
        onQuitar = { quitar(it) }
    )

    private lateinit var rvCarrito: RecyclerView
    private lateinit var btnEditar: TextView
    private lateinit var bannerReserva: View
    private lateinit var layoutVacio: View
    private lateinit var layoutResumen: View
    private lateinit var panelContinuar: View
    private lateinit var tvSubtotal: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvBadgeNav: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_carrito_comprador)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        rvCarrito = findViewById(R.id.rvCarrito)
        btnEditar = findViewById(R.id.btnEditar)
        bannerReserva = findViewById(R.id.bannerReserva)
        layoutVacio = findViewById(R.id.layoutVacio)
        layoutResumen = findViewById(R.id.layoutResumen)
        panelContinuar = findViewById(R.id.panelContinuar)
        tvSubtotal = findViewById(R.id.tvSubtotal)
        tvTotal = findViewById(R.id.tvTotal)
        tvBadgeNav = findViewById(R.id.tvBadgeNav)

        rvCarrito.layoutManager = LinearLayoutManager(this)
        rvCarrito.adapter = adapter

        // Editar / Listo
        btnEditar.setOnClickListener {
            modoEdicion = !modoEdicion
            refrescar()
        }

        // Todavía sin pantalla
        findViewById<View>(R.id.btnCupon).setOnClickListener { mensaje("Próximamente") }
        findViewById<View>(R.id.btnContinuar).setOnClickListener {
            startActivity(android.content.Intent(this, EntregaCompradorActivity::class.java))
        }
        findViewById<View>(R.id.navPerfil).setOnClickListener {
            startActivity(android.content.Intent(this, PerfilCompradorActivity::class.java))
        }

        // Volver al catálogo
        findViewById<View>(R.id.btnVerProductos).setOnClickListener { finish() }
        findViewById<View>(R.id.navInicio).setOnClickListener { finish() }
        findViewById<View>(R.id.navCategorias).setOnClickListener { finish() }
        // navCarrito: ya estamos en el carrito, no hace nada
    }

    override fun onResume() {
        super.onResume()
        refrescar()
    }

    // ---------- Acciones sobre los productos ----------

    private fun sumar(p: ProductoInventario) {
        when (CarritoRepositorio.agregar(p.id)) {
            ResultadoAgregar.AGREGADO -> Unit
            ResultadoAgregar.LIMITE_DE_STOCK -> mensaje("Solo hay ${p.textoStock} de ${p.nombre}")
            ResultadoAgregar.AGOTADO -> mensaje("${p.nombre} está agotado")
            ResultadoAgregar.NO_DISPONIBLE -> mensaje("Este producto ya no está disponible")
        }
        refrescar()
    }

    private fun restar(p: ProductoInventario) {
        val eraLaUltima = CarritoRepositorio.cantidadDe(p.id) <= 1
        CarritoRepositorio.disminuir(p.id)
        if (eraLaUltima) mensaje("${p.nombre} quitado del carrito")
        refrescar()
    }

    private fun quitar(p: ProductoInventario) {
        CarritoRepositorio.quitar(p.id)
        mensaje("${p.nombre} quitado del carrito")
        refrescar()
    }

    // ---------- Pantalla ----------

    /** Vuelve a dibujar todo con lo que haya en el carrito en este momento. */
    private fun refrescar() {
        val items = CarritoRepositorio.obtenerItems()
        val vacio = items.isEmpty()
        if (vacio) modoEdicion = false

        adapter.submit(items, modoEdicion)

        // Qué se ve según haya o no productos
        val conProductos = if (vacio) View.GONE else View.VISIBLE
        bannerReserva.visibility = conProductos
        rvCarrito.visibility = conProductos
        layoutResumen.visibility = conProductos
        panelContinuar.visibility = conProductos
        layoutVacio.visibility = if (vacio) View.VISIBLE else View.GONE
        btnEditar.visibility = if (vacio) View.INVISIBLE else View.VISIBLE
        btnEditar.text = if (modoEdicion) "Listo" else "Editar"

        // Totales en pesos colombianos
        val total = CarritoRepositorio.total()
        tvSubtotal.text = formatearCop(total)
        tvTotal.text = formatearCop(total)

        // Globito del menú inferior
        val unidades = CarritoRepositorio.cantidadTotal()
        tvBadgeNav.visibility = if (unidades == 0) View.GONE else View.VISIBLE
        tvBadgeNav.text = if (unidades > 9) "9+" else unidades.toString()
    }

    private fun mensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }
}