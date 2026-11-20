package com.sasobakery.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration

// lo del admin con la coleccion usuarios, listar, editar, activar y eliminar
object UsuariosRepositorio {
    private const val COLECCION = "usuarios"
    private val db get() = FirebaseFirestore.getInstance()

    // lista de usuarios en tiempo real, ordenada por nombre
    fun escuchar(
        alCambiar: (List<Usuario>) -> Unit,
        alFallar: (String) -> Unit
    ): ListenerRegistration =
        db.collection(COLECCION).addSnapshotListener { instantanea, error ->
            if (error != null || instantanea == null) {
                alFallar("No se pudo cargar la lista de usuarios.")
                return@addSnapshotListener
            }

            val lista = instantanea.documents.map { doc ->
                val nombre = doc.getString("nombre").orEmpty()
                Usuario(
                    iniciales = calcularIniciales(nombre),
                    nombre = nombre,
                    correo = doc.getString("correo").orEmpty(),
                    // si el rol no se reconoce queda como comprador
                    rol = Rol.values().firstOrNull {
                        it.name.equals(doc.getString("rol"), ignoreCase = true)
                    } ?: Rol.COMPRADOR,
                    id = doc.id,
                    activo = doc.getBoolean("activo") != false
                )
            }.sortedBy { it.nombre.lowercase() }

            alCambiar(lista)
        }

    // cambia nombre y rol
    fun actualizar(id: String, nombre: String, rol: Rol, alTerminar: (String?) -> Unit) {
        db.collection(COLECCION).document(id)
            .update(mapOf("nombre" to nombre.trim(), "rol" to rol.name.lowercase()))
            .addOnSuccessListener { alTerminar(null) }
            .addOnFailureListener { alTerminar(mensajeDeError(it)) }
    }

    // activa o desactiva la cuenta, una desactivada no puede entrar
    fun cambiarActivo(id: String, activo: Boolean, alTerminar: (String?) -> Unit) {
        db.collection(COLECCION).document(id)
            .update("activo", activo)
            .addOnSuccessListener { alTerminar(null) }
            .addOnFailureListener { alTerminar(mensajeDeError(it)) }
    }

    // borra el perfil, sin perfil ya no puede entrar
    fun eliminar(id: String, alTerminar: (String?) -> Unit) {
        db.collection(COLECCION).document(id)
            .delete()
            .addOnSuccessListener { alTerminar(null) }
            .addOnFailureListener { alTerminar(mensajeDeError(it)) }
    }

    // true si es el unico admin activo, a ese no se le puede quitar el rol
    fun esUltimoAdminActivo(lista: List<Usuario>, usuario: Usuario): Boolean =
        usuario.rol == Rol.ADMINISTRADOR && usuario.activo &&
                lista.count { it.rol == Rol.ADMINISTRADOR && it.activo } <= 1

    // iniciales para el avatar, Ana Gomez da AG
    fun calcularIniciales(nombre: String): String {
        val palabras = nombre.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            palabras.isEmpty() -> "?"
            palabras.size == 1 -> palabras[0].take(2).uppercase()
            else -> "${palabras.first().first()}${palabras.last().first()}".uppercase()
        }
    }

    // mensaje segun el error
    private fun mensajeDeError(error: Exception): String =
        if (error is FirebaseFirestoreException) {
            when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "No tienes permiso para esta acción."
                FirebaseFirestoreException.Code.NOT_FOUND -> "Ese usuario ya no existe."
                else -> "No se pudo completar la acción. Revisa tu conexión."
            }
        } else {
            "No se pudo completar la acción. Intenta de nuevo."
        }
}