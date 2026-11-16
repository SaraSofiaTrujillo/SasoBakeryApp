package com.sasobakery.app

object FavoritosRepositorio {

    private val ids = mutableSetOf<String>()

    fun esFavorito(idProducto: String): Boolean = ids.contains(idProducto)

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