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

class ProductosCategoriasActivity : AppCompatActivity() {

    // Datos de ejemplo en pesos colombianos (luego vendrán de la base de datos).
    // Para usar una foto: copia el PNG a res/drawable (ej. prod_torta_aurora.png)
    // y agrega: imagen = R.drawable.prod_torta_aurora
    private val productos = listOf(
        Producto("Torta Aurora", "Tortas", 68000, EstadoProducto.ACTIVO),
        Producto("Cheesecake fresa", "Postres", 28000, EstadoProducto.ACTIVO),
        Producto("Brownie intenso", "Brownies", 10000, EstadoProducto.STOCK_BAJO),
        Producto("Galletas de casa", "Galletas", 14000, EstadoProducto.ACTIVO)
    )

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

        // Botones que aún no tienen pantalla
        findViewById<View>(R.id.btnMenu).setOnClickListener { proximamente() }
        findViewById<View>(R.id.btnCrear).setOnClickListener { proximamente() }

        // Pestaña Categorías y "Gestionar categorías" abren la pantalla de categorías
        findViewById<View>(R.id.tabCategorias).setOnClickListener { abrirCategorias() }
        findViewById<View>(R.id.btnGestionarCategorias).setOnClickListener { abrirCategorias() }

        // Menú inferior
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