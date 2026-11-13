package com.sasobakery.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration

/**
 * Acceso del administrador a la colección "usuarios" de Firestore: listar en tiempo
 * real, editar, activar/desactivar y eliminar. Crear cuentas nuevas está en
 * AuthRepositorio.crearUsuarioComoAdmin, porque además crea la cuenta de acceso.
 */
object UsuariosRepositorio {

    private const val COLECCION = "usuarios"
    private val db get() = FirebaseFirestore.getInstance()

    /**
     * Escucha la lista de usuarios: [alCambiar] se llama una vez al empezar y de nuevo
     * cada vez que alguien crea, edita o elimina un usuario. Hay que cancelar el
     * ListenerRegistration que devuelve cuando la pantalla deja de verse.
     */
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
                    rol = Rol.values().firstOrNull {
                        it.name.equals(doc.getString("rol"), ignoreCase = true)
                    } ?: Rol.COMPRADOR,
                    id = doc.id,
                    // Si el campo no existe se considera activo, igual que en el login
                    activo = doc.getBoolean("activo") != false
                )
            }.sortedBy { it.nombre.lowercase() }

            alCambiar(lista)
        }

    /** Guarda el nuevo nombre y rol de un usuario. [alTerminar] recibe null si salió bien. */
    fun actualizar(id: String, nombre: String, rol: Rol, alTerminar: (String?) -> Unit) {
        db.collection(COLECCION).document(id)
            .update(mapOf("nombre" to nombre.trim(), "rol" to rol.name.lowercase()))
            .addOnSuccessListener { alTerminar(null) }
            .addOnFailureListener { alTerminar(mensajeDeError(it)) }
    }

    /** Activa o desactiva la cuenta. Una cuenta desactivada no puede iniciar sesión. */
    fun cambiarActivo(id: String, activo: Boolean, alTerminar: (String?) -> Unit) {
        db.collection(COLECCION).document(id)
            .update("activo", activo)
            .addOnSuccessListener { alTerminar(null) }
            .addOnFailureListener { alTerminar(mensajeDeError(it)) }
    }

    /**
     * Elimina el perfil del usuario: sin perfil ya no puede entrar a la app. Su cuenta
     * en Authentication no se borra desde aquí (eso exige permisos de servidor).
     */
    fun eliminar(id: String, alTerminar: (String?) -> Unit) {
        db.collection(COLECCION).document(id)
            .delete()
            .addOnSuccessListener { alTerminar(null) }
            .addOnFailureListener { alTerminar(mensajeDeError(it)) }
    }

    /** true si [usuario] es el único administrador activo de [lista] (no se puede quitar). */
    fun esUltimoAdminActivo(lista: List<Usuario>, usuario: Usuario): Boolean =
        usuario.rol == Rol.ADMINISTRADOR && usuario.activo &&
                lista.count { it.rol == Rol.ADMINISTRADOR && it.activo } <= 1

    /** "Ana María Gómez" -> "AG"; "Ana" -> "AN". */
    fun calcularIniciales(nombre: String): String {
        val palabras = nombre.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            palabras.isEmpty() -> "?"
            palabras.size == 1 -> palabras[0].take(2).uppercase()
            else -> "${palabras.first().first()}${palabras.last().first()}".uppercase()
        }
    }

    // Convierte el error de Firestore en un mensaje claro en español
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