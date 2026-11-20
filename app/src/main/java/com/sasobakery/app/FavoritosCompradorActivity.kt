package com.sasobakery.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration

// productos que el comprador marco como favoritos
class FavoritosCompradorActivity : AppCompatActivity() {
    private val adapter = CatalogoAdapter(
        onProductoClick = { abrirDetalle(it) },
        onAgregarClick = { agregarAlCarrito(it) },
        onFavoritoClick = { FavoritosRepositorio.alternar(it.id) }
    )

    private lateinit var tvVacio: View
    private var registro: ListenerRegistration? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_favoritos_comprador)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        tvVacio = findViewById(R.id.tvVacio)
        findViewById<View>(R.id.btnAtras).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvFavoritos)
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = adapter
    }

    // al volver se actualiza por si quito un favorito en otra pantalla
    override fun onResume() {
        super.onResume()
        refrescar()
    }

    // si cambia el catalogo se actualiza la lista
    override fun onStart() {
        super.onStart()
        registro = InventarioRepositorio.escuchar(
            alCambiar = { refrescar() },
            alFallar = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        )
    }

    override fun onStop() {
        super.onStop()
        registro?.remove()
        registro = null
    }

    // solo favoritos que sigan disponibles
    private fun refrescar() {
        val lista = InventarioRepositorio.obtenerCatalogo()
            .filter { FavoritosRepositorio.esFavorito(it.id) }
        adapter.submit(lista)
        tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
    }

    // abre el detalle del producto
    private fun abrirDetalle(p: ProductoInventario) {
        val intent = Intent(this, ProductoDetalleActivity::class.java)
        intent.putExtra(ProductoDetalleActivity.EXTRA_PRODUCTO_ID, p.id)
        startActivity(intent)
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
    }
}