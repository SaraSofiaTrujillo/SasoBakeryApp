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
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ImageView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// lista de productos del admin con resumen, busqueda y detalle
class ProductosCategoriasActivity : AppCompatActivity() {
    private var productos: List<Producto> = emptyList()
    // escucha de firestore, se activa en onStart y se quita en onStop
    private var registro: ListenerRegistration? = null

    private val adapter = ProductosAdapter(onClick = { abrirDetalle(it) })
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

        // estos dos botones todavia no estan hechos
        findViewById<View>(R.id.btnMenu).setOnClickListener { proximamente() }
        findViewById<View>(R.id.btnCrear).setOnClickListener { proximamente() }

        findViewById<View>(R.id.tabCategorias).setOnClickListener { abrirCategorias() }
        findViewById<View>(R.id.btnGestionarCategorias).setOnClickListener { abrirCategorias() }
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

    // productos en tiempo real
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

    // calcula el estado de cada producto, stock de 5 o menos es bajo
    private fun aProductoAdmin(p: ProductoInventario) = Producto(
        nombre = p.nombre,
        categoria = p.categoria,
        precio = p.precio,
        estado = when {
            !p.disponible -> EstadoProducto.DESACTIVADO
            p.stock <= 0 -> EstadoProducto.AGOTADO
            p.stock <= 5 -> EstadoProducto.STOCK_BAJO
            else -> EstadoProducto.ACTIVO
        },
        imagen = p.imagen,
        imagenBase64 = p.imagenBase64,
        id = p.id,
        stock = p.stock
    )

    // contadores de arriba
    private fun actualizarEstadisticas(lista: List<ProductoInventario>) {
        findViewById<TextView>(R.id.tvActivos).text = lista.count { it.disponible }.toString()
        findViewById<TextView>(R.id.tvOcultos).text = lista.count { !it.disponible }.toString()
        findViewById<TextView>(R.id.tvStockBajo).text =
            lista.count { it.disponible && it.stock <= 5 }.toString()
    }

    // pantalla de categorias, esta reemplaza a la actual
    private fun abrirCategorias() {
        startActivity(Intent(this, CategoriasActivity::class.java))
        finish()
    }

    // busca por nombre o categoria
    private fun aplicarFiltro() {
        val texto = etBuscar.text.toString().trim().lowercase()
        val lista = productos.filter { p ->
            texto.isEmpty() ||
                    p.nombre.lowercase().contains(texto) ||
                    p.categoria.lowercase().contains(texto)
        }
        adapter.submit(lista)
    }

    // hoja con el detalle del producto, desde ahi se edita o se activa y desactiva
    private fun abrirDetalle(p: Producto) {
        val producto = InventarioRepositorio.buscarPorId(p.id) ?: return
        val dialogo = BottomSheetDialog(this)
        val vista = layoutInflater.inflate(R.layout.dialog_producto_admin, null)

        vista.findViewById<ImageView>(R.id.ivPaFoto)
            .mostrarFotoDesde(producto.imagenBase64, producto.imagen, 12)
        vista.findViewById<TextView>(R.id.tvPaNombre).text = producto.nombre
        vista.findViewById<TextView>(R.id.tvPaCategoria).text = producto.categoria

        val estado = vista.findViewById<TextView>(R.id.tvPaEstado)
        // colores del estado
        val (fondo, texto) = when (p.estado) {
            EstadoProducto.ACTIVO -> "#DDF2E6" to "#3F7D5A"
            EstadoProducto.STOCK_BAJO -> "#FCEBD0" to "#B7791F"
            EstadoProducto.AGOTADO -> "#FBE0E0" to "#B23A3A"
            EstadoProducto.DESACTIVADO -> "#EFEAF5" to "#8A8494"
        }
        estado.text = p.estado.etiqueta
        estado.backgroundTintList = ColorStateList.valueOf(Color.parseColor(fondo))
        estado.setTextColor(Color.parseColor(texto))

        vista.findViewById<TextView>(R.id.tvPaPrecio).text =
            "Precio: ${formatearCop(producto.precio)}"
        vista.findViewById<TextView>(R.id.tvPaStock).text =
            "Stock: ${producto.stock} unidades"
        vista.findViewById<TextView>(R.id.tvPaPorciones).text =
            "Porciones: ${producto.porciones}" + if (producto.destacado) " · Destacado" else ""
        vista.findViewById<TextView>(R.id.tvPaVisibilidad).text =
            if (producto.disponible) "Visible en el catálogo" else "Oculto del catálogo"
        vista.findViewById<TextView>(R.id.tvPaDescripcion).text =
            producto.descripcion.ifBlank { "Sin descripción" }

        vista.findViewById<View>(R.id.btnPaEditar).setOnClickListener {
            dialogo.dismiss()
            val intent = Intent(this, ProductoFormularioActivity::class.java)
            intent.putExtra(ProductoFormularioActivity.EXTRA_PRODUCTO_ID, producto.id)
            startActivity(intent)
        }

        val btnVisibilidad = vista.findViewById<TextView>(R.id.btnPaCambiarVisibilidad)
        btnVisibilidad.text = if (producto.disponible) "Desactivar" else "Activar"
        btnVisibilidad.setOnClickListener {
            dialogo.dismiss()
            if (producto.disponible) confirmarDesactivar(producto)
            else cambiarDisponibilidad(producto, true)
        }

        dialogo.setContentView(vista)
        dialogo.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialogo.behavior.skipCollapsed = true
        dialogo.show()
    }

    // avisa que deja de verse en el catalogo, los pedidos hechos no cambian
    private fun confirmarDesactivar(p: ProductoInventario) {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Desactivar ${p.nombre}?")
            .setMessage(
                "Dejará de verse en el catálogo del comprador. Los pedidos que ya se " +
                        "hicieron no cambian, y puedes activarlo de nuevo cuando quieras."
            )
            .setNegativeButton("Volver", null)
            .setPositiveButton("Desactivar") { _, _ -> cambiarDisponibilidad(p, false) }
            .show()
    }
    // muestra u oculta el producto en el catalogo
    private fun cambiarDisponibilidad(p: ProductoInventario, disponible: Boolean) {
        InventarioRepositorio.cambiarDisponibilidad(p.id, disponible) { error ->
            if (isFinishing || isDestroyed) return@cambiarDisponibilidad
            val mensaje = error
                ?: if (disponible) "${p.nombre} activado" else "${p.nombre} desactivado"
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        }
    }

    private fun proximamente() {
        Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
    }
}