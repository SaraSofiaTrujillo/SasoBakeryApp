package com.sasobakery.app

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

object CarritoRepositorio {

    // id del producto -> cantidad en el carrito
    private val cantidades = linkedMapOf<String, Int>()

    fun agregar(idProducto: String): ResultadoAgregar = agregar(idProducto, 1)

    fun agregar(idProducto: String, cantidad: Int): ResultadoAgregar {
        val producto = InventarioRepositorio.buscarPorId(idProducto)
        if (producto == null || !producto.disponible) return ResultadoAgregar.NO_DISPONIBLE
        if (producto.stock <= 0) return ResultadoAgregar.AGOTADO

        val unidades = cantidad.coerceAtLeast(1)
        val actual = cantidades[idProducto] ?: 0
        if (actual + unidades > producto.stock) return ResultadoAgregar.LIMITE_DE_STOCK

        cantidades[idProducto] = actual + unidades
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
    }
    fun quitar(idProducto: String) {
        cantidades.remove(idProducto)
    }

    fun vaciar() {
        cantidades.clear()
    }

    fun cantidadDe(idProducto: String): Int = cantidades[idProducto] ?: 0

    fun cantidadTotal(): Int = cantidades.values.sum()

    fun obtenerItems(): List<ItemCarrito> =
        cantidades.mapNotNull { (id, cantidad) ->
            InventarioRepositorio.buscarPorId(id)?.let { ItemCarrito(it, cantidad) }
        }
    fun total(): Int = obtenerItems().sumOf { it.subtotal }
}