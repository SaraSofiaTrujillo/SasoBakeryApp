package com.sasobakery.app

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration

/**
 * Acceso a la colección "productos" de Firestore: listar en tiempo real, crear,
 * editar y descontar stock. Guarda además una copia en memoria de la última lista
 * recibida, para que el carrito y el detalle puedan consultar un producto al instante.
 */
object InventarioRepositorio {

    val categorias = listOf("Tortas", "Postres", "Brownies", "Galletas")

    private const val COLECCION = "productos"
    private val db get() = FirebaseFirestore.getInstance()

    // Última lista recibida de Firestore (se actualiza sola mientras hay una escucha activa)
    private var productos: List<ProductoInventario> = emptyList()

    /**
     * Escucha la lista de productos: [alCambiar] se llama una vez al empezar y de nuevo
     * cada vez que alguien crea o edita un producto. Hay que cancelar el
     * ListenerRegistration que devuelve cuando la pantalla deja de verse.
     */
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
            alCambiar(productos)
        }

    // ---------- Consultas sobre la copia en memoria ----------

    fun obtenerTodos(): List<ProductoInventario> = productos

    fun buscarPorId(id: String): ProductoInventario? = productos.firstOrNull { it.id == id }

    /** Solo los productos que el vendedor dejó disponibles para la venta. */
    fun obtenerCatalogo(): List<ProductoInventario> = productos.filter { it.disponible }

    /**
     * "Favoritos de Saso": los productos que el vendedor marcó como destacados.
     * Si todavía no hay ninguno marcado, se muestran los primeros 4 disponibles.
     */
    fun obtenerFavoritosDeSaso(): List<ProductoInventario> {
        val destacados = obtenerCatalogo().filter { it.destacado }
        return if (destacados.isNotEmpty()) destacados else obtenerCatalogo().take(4)
    }
    // ---------- Escritura en Firestore ----------

    /**
     * Crea el producto (si su id está vacío) o actualiza el existente.
     * [alTerminar] recibe null si salió bien, o el mensaje de error.
     */
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

        // La foto solo se escribe si el producto tiene una
        if (producto.imagenBase64 != null) datos["imagen"] = producto.imagenBase64

        val tarea = if (producto.id.isEmpty()) {
            // Producto nuevo: Firestore le inventa el id
            db.collection(COLECCION).add(datos + ("creado" to FieldValue.serverTimestamp()))
        } else {
            // Producto existente: solo se cambian estos campos
            db.collection(COLECCION).document(producto.id).update(datos)
        }
        tarea
            .addOnSuccessListener { alTerminar(null) }
            .addOnFailureListener { alTerminar(mensajeDeError(it)) }
    }

    /** Resta unidades al stock real en Firestore (se usa al confirmar un pago). */
    fun descontarStock(id: String, cantidad: Int) {
        db.collection(COLECCION).document(id)
            .update("stock", FieldValue.increment(-cantidad.toLong()))
    }

    // ---------- Utilidades ----------

    // Convierte un documento de Firestore en un ProductoInventario
    private fun aProducto(doc: DocumentSnapshot) = ProductoInventario(
        nombre = doc.getString("nombre").orEmpty(),
        categoria = doc.getString("categoria") ?: categorias.first(),
        precio = (doc.getLong("precio") ?: 0L).toInt(),
        stock = (doc.getLong("stock") ?: 0L).toInt(),
        id = doc.id,
        descripcion = doc.getString("descripcion").orEmpty(),
        // Si el campo no existe se considera disponible
        disponible = doc.getBoolean("disponible") != false,
        porciones = (doc.getLong("porciones") ?: 1L).toInt(),
        destacado = doc.getBoolean("destacado") == true,
        imagenBase64 = doc.getString("imagen")
    )

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