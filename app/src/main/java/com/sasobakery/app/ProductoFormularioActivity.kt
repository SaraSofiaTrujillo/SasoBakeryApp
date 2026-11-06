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

class ProductoFormularioActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PRODUCTO_ID = "producto_id"
    }

    private val opcionesPorciones = listOf(1, 2, 4, 6, 8, 10, 12, 16, 20)

    private var productoId = 0 // 0 = producto nuevo
    private var productoOriginal: ProductoInventario? = null
    private var categoriaActual = "Tortas"
    private var porcionesActual = 10
    private var imagenUri: String? = null // foto nueva elegida de la galería

    private lateinit var ivFoto: ImageView
    private lateinit var etNombre: EditText
    private lateinit var etDescripcion: EditText
    private lateinit var etPrecio: EditText
    private lateinit var tvCategoria: TextView
    private lateinit var switchDisponible: MaterialSwitch
    private lateinit var etStock: EditText
    private lateinit var tvPorciones: TextView

    private val elegirFoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val guardada = copiarFoto(uri)
            if (guardada != null) {
                imagenUri = guardada
                mostrarFoto()
            } else {
                Toast.makeText(this, "No se pudo cargar la foto", Toast.LENGTH_SHORT).show()
            }
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
        etStock = findViewById(R.id.etStock)
        tvPorciones = findViewById(R.id.tvPorciones)

        colorearInterruptor()

        productoId = intent.getIntExtra(EXTRA_PRODUCTO_ID, 0)
        productoOriginal = InventarioRepositorio.buscarPorId(productoId)
        if (productoOriginal != null) {
            llenarCampos(productoOriginal!!)
        } else {
            productoId = 0
            prepararNuevo()
        }
        mostrarFoto()

        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }
        findViewById<View>(R.id.btnGuardarTop).setOnClickListener { guardar() }
        findViewById<View>(R.id.btnGuardar).setOnClickListener { guardar() }
        findViewById<View>(R.id.btnCambiarFoto).setOnClickListener { elegirFoto.launch("image/*") }
        tvCategoria.setOnClickListener { mostrarMenuCategorias() }
        tvPorciones.setOnClickListener { mostrarMenuPorciones() }
    }

    private fun llenarCampos(p: ProductoInventario) {
        findViewById<TextView>(R.id.tvTitulo).text = "Editar producto"
        findViewById<TextView>(R.id.btnGuardar).text = "Guardar cambios"

        etNombre.setText(p.nombre)
        etDescripcion.setText(p.descripcion)
        etPrecio.setText(p.precio.toString())
        etStock.setText(p.stock.toString())
        switchDisponible.isChecked = p.disponible

        categoriaActual = p.categoria
        tvCategoria.text = categoriaActual
        porcionesActual = p.porciones
        tvPorciones.text = textoPorciones(porcionesActual)
    }

    private fun prepararNuevo() {
        findViewById<TextView>(R.id.tvTitulo).text = "Crear producto"
        findViewById<TextView>(R.id.btnGuardar).text = "Crear producto"

        switchDisponible.isChecked = true
        categoriaActual = InventarioRepositorio.categorias.first()
        tvCategoria.text = categoriaActual
        porcionesActual = 10
        tvPorciones.text = textoPorciones(porcionesActual)
    }

    private fun colorearInterruptor() {
        val estados = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        switchDisponible.trackTintList = ColorStateList(
            estados,
            intArrayOf(0xFF7B5EA7.toInt(), 0xFFE6DDF5.toInt())
        )
        switchDisponible.thumbTintList = ColorStateList(
            estados,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFF8A8494.toInt())
        )
    }


    private fun mostrarFoto() {
        val uri = imagenUri ?: productoOriginal?.imagenUri
        val recurso = productoOriginal?.imagen

        when {
            uri != null -> {
                ivFoto.setPadding(0, 0, 0, 0)
                ivFoto.imageTintList = null
                ivFoto.setImageURI(Uri.parse(uri))
            }
            recurso != null -> {
                ivFoto.setPadding(0, 0, 0, 0)
                ivFoto.imageTintList = null
                ivFoto.setImageResource(recurso)
            }
            else -> {
                // Sin foto: icono provisional
                val pad = (64 * resources.displayMetrics.density).toInt()
                ivFoto.setPadding(pad, pad, pad, pad)
                ivFoto.setImageResource(R.drawable.ic_box)
                ivFoto.imageTintList = ColorStateList.valueOf(Color.parseColor("#7B5EA7"))
            }
        }
    }


    private fun copiarFoto(uri: Uri): String? {
        return try {
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, medidas) }

            var reduccion = 1
            while (medidas.outWidth / reduccion > 1024 || medidas.outHeight / reduccion > 1024) {
                reduccion *= 2
            }

            val opciones = BitmapFactory.Options().apply { inSampleSize = reduccion }
            val bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opciones)
            } ?: return null

            val archivo = File(filesDir, "producto_${System.currentTimeMillis()}.jpg")
            FileOutputStream(archivo).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            Uri.fromFile(archivo).toString()
        } catch (e: Exception) {
            null
        }
    }


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


    private fun guardar() {
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
            id = productoId, // 0 = nuevo; otro número = reemplaza al existente
            descripcion = etDescripcion.text.toString().trim(),
            disponible = switchDisponible.isChecked,
            porciones = porcionesActual,
            imagenUri = imagenUri ?: productoOriginal?.imagenUri
        )
        InventarioRepositorio.guardar(producto)

        val mensaje = if (productoId == 0) "Producto creado" else "Cambios guardados"
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        finish()
    }
}