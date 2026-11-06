package com.sasobakery.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ProductoDetalleActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PRODUCTO_ID = "producto_id"
    }

    private var idProducto = 0
    private var cantidad = 1

    private lateinit var ivFoto: ImageView
    private lateinit var ivCorazon: ImageView
    private lateinit var tvAgotado: TextView
    private lateinit var tvCategoria: TextView
    private lateinit var tvNombre: TextView
    private lateinit var tvDescripcion: TextView
    private lateinit var tvPorciones: TextView
    private lateinit var tvStockEstado: TextView
    private lateinit var tvEnCarrito: TextView
    private lateinit var tvCantidad: TextView
    private lateinit var btnMenos: View
    private lateinit var btnMas: View
    private lateinit var btnAgregarCarrito: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        idProducto = intent.getIntExtra(EXTRA_PRODUCTO_ID, 0)
        val producto = InventarioRepositorio.buscarPorId(idProducto)
        if (producto == null || !producto.disponible) {
            Toast.makeText(this, "Este producto ya no está disponible", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_producto_detalle)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        ivFoto = findViewById(R.id.ivFoto)
        ivCorazon = findViewById(R.id.ivCorazon)
        tvAgotado = findViewById(R.id.tvAgotado)
        tvCategoria = findViewById(R.id.tvCategoria)
        tvNombre = findViewById(R.id.tvNombre)
        tvDescripcion = findViewById(R.id.tvDescripcion)
        tvPorciones = findViewById(R.id.tvPorciones)
        tvStockEstado = findViewById(R.id.tvStockEstado)
        tvEnCarrito = findViewById(R.id.tvEnCarrito)
        tvCantidad = findViewById(R.id.tvCantidad)
        btnMenos = findViewById(R.id.btnMenos)
        btnMas = findViewById(R.id.btnMas)
        btnAgregarCarrito = findViewById(R.id.btnAgregarCarrito)

        findViewById<View>(R.id.btnAtras).setOnClickListener { finish() }

        findViewById<View>(R.id.btnFavorito).setOnClickListener {
            FavoritosRepositorio.alternar(idProducto)
            actualizarCorazon()
        }

        btnMenos.setOnClickListener {
            if (cantidad > 1) {
                cantidad--
                actualizarVista()
            }
        }

        btnMas.setOnClickListener {
            val p = InventarioRepositorio.buscarPorId(idProducto) ?: return@setOnClickListener
            val agregables = CarritoRepositorio.unidadesAgregables(p.id)
            if (cantidad < agregables) {
                cantidad++
                actualizarVista()
            } else {
                mensaje(mensajeLimite(p))
            }
        }

        btnAgregarCarrito.setOnClickListener { agregarAlCarrito() }

        actualizarVista()
    }

    private fun actualizarVista() {
        val p = InventarioRepositorio.buscarPorId(idProducto)
        if (p == null) {
            finish()
            return
        }

        ivFoto.mostrarFotoProducto(p, 80)
        val sinFoto = p.imagenUri == null && p.imagen == null
        ivFoto.scaleType =
            if (sinFoto) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP

        tvCategoria.text = p.categoria.uppercase()
        tvNombre.text = p.nombre
        tvDescripcion.text = p.descripcion
        tvDescripcion.visibility = if (p.descripcion.isBlank()) View.GONE else View.VISIBLE
        tvPorciones.text = if (p.porciones == 1) "1 porción" else "${p.porciones} porciones"

        tvStockEstado.text = when (p.nivel) {
            NivelStock.DISPONIBLE -> p.textoStock
            NivelStock.BAJO -> "${p.textoStock} · pocas unidades"
            NivelStock.AGOTADO -> "Agotado"
        }
        tvStockEstado.setTextColor(Color.parseColor(p.nivel.color))
        tvAgotado.visibility = if (p.nivel == NivelStock.AGOTADO) View.VISIBLE else View.GONE

        actualizarCorazon()

        val enCarrito = CarritoRepositorio.cantidadDe(p.id)
        if (enCarrito > 0) {
            tvEnCarrito.visibility = View.VISIBLE
            tvEnCarrito.text =
                if (enCarrito == 1) "Ya tienes 1 en tu carrito" else "Ya tienes $enCarrito en tu carrito"
        } else {
            tvEnCarrito.visibility = View.GONE
        }

        val agregables = CarritoRepositorio.unidadesAgregables(p.id)
        cantidad = if (agregables == 0) 0 else cantidad.coerceIn(1, agregables)
        tvCantidad.text = cantidad.toString()
        btnMenos.alpha = if (cantidad > 1) 1f else 0.35f
        btnMas.alpha = if (cantidad in 1 until agregables) 1f else 0.35f

        if (agregables == 0) {
            btnAgregarCarrito.text =
                if (p.stock <= 0) "Agotado" else "Ya tienes todo el stock en tu carrito"
            btnAgregarCarrito.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor("#C9C1D6"))
        } else {
            btnAgregarCarrito.text = "Agregar · ${formatearCop(p.precio * cantidad)}"
            btnAgregarCarrito.backgroundTintList = null
        }
    }

    private fun actualizarCorazon() {
        val esFavorito = FavoritosRepositorio.esFavorito(idProducto)
        ivCorazon.setImageResource(
            if (esFavorito) R.drawable.ic_heart_filled else R.drawable.ic_heart
        )
        ivCorazon.imageTintList = ColorStateList.valueOf(
            Color.parseColor(if (esFavorito) "#C2485F" else "#5D477F")
        )
    }


    private fun agregarAlCarrito() {
        val p = InventarioRepositorio.buscarPorId(idProducto) ?: return
        val unidades = cantidad.coerceAtLeast(1)

        when (CarritoRepositorio.agregar(p.id, unidades)) {
            ResultadoAgregar.AGREGADO -> {
                mensaje(
                    if (unidades == 1) "${p.nombre} agregado al carrito"
                    else "$unidades unidades de ${p.nombre} agregadas al carrito"
                )
                cantidad = 1
                actualizarVista()
            }
            ResultadoAgregar.AGOTADO -> mensaje("${p.nombre} está agotado")
            ResultadoAgregar.LIMITE_DE_STOCK -> mensaje(mensajeLimite(p))
            ResultadoAgregar.NO_DISPONIBLE -> mensaje("Este producto ya no está disponible")
        }
    }


    private fun mensajeLimite(p: ProductoInventario): String =
        if (p.stock <= 0) "${p.nombre} está agotado"
        else "Solo hay ${p.textoStock} de ${p.nombre}"

    private fun mensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }
}