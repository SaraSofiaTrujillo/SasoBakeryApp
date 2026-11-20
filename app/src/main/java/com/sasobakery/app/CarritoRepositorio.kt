package com.sasobakery.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// una linea del carrito, producto y cantidad
data class ItemCarrito(
    val producto: ProductoInventario,
    val cantidad: Int
) {
    val subtotal: Int get() = producto.precio * cantidad
}

// resultado de agregar un producto
enum class ResultadoAgregar {
    AGREGADO,
    AGOTADO,
    LIMITE_DE_STOCK,
    NO_DISPONIBLE
}

// carrito del comprador, guarda id y cantidad y se sube a firestore
object CarritoRepositorio {
    private const val COLECCION = "carritos"
    private val db get() = FirebaseFirestore.getInstance()

    // id del producto y su cantidad
    private val cantidades = linkedMapOf<String, Int>()

    // cuenta de la que se cargo el carrito
    private var uidCargado: String? = null

    // trae el carrito guardado, lo que se agrego antes se junta
    fun cargarDeSesion(alTerminar: () -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid == uidCargado) {
            alTerminar()
            return
        }
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

    // sube el carrito a firestore
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

    // al cerrar sesion, en firestore queda guardado
    fun limpiarMemoria() {
        cantidades.clear()
        uidCargado = null
    }

    // quita lo agotado o desactivado y ajusta cantidades, true si cambio algo
    fun reconciliar(): Boolean {
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

    fun agregar(idProducto: String): ResultadoAgregar = agregar(idProducto, 1)

    // agrega si esta disponible y no pasa el stock
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

    // cuantas unidades mas se pueden agregar
    fun unidadesAgregables(idProducto: String): Int {
        val producto = InventarioRepositorio.buscarPorId(idProducto) ?: return 0
        if (!producto.disponible) return 0
        return (producto.stock - cantidadDe(idProducto)).coerceAtLeast(0)
    }

    // resta una, si llega a cero lo quita
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

    fun cantidadDe(idProducto: String): Int = cantidades[idProducto] ?: 0

    fun cantidadTotal(): Int = obtenerItems().sumOf { it.cantidad }

    // arma los items con los productos reales
    fun obtenerItems(): List<ItemCarrito> =
        cantidades.mapNotNull { (id, cantidad) ->
            InventarioRepositorio.buscarPorId(id)
                ?.takeIf { it.disponible }
                ?.let { ItemCarrito(it, cantidad) }
        }

    fun total(): Int = obtenerItems().sumOf { it.subtotal }
}