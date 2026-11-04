package com.sasobakery.app

// Un producto dentro del carrito, con su cantidad
data class ItemCarrito(
    val producto: ProductoInventario,
    val cantidad: Int
) {
    val subtotal: Int get() = producto.precio * cantidad // pesos colombianos (COP)
}

// Resultado de intentar agregar un producto al carrito
enum class ResultadoAgregar {
    AGREGADO,
    AGOTADO,
    LIMITE_DE_STOCK,
    NO_DISPONIBLE
}

// Carrito del comprador (solo en memoria).
// Cuando exista la base de datos, esta clase es la que se reemplaza.
object CarritoRepositorio {

    // id del producto -> cantidad (conserva el orden en que se agregaron)
    private val cantidades = linkedMapOf<Int, Int>()

    /** Agrega 1 unidad si el producto existe, está disponible y hay stock suficiente. */
    fun agregar(idProducto: Int): ResultadoAgregar {
        val producto = InventarioRepositorio.buscarPorId(idProducto)
        if (producto == null || !producto.disponible) return ResultadoAgregar.NO_DISPONIBLE
        if (producto.stock <= 0) return ResultadoAgregar.AGOTADO

        val actual = cantidades[idProducto] ?: 0
        if (actual >= producto.stock) return ResultadoAgregar.LIMITE_DE_STOCK

        cantidades[idProducto] = actual + 1
        return ResultadoAgregar.AGREGADO
    }

    /** Quita 1 unidad (si era la última, quita el producto del carrito). */
    fun disminuir(idProducto: Int) {
        val actual = cantidades[idProducto] ?: return
        if (actual <= 1) cantidades.remove(idProducto) else cantidades[idProducto] = actual - 1
    }

    /** Quita el producto completo del carrito. */
    fun quitar(idProducto: Int) {
        cantidades.remove(idProducto)
    }

    fun vaciar() {
        cantidades.clear()
    }

    fun cantidadDe(idProducto: Int): Int = cantidades[idProducto] ?: 0

    /** Total de unidades en el carrito (es el número del globito). */
    fun cantidadTotal(): Int = cantidades.values.sum()

    /** Productos del carrito con su cantidad (ignora los que ya no existan). */
    fun obtenerItems(): List<ItemCarrito> =
        cantidades.mapNotNull { (id, cantidad) ->
            InventarioRepositorio.buscarPorId(id)?.let { ItemCarrito(it, cantidad) }
        }

    /** Total a pagar en pesos colombianos. */
    fun total(): Int = obtenerItems().sumOf { it.subtotal }
}