package com.sasobakery.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

data class ItemCarrito(
    val producto: ProductoInventario,
    val cantidad: Int
) {
    val subtotal: Int get() = producto.precio * cantidad
}

enum class ResultadoAgregar {
    AGREGADO,
    AGOTADO,
    LIMITE_DE_STOCK,
    NO_DISPONIBLE
}

/**
 * Carrito del comprador con la sesión iniciada. Guarda solo "id del producto -> cantidad":
 * nombre, precio, foto y stock se leen siempre de los productos reales de Firestore.
 * Cada cambio se guarda en la colección "carritos", en el documento del comprador (su uid),
 * así el carrito sigue ahí al cerrar y abrir la app.
 */
object CarritoRepositorio {

    private const val COLECCION = "carritos"
    private val db get() = FirebaseFirestore.getInstance()

    // id del producto -> cantidad en el carrito
    private val cantidades = linkedMapOf<String, Int>()

    // De qué cuenta es el carrito que ya se leyó de Firestore (null = todavía no)
    private var uidCargado: String? = null

    // ---------- Firestore ----------

    /**
     * Lee el carrito guardado de esta cuenta y avisa cuando termina. Si ya estaba
     * cargado, avisa de inmediato. Lo agregado antes de que llegue la lectura no se pierde.
     */
    fun cargarDeSesion(alTerminar: () -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid == uidCargado) {
            alTerminar()
            return
        }
        // Si lo que hay en memoria era de otra cuenta, se descarta
        if (uidCargado != null) cantidades.clear()

        db.collection(COLECCION).document(uid).get()
            .addOnSuccessListener { doc ->
                val habiaLocales = cantidades.isNotEmpty()
                val guardados = doc.get("items") as? Map<*, *> ?: emptyMap<Any, Any>()
                guardados.forEach { (clave, valor) ->
                    val id = clave as? String ?: return@forEach
                    val cantidad = (valor as? Number)?.toInt() ?: return@forEach
                    if (cantidad > 0) cantidades[id] = maxOf(cantidades[id] ?: 0, cantidad)
                }
                uidCargado = uid
                if (habiaLocales) guardar()
                alTerminar()
            }
    }

    // Escribe el carrito actual. No hace nada hasta que se haya leído el guardado,
    // para no borrar por error lo que ya había en Firestore.
    private fun guardar() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid != uidCargado) return
        db.collection(COLECCION).document(uid).set(
            mapOf(
                "items" to cantidades.toMap(),
                "actualizado" to FieldValue.serverTimestamp()
            )
        )
    }

    /** Al cerrar sesión: se olvida el carrito de memoria (en Firestore queda guardado). */
    fun limpiarMemoria() {
        cantidades.clear()
        uidCargado = null
    }

    /**
     * Ajusta el carrito a lo que hay en los productos reales: quita los desactivados,
     * agotados o que ya no existen, y baja la cantidad si supera el stock.
     * Devuelve true si cambió algo (para avisarle al comprador).
     */
    fun reconciliar(): Boolean {
        // Mientras no lleguen los productos reales no se ajusta nada
        if (!InventarioRepositorio.estaCargado()) return false

        var cambio = false
        val iterador = cantidades.entries.iterator()
        while (iterador.hasNext()) {
            val entrada = iterador.next()
            val producto = InventarioRepositorio.buscarPorId(entrada.key)
            if (producto == null || !producto.disponible || producto.stock <= 0) {
                iterador.remove()
                cambio = true
            } else if (entrada.value > producto.stock) {
                entrada.setValue(producto.stock)
                cambio = true
            }
        }
        if (cambio) guardar()
        return cambio
    }

    // ---------- Cambios en el carrito ----------

    fun agregar(idProducto: String): ResultadoAgregar = agregar(idProducto, 1)

    fun agregar(idProducto: String, cantidad: Int): ResultadoAgregar {
        val producto = InventarioRepositorio.buscarPorId(idProducto)
        if (producto == null || !producto.disponible) return ResultadoAgregar.NO_DISPONIBLE
        if (producto.stock <= 0) return ResultadoAgregar.AGOTADO

        val unidades = cantidad.coerceAtLeast(1)
        val actual = cantidades[idProducto] ?: 0
        if (actual + unidades > producto.stock) return ResultadoAgregar.LIMITE_DE_STOCK

        cantidades[idProducto] = actual + unidades
        guardar()
        return ResultadoAgregar.AGREGADO
    }

    fun unidadesAgregables(idProducto: String): Int {
        val producto = InventarioRepositorio.buscarPorId(idProducto) ?: return 0
        if (!producto.disponible) return 0
        return (producto.stock - cantidadDe(idProducto)).coerceAtLeast(0)
    }

    fun disminuir(idProducto: String) {
        val actual = cantidades[idProducto] ?: return
        if (actual <= 1) cantidades.remove(idProducto) else cantidades[idProducto] = actual - 1
        guardar()
    }

    fun quitar(idProducto: String) {
        cantidades.remove(idProducto)
        guardar()
    }

    fun vaciar() {
        cantidades.clear()
        guardar()
    }

    // ---------- Consultas ----------

    fun cantidadDe(idProducto: String): Int = cantidades[idProducto] ?: 0

    // Solo cuenta lo que realmente se puede comprar (productos reales y disponibles)
    fun cantidadTotal(): Int = obtenerItems().sumOf { it.cantidad }

    fun obtenerItems(): List<ItemCarrito> =
        cantidades.mapNotNull { (id, cantidad) ->
            InventarioRepositorio.buscarPorId(id)
                ?.takeIf { it.disponible }
                ?.let { ItemCarrito(it, cantidad) }
        }

    fun total(): Int = obtenerItems().sumOf { it.subtotal }
}