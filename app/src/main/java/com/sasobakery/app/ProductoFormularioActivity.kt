package com.sasobakery.app

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.materialswitch.MaterialSwitch
import java.io.File
import java.io.FileOutputStream

// formulario para crear o editar un producto, lo usan admin y vendedor
class ProductoFormularioActivity : AppCompatActivity() {
    companion object {
        // id del producto a editar, si no llega es un producto nuevo
        const val EXTRA_PRODUCTO_ID = "producto_id"
    }

    // porciones que se pueden elegir
    private val opcionesPorciones = listOf(1, 2, 4, 6, 8, 10, 12, 16, 20)

    private var productoId = ""
    private var enCurso = false
    private var productoOriginal: ProductoInventario? = null
    private var categoriaActual = "Tortas"
    private var porcionesActual = 10
    // foto recien elegida en base64, si es null se deja la que ya tenia
    private var imagenNueva: String? = null

    private lateinit var ivFoto: ImageView
    private lateinit var etNombre: EditText
    private lateinit var etDescripcion: EditText
    private lateinit var etPrecio: EditText
    private lateinit var tvCategoria: TextView
    private lateinit var switchDisponible: MaterialSwitch
    private lateinit var switchDestacado: MaterialSwitch
    private lateinit var etStock: EditText
    private lateinit var tvPorciones: TextView

    // abre la galeria y procesa la foto en otro hilo para no congelar la pantalla
    private val elegirFoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            Toast.makeText(this, "Procesando foto…", Toast.LENGTH_SHORT).show()
            Thread {
                val base64 = ImagenBase64.desdeUri(this, uri)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (base64 != null) {
                        imagenNueva = base64
                        mostrarFoto()
                    } else {
                        Toast.makeText(this, "No se pudo cargar la foto", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_producto_formulario)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        ivFoto = findViewById(R.id.ivFoto)
        etNombre = findViewById(R.id.etNombre)
        etDescripcion = findViewById(R.id.etDescripcion)
        etPrecio = findViewById(R.id.etPrecio)
        tvCategoria = findViewById(R.id.tvCategoria)
        switchDisponible = findViewById(R.id.switchDisponible)
        switchDestacado = findViewById(R.id.switchDestacado)
        etStock = findViewById(R.id.etStock)
        tvPorciones = findViewById(R.id.tvPorciones)

        colorearInterruptor()

        // con id edita, sin id crea uno nuevo
        productoId = intent.getStringExtra(EXTRA_PRODUCTO_ID).orEmpty()
        if (productoId.isEmpty()) {
            prepararNuevo()
        } else {
            val existente = InventarioRepositorio.buscarPorId(productoId)
            if (existente == null) {
                Toast.makeText(this, "Ese producto ya no existe", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            productoOriginal = existente
            llenarCampos(existente)
        }
        mostrarFoto()

        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }
        findViewById<View>(R.id.btnGuardarTop).setOnClickListener { guardar() }
        findViewById<View>(R.id.btnGuardar).setOnClickListener { guardar() }
        findViewById<View>(R.id.btnCambiarFoto).setOnClickListener { elegirFoto.launch("image/*") }
        tvCategoria.setOnClickListener { mostrarMenuCategorias() }
        tvPorciones.setOnClickListener { mostrarMenuPorciones() }
    }

    // pone los datos del producto en el formulario para editarlo
    private fun llenarCampos(p: ProductoInventario) {
        findViewById<TextView>(R.id.tvTitulo).text = "Editar producto"
        findViewById<TextView>(R.id.btnGuardar).text = "Guardar cambios"

        etNombre.setText(p.nombre)
        etDescripcion.setText(p.descripcion)
        etPrecio.setText(p.precio.toString())
        etStock.setText(p.stock.toString())
        switchDisponible.isChecked = p.disponible
        switchDestacado.isChecked = p.destacado

        categoriaActual = p.categoria
        tvCategoria.text = categoriaActual
        porcionesActual = p.porciones
        tvPorciones.text = textoPorciones(porcionesActual)
    }

    // valores iniciales para un producto nuevo
    private fun prepararNuevo() {
        findViewById<TextView>(R.id.tvTitulo).text = "Crear producto"
        findViewById<TextView>(R.id.btnGuardar).text = "Crear producto"

        switchDisponible.isChecked = true
        switchDestacado.isChecked = false
        categoriaActual = InventarioRepositorio.categorias.first()
        tvCategoria.text = categoriaActual
        porcionesActual = 10
        tvPorciones.text = textoPorciones(porcionesActual)
    }

    // colores de los switch, activo y apagado
    private fun colorearInterruptor() {
        val estados = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        listOf(switchDisponible, switchDestacado).forEach { interruptor ->
            interruptor.trackTintList = ColorStateList(
                estados,
                intArrayOf(0xFF7B5EA7.toInt(), 0xFFE6DDF5.toInt())
            )
            interruptor.thumbTintList = ColorStateList(
                estados,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFF8A8494.toInt())
            )
        }
    }

    // foto nueva, si no la que tenia, si no el icono por defecto
    private fun mostrarFoto() {
        ivFoto.mostrarFotoDesde(
            imagenNueva ?: productoOriginal?.imagenBase64,
            productoOriginal?.imagen,
            64
        )
    }

    // menu para elegir categoria, marca la actual con check
    private fun mostrarMenuCategorias() {
        val menu = PopupMenu(this, tvCategoria)
        InventarioRepositorio.categorias.forEachIndexed { indice, categoria ->
            val titulo = if (categoria == categoriaActual) "✓ $categoria" else categoria
            menu.menu.add(0, indice, indice, titulo)
        }
        menu.setOnMenuItemClickListener { opcion ->
            categoriaActual = InventarioRepositorio.categorias[opcion.itemId]
            tvCategoria.text = categoriaActual
            true
        }
        menu.show()
    }

    // menu para elegir porciones, marca la actual con check
    private fun mostrarMenuPorciones() {
        val menu = PopupMenu(this, tvPorciones)
        opcionesPorciones.forEachIndexed { indice, cantidad ->
            val texto = textoPorciones(cantidad)
            val titulo = if (cantidad == porcionesActual) "✓ $texto" else texto
            menu.menu.add(0, indice, indice, titulo)
        }
        menu.setOnMenuItemClickListener { opcion ->
            porcionesActual = opcionesPorciones[opcion.itemId]
            tvPorciones.text = textoPorciones(porcionesActual)
            true
        }
        menu.show()
    }

    private fun textoPorciones(cantidad: Int): String =
        if (cantidad == 1) "1 porción" else "$cantidad porciones"

    // valida nombre y precio y arma el producto para guardarlo
    private fun guardar() {
        if (enCurso) return

        val nombre = etNombre.text.toString().trim()
        if (nombre.isEmpty()) {
            etNombre.error = "Escribe el nombre del producto"
            etNombre.requestFocus()
            return
        }

        val precio = etPrecio.text.toString().trim().toIntOrNull() ?: 0
        if (precio <= 0) {
            etPrecio.error = "Escribe un precio mayor a 0"
            etPrecio.requestFocus()
            return
        }

        val stock = etStock.text.toString().trim().toIntOrNull() ?: 0

        val producto = ProductoInventario(
            nombre = nombre,
            categoria = categoriaActual,
            precio = precio,
            stock = stock,
            imagen = productoOriginal?.imagen,
            id = productoId,
            descripcion = etDescripcion.text.toString().trim(),
            disponible = switchDisponible.isChecked,
            destacado = switchDestacado.isChecked,
            porciones = porcionesActual,
            imagenBase64 = imagenNueva ?: productoOriginal?.imagenBase64
        )

        // se guarda antes porque el texto del mensaje depende de si era nuevo
        val esNuevo = productoId.isEmpty()
        cambiarEstadoCarga(true)
        InventarioRepositorio.guardar(producto) { error ->
            if (!isFinishing && !isDestroyed) {
                cambiarEstadoCarga(false)
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                } else {
                    val mensaje = if (esNuevo) "Producto creado" else "Cambios guardados"
                    Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    // bloquea los botones mientras guarda
    private fun cambiarEstadoCarga(cargando: Boolean) {
        enCurso = cargando
        val boton = findViewById<TextView>(R.id.btnGuardar)
        boton.isEnabled = !cargando
        findViewById<View>(R.id.btnGuardarTop).isEnabled = !cargando
        boton.text = when {
            cargando -> "Guardando…"
            productoId.isEmpty() -> "Crear producto"
            else -> "Guardar cambios"
        }
    }
}