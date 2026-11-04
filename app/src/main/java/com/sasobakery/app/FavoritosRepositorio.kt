package com.sasobakery.app

// Productos que el comprador marcó con el corazón (solo en memoria).
// Cuando exista la base de datos, esta clase es la que se reemplaza.
object FavoritosRepositorio {

    private val ids = mutableSetOf<Int>()

    fun esFavorito(idProducto: Int): Boolean = ids.contains(idProducto)

    /** Marca o desmarca el producto. Devuelve true si quedó como favorito. */
    fun alternar(idProducto: Int): Boolean {
        return if (ids.remove(idProducto)) {
            false
        } else {
            ids.add(idProducto)
            true
        }
    }

    fun obtenerIds(): Set<Int> = ids.toSet()
}