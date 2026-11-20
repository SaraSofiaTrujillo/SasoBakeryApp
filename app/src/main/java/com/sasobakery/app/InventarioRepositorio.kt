package com.sasobakery.app

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration

// todo lo de la coleccion productos, listar, crear, editar y descontar stock
object InventarioRepositorio {
    // categorias fijas de la tienda
    val categorias = listOf("Tortas", "Postres", "Brownies", "Galletas")

    private const val COLECCION = "productos"
    private val db get() = FirebaseFirestore.getInstance()

    // ultima lista que llego de firestore, asi el carrito consulta rapido
    private var productos: List<ProductoInventario> = emptyList()

    // true cuando ya llegaron los productos reales
    private var cargado = false

    // lista de productos en tiempo real, hay que cancelarla en onStop
    fun escuchar(
        alCambiar: (List<ProductoInventario>) -> Unit,
        alFallar: (String) -> Unit
    ): ListenerRegistration =
        db.collection(COLECCION).addSnapshotListener { instantanea, error ->
            if (error != null || instantanea == null) {
                alFallar("No se pudo cargar la lista de productos.")
                return@addSnapshotListener
            }
            productos = instantanea.documents
                .map { aProducto(it) }
                .sortedBy { it.nombre.lowercase() }
            if (!instantanea.metadata.isFromCache || !instantanea.isEmpty) cargado = true
            alCambiar(productos)
        }

    fun obtenerTodos(): List<ProductoInventario> = productos

    fun estaCargado(): Boolean = cargado

    fun buscarPorId(id: String): ProductoInventario? = productos.firstOrNull { it.id == id }

    // solo los productos disponibles para la venta
    fun obtenerCatalogo(): List<ProductoInventario> = productos.filter { it.disponible }

    // los destacados, si no hay ninguno muestra los primeros 4
    fun obtenerFavoritosDeSaso(): List<ProductoInventario> {
        val destacados = obtenerCatalogo().filter { it.destacado }
        return if (destacados.isNotEmpty()) destacados else obtenerCatalogo().take(4)
    }

    // crea el producto si no tiene id, si tiene lo actualiza
    fun guardar(producto: ProductoInventario, alTerminar: (String?) -> Unit) {
        val datos = mutableMapOf<String, Any>(
            "nombre" to producto.nombre,
            "categoria" to producto.categoria,
            "precio" to producto.precio,
            "stock" to producto.stock,
            "descripcion" to producto.descripcion,
            "disponible" to producto.disponible,
            "porciones" to producto.porciones,
            "destacado" to producto.destacado
        )

        // la foto solo se escribe si hay una
        if (producto.imagenBase64 != null) datos["imagen"] = producto.imagenBase64

        val tarea = if (producto.id.isEmpty()) {
            db.collection(COLECCION).add(datos + ("creado" to FieldValue.serverTimestamp()))
        } else {
            db.collection(COLECCION).document(producto.id).update(datos)
        }
        tarea
            .addOnSuccessListener { alTerminar(null) }
            .addOnFailureListener { alTerminar(mensajeDeError(it)) }
    }

    // muestra u oculta el producto sin borrarlo
    fun cambiarDisponibilidad(id: String, disponible: Boolean, alTerminar: (String?) -> Unit) {
        db.collection(COLECCION).document(id).update("disponible", disponible)
            .addOnSuccessListener { alTerminar(null) }
            .addOnFailureListener { alTerminar(mensajeDeError(it)) }
    }

    // resta unidades al stock
    fun descontarStock(id: String, cantidad: Int) {
        db.collection(COLECCION).document(id)
            .update("stock", FieldValue.increment(-cantidad.toLong()))
    }

    // pasa el documento de firestore a ProductoInventario
    private fun aProducto(doc: DocumentSnapshot) = ProductoInventario(
        nombre = doc.getString("nombre").orEmpty(),
        categoria = doc.getString("categoria") ?: categorias.first(),
        precio = (doc.getLong("precio") ?: 0L).toInt(),
        stock = (doc.getLong("stock") ?: 0L).toInt(),
        id = doc.id,
        descripcion = doc.getString("descripcion").orEmpty(),
        // si el campo no existe cuenta como disponible
        disponible = doc.getBoolean("disponible") != false,
        porciones = (doc.getLong("porciones") ?: 1L).toInt(),
        destacado = doc.getBoolean("destacado") == true,
        imagenBase64 = doc.getString("imagen")
    )

    // mensaje segun el error
    private fun mensajeDeError(error: Exception): String =
        if (error is FirebaseFirestoreException) {
            when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "No tienes permiso para esta acción."
                FirebaseFirestoreException.Code.NOT_FOUND -> "Ese producto ya no existe."
                else -> "No se pudo completar la acción. Revisa tu conexión."
            }
        } else {
            "No se pudo completar la acción. Intenta de nuevo."
        }
}