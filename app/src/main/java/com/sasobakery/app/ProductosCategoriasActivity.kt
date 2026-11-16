package com.sasobakery.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import com.google.firebase.firestore.ListenerRegistration

class ProductosCategoriasActivity : AppCompatActivity() {

    // Lista real: se llena con lo que llega de Firestore
    private var productos: List<Producto> = emptyList()
    private var registro: ListenerRegistration? = null

    private val adapter = ProductosAdapter()
    private lateinit var etBuscar: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_productos_categorias)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        val rv = findViewById<RecyclerView>(R.id.rvProductos)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        etBuscar = findViewById(R.id.etBuscar)
        etBuscar.doAfterTextChanged { aplicarFiltro() }

        findViewById<View>(R.id.btnMenu).setOnClickListener { proximamente() }
        findViewById<View>(R.id.btnCrear).setOnClickListener { proximamente() }

        findViewById<View>(R.id.tabCategorias).setOnClickListener { abrirCategorias() }
        findViewById<View>(R.id.btnGestionarCategorias).setOnClickListener { abrirCategorias() }
        // "Resumen" vuelve al panel del administrador que ya estaba abierto
        findViewById<View>(R.id.navResumen).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        findViewById<View>(R.id.navPedidos).setOnClickListener {
            startActivity(Intent(this, PedidosActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navUsuarios).setOnClickListener {
            startActivity(Intent(this, UsuariosRolesActivity::class.java))
            finish()
        }

        aplicarFiltro()
    }

    // Escucha Firestore mientras la pantalla se ve: si el vendedor crea, edita o
    // desactiva un producto, esta lista y los contadores se actualizan solos.
    override fun onStart() {
        super.onStart()
        registro = InventarioRepositorio.escuchar(
            alCambiar = { lista ->
                productos = lista.map { aProductoAdmin(it) }
                actualizarEstadisticas(lista)
                aplicarFiltro()
            },
            alFallar = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        )
    }

    override fun onStop() {
        super.onStop()
        registro?.remove()
        registro = null
    }

    // Convierte un producto de Firestore en el que muestra esta lista
    private fun aProductoAdmin(p: ProductoInventario) = Producto(
        nombre = p.nombre,
        categoria = p.categoria,
        precio = p.precio,
        estado = when {
            !p.disponible -> EstadoProducto.DESACTIVADO
            p.stock <= 0 -> EstadoProducto.AGOTADO
            p.stock <= 5 -> EstadoProducto.STOCK_BAJO // mismo criterio que el inventario
            else -> EstadoProducto.ACTIVO
        },
        imagen = p.imagen,
        imagenBase64 = p.imagenBase64
    )

    // Activos = visibles en el catálogo; Ocultos = desactivados;
    // Stock bajo = visibles con 5 unidades o menos (incluye agotados)
    private fun actualizarEstadisticas(lista: List<ProductoInventario>) {
        findViewById<TextView>(R.id.tvActivos).text = lista.count { it.disponible }.toString()
        findViewById<TextView>(R.id.tvOcultos).text = lista.count { !it.disponible }.toString()
        findViewById<TextView>(R.id.tvStockBajo).text =
            lista.count { it.disponible && it.stock <= 5 }.toString()
    }

    private fun abrirCategorias() {
        startActivity(Intent(this, CategoriasActivity::class.java))
        finish()
    }

    private fun aplicarFiltro() {
        val texto = etBuscar.text.toString().trim().lowercase()
        val lista = productos.filter { p ->
            texto.isEmpty() ||
                    p.nombre.lowercase().contains(texto) ||
                    p.categoria.lowercase().contains(texto)
        }
        adapter.submit(lista)
    }

    private fun proximamente() {
        Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
    }
}