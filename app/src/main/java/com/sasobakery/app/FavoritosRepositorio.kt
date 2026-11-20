package com.sasobakery.app

// ids de los productos favoritos, solo en memoria
object FavoritosRepositorio {
    private val ids = mutableSetOf<String>()

    fun esFavorito(idProducto: String): Boolean = ids.contains(idProducto)

    // quita el favorito si estaba o lo agrega si no, devuelve el nuevo estado
    fun alternar(idProducto: String): Boolean {
        return if (ids.remove(idProducto)) {
            false
        } else {
            ids.add(idProducto)
            true
        }
    }

    fun obtenerIds(): Set<String> = ids.toSet()
}