package com.sasobakery.app

object FavoritosRepositorio {

    private val ids = mutableSetOf<Int>()

    fun esFavorito(idProducto: Int): Boolean = ids.contains(idProducto)

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