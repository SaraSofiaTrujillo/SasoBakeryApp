package com.sasobakery.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration

// categorias del admin con cuantos productos tiene cada una
class CategoriasActivity : AppCompatActivity() {
    // escucha de firestore, se activa en onStart y se quita en onStop
    private var registro: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_categorias)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        val rv = findViewById<RecyclerView>(R.id.rvCategorias)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = CategoriasAdapter(emptyList()) { proximamente() }

        // los botones de menu y nueva categoria todavia no estan hechos
        findViewById<View>(R.id.btnMenu).setOnClickListener { proximamente() }
        findViewById<View>(R.id.btnNuevaCategoria).setOnClickListener { proximamente() }

        findViewById<View>(R.id.tabProductos).setOnClickListener { abrirProductos() }

        findViewById<View>(R.id.navResumen).setOnClickListener { finish() }
        findViewById<View>(R.id.navPedidos).setOnClickListener {
            startActivity(Intent(this, PedidosActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navProductos).setOnClickListener { abrirProductos() }
        findViewById<View>(R.id.navUsuarios).setOnClickListener {
            startActivity(Intent(this, UsuariosRolesActivity::class.java))
            finish()
        }
    }

    // cuenta los productos por categoria en tiempo real
    override fun onStart() {
        super.onStart()
        registro = InventarioRepositorio.escuchar(
            alCambiar = { productos ->
                val categorias = InventarioRepositorio.categorias.map { nombre ->
                    Categoria(nombre, productos.count { it.categoria == nombre })
                }
                findViewById<RecyclerView>(R.id.rvCategorias).adapter =
                    CategoriasAdapter(categorias) { proximamente() }
            },
            alFallar = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        )
    }

    override fun onStop() {
        super.onStop()
        registro?.remove()
        registro = null
    }

    // lista de productos, esta reemplaza a la actual
    private fun abrirProductos() {
        startActivity(Intent(this, ProductosCategoriasActivity::class.java))
        finish()
    }

    private fun proximamente() {
        Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
    }
}