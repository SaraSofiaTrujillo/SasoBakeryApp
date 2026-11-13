package com.sasobakery.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

enum class FiltroStock(val etiqueta: String) {
    TODOS("Todos los estados"),
    BAJO("Stock bajo"),
    AGOTADO("Agotados")
}

class ProductosInventarioActivity : AppCompatActivity() {

    private var productos: List<ProductoInventario> = InventarioRepositorio.obtenerTodos()

    private val adapter = InventarioAdapter { producto -> abrirFormulario(producto.id) }
    private var categoriaActual: String? = null // null = Todos
    private var filtroStock = FiltroStock.TODOS

    private lateinit var etBuscar: EditText
    private lateinit var btnFiltro: View
    private lateinit var bannerStockBajo: View
    private lateinit var tvAvisoTitulo: TextView
    private lateinit var tvVacio: TextView
    private lateinit var chips: List<Pair<TextView, String?>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_productos_inventario)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        val rv = findViewById<RecyclerView>(R.id.rvInventario)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        etBuscar = findViewById(R.id.etBuscar)
        btnFiltro = findViewById(R.id.btnFiltro)
        bannerStockBajo = findViewById(R.id.bannerStockBajo)
        tvAvisoTitulo = findViewById(R.id.tvAvisoTitulo)
        tvVacio = findViewById(R.id.tvVacio)

        etBuscar.doAfterTextChanged { refrescar() }

        // Chips de categoría (null = Todos)
        chips = listOf(
            findViewById<TextView>(R.id.chipTodos) to null,
            findViewById<TextView>(R.id.chipTortas) to "Tortas",
            findViewById<TextView>(R.id.chipPostres) to "Postres",
            findViewById<TextView>(R.id.chipGalletas) to "Galletas"
        )
        chips.forEach { (chip, categoria) ->
            chip.setOnClickListener {
                categoriaActual = categoria
                refrescar()
            }
        }

        btnFiltro.setOnClickListener { mostrarMenuFiltro() }

        bannerStockBajo.setOnClickListener {
            filtroStock = FiltroStock.BAJO
            refrescar()
        }

        findViewById<View>(R.id.btnNuevo).setOnClickListener { abrirFormulario(0) }

        findViewById<View>(R.id.navResumen).setOnClickListener { finish() }
        // "Pedidos" abre la gestión de pedidos y "Más" abre el perfil del vendedor
        findViewById<View>(R.id.navPedidos).setOnClickListener {
            startActivity(Intent(this, PedidosVendedorActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navMas).setOnClickListener {
            startActivity(Intent(this, PerfilVendedorActivity::class.java))
            finish()
        }
        refrescar()
    }

    override fun onResume() {
        super.onResume()
        productos = InventarioRepositorio.obtenerTodos()
        refrescar()
    }

    private fun abrirFormulario(id: Int) {
        val intent = Intent(this, ProductoFormularioActivity::class.java)
        intent.putExtra(ProductoFormularioActivity.EXTRA_PRODUCTO_ID, id)
        startActivity(intent)
    }


    private fun cumpleStock(p: ProductoInventario): Boolean = when (filtroStock) {
        FiltroStock.TODOS -> true
        FiltroStock.BAJO -> p.nivel == NivelStock.BAJO
        FiltroStock.AGOTADO -> p.nivel == NivelStock.AGOTADO
    }

    private fun mostrarMenuFiltro() {
        val menu = PopupMenu(this, btnFiltro)
        FiltroStock.values().forEachIndexed { indice, filtro ->
            val titulo = if (filtro == filtroStock) "✓ ${filtro.etiqueta}" else filtro.etiqueta
            menu.menu.add(0, indice, indice, titulo)
        }
        menu.setOnMenuItemClickListener { opcion ->
            filtroStock = FiltroStock.values()[opcion.itemId]
            refrescar()
            true
        }
        menu.show()
    }

    private fun refrescar() {

        chips.forEach { (chip, categoria) ->
            val cantidad = if (categoria == null) {
                productos.size
            } else {
                productos.count { it.categoria == categoria }
            }
            chip.text = "${categoria ?: "Todos"} $cantidad"

            if (categoria == categoriaActual) {
                chip.setBackgroundResource(R.drawable.bg_users_chip_selected)
                chip.setTextColor(0xFFFFFFFF.toInt())
            } else {
                chip.setBackgroundResource(R.drawable.bg_users_chip)
                chip.setTextColor(0xFF5D477F.toInt())
            }
        }

        val bajos = productos.count { it.nivel == NivelStock.BAJO }
        bannerStockBajo.visibility = if (bajos == 0) View.GONE else View.VISIBLE
        tvAvisoTitulo.text =
            if (bajos == 1) "1 producto con stock bajo" else "$bajos productos con stock bajo"

        btnFiltro.setBackgroundResource(
            if (filtroStock == FiltroStock.TODOS) R.drawable.bg_users_input
            else R.drawable.bg_catalogo_manage
        )

        val texto = etBuscar.text.toString().trim().lowercase()
        val lista = productos.filter { p ->
            (categoriaActual == null || p.categoria == categoriaActual) &&
                    cumpleStock(p) &&
                    (texto.isEmpty() ||
                            p.nombre.lowercase().contains(texto) ||
                            p.categoria.lowercase().contains(texto))
        }
        adapter.submit(lista)
        tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun proximamente() {
        Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
    }
}