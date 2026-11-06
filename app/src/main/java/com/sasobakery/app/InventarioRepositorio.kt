package com.sasobakery.app

// Lista de productos compartida entre pantallas (solo en memoria).
// Cuando exista la base de datos, esta clase es la que se reemplaza.
object InventarioRepositorio {

    // Categorías disponibles para el selector del formulario
    val categorias = listOf("Tortas", "Postres", "Brownies", "Galletas")

    // Productos de prueba (los mismos que ya se veían en el inventario)
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

    // El próximo producto nuevo recibe el id 11, luego 12, etc.
    private var siguienteId = 11

    // Ids de los productos que Saso destaca en "Favoritos de Saso" (el comprador los ve
    // en ese orden). Si el vendedor desactiva alguno, deja de aparecer.
    private val idsFavoritosDeSaso = listOf(1, 2, 3, 4)

    /** Copia de la lista actual (así nadie la modifica por accidente). */
    fun obtenerTodos(): List<ProductoInventario> = productos.toList()

    /** Busca un producto por su id; devuelve null si no existe. */
    fun buscarPorId(id: Int): ProductoInventario? = productos.firstOrNull { it.id == id }

    /** Productos que ve el comprador: solo los marcados como disponibles (visibles en el catálogo). */
    fun obtenerCatalogo(): List<ProductoInventario> = productos.filter { it.disponible }

    /** "Favoritos de Saso": los destacados de la casa que además estén visibles en el catálogo. */
    fun obtenerFavoritosDeSaso(): List<ProductoInventario> =
        idsFavoritosDeSaso.mapNotNull { id ->
            productos.firstOrNull { it.id == id && it.disponible }
        }

    /**
     * Guarda un producto:
     * - id = 0  -> es NUEVO: se le asigna un id y se agrega al inicio de la lista.
     * - id != 0 -> es EXISTENTE: se reemplaza el que tiene ese id (no se duplica).
     */
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

    /** Descuenta las unidades vendidas de un producto (el stock nunca baja de 0). */
    fun descontarStock(id: Int, cantidad: Int) {
        val posicion = productos.indexOfFirst { it.id == id }
        if (posicion >= 0) {
            val p = productos[posicion]
            productos[posicion] = p.copy(stock = (p.stock - cantidad).coerceAtLeast(0))
        }
    }
}