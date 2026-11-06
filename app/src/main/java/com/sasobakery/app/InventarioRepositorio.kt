package com.sasobakery.app

object InventarioRepositorio {

    val categorias = listOf("Tortas", "Postres", "Brownies", "Galletas")

    private val productos = mutableListOf(
        ProductoInventario(
            "Torta Aurora", "Tortas", 68000, 8, id = 1,
            descripcion = "Bizcocho de vainilla, frutos rojos y crema suave. Decorada a mano con flores delicadas.",
            porciones = 10
        ),
        ProductoInventario(
            "Cheesecake fresa", "Postres", 28000, 6, id = 2,
            descripcion = "Base crujiente de galleta con crema suave y salsa de fresa natural.",
            porciones = 8
        ),
        ProductoInventario(
            "Brownie intenso", "Brownies", 10000, 3, id = 3,
            descripcion = "Brownie húmedo de chocolate oscuro.",
            porciones = 1
        ),
        ProductoInventario(
            "Galletas de casa", "Galletas", 14000, 0, id = 4,
            descripcion = "Galletas horneadas cada mañana con mantequilla.",
            porciones = 6
        ),
        ProductoInventario(
            "Torta de chocolate", "Tortas", 72000, 5, id = 5,
            descripcion = "Bizcocho de chocolate relleno de ganache.",
            porciones = 12
        ),
        ProductoInventario(
            "Torta red velvet", "Tortas", 75000, 9, id = 6,
            descripcion = "Bizcocho rojo aterciopelado con crema de queso.",
            porciones = 12
        ),
        ProductoInventario(
            "Tiramisú", "Postres", 24000, 12, id = 7,
            descripcion = "Capas de café, mascarpone y cacao.",
            porciones = 6
        ),
        ProductoInventario(
            "Tres leches", "Postres", 22000, 2, id = 8,
            descripcion = "Bizcocho esponjoso bañado en tres leches con crema.",
            porciones = 8
        ),
        ProductoInventario(
            "Galletas de avena", "Galletas", 12000, 15, id = 9,
            descripcion = "Galletas de avena con pasas y canela.",
            porciones = 6
        ),
        ProductoInventario(
            "Brownie con nueces", "Brownies", 12000, 7, id = 10,
            descripcion = "Brownie de chocolate con nueces tostadas.",
            porciones = 1
        )
    )

    private var siguienteId = 11

    private val idsFavoritosDeSaso = listOf(1, 2, 3, 4)

    fun obtenerTodos(): List<ProductoInventario> = productos.toList()

    fun buscarPorId(id: Int): ProductoInventario? = productos.firstOrNull { it.id == id }

    fun obtenerCatalogo(): List<ProductoInventario> = productos.filter { it.disponible }

    fun obtenerFavoritosDeSaso(): List<ProductoInventario> =
        idsFavoritosDeSaso.mapNotNull { id ->
            productos.firstOrNull { it.id == id && it.disponible }
        }

    fun guardar(producto: ProductoInventario) {
        if (producto.id == 0) {
            productos.add(0, producto.copy(id = siguienteId++))
        } else {
            val posicion = productos.indexOfFirst { it.id == producto.id }
            if (posicion >= 0) {
                productos[posicion] = producto
            } else {
                productos.add(0, producto)
            }
        }
    }

    fun descontarStock(id: Int, cantidad: Int) {
        val posicion = productos.indexOfFirst { it.id == id }
        if (posicion >= 0) {
            val p = productos[posicion]
            productos[posicion] = p.copy(stock = (p.stock - cantidad).coerceAtLeast(0))
        }
    }
}