package com.sasobakery.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Datos del vendedor (personales) y de la tienda (los define el administrador)
data class PerfilVendedor(
    val nombre: String,
    val correo: String,
    val telefono: String,
    val negocio: String,
    val direccion: String,
    val horario: String
) {
    // "Camila Ríos" -> "CR" (para el avatar)
    val iniciales: String
        get() = nombre.trim()
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
}

/**
 * Perfil del vendedor con la sesión iniciada. Nombre y teléfono personal vienen de su
 * documento en Firestore (usuarios/{uid}) y el correo de Firebase Authentication.
 * Los datos del negocio NO son del vendedor: vienen de NegocioRepositorio y solo los
 * modifica el administrador.
 */
object PerfilVendedorRepositorio {

    private var nombre = ""
    private var correo = ""
    private var telefono = ""
    private var uidCargado: String? = null // de qué cuenta son los datos guardados en memoria

    /** El perfil del vendedor en sesión, con los datos actuales del negocio. */
    fun obtener(): PerfilVendedor {
        val usuario = FirebaseAuth.getInstance().currentUser
        if (usuario?.uid != uidCargado) {
            // Todavía no llegó el perfil de esta cuenta: se muestra algo provisional
            correo = usuario?.email.orEmpty()
            nombre = correo.substringBefore("@")
            telefono = ""
        }
        val negocio = NegocioRepositorio.obtener()
        return PerfilVendedor(
            nombre = nombre,
            correo = correo,
            telefono = telefono,
            negocio = negocio.nombre,
            direccion = negocio.direccion,
            horario = negocio.horario
        )
    }

    /** Datos del negocio para mostrarlos al comprador (recogida y ayuda). */
    fun tienda(): PerfilVendedor {
        val negocio = NegocioRepositorio.obtener()
        return PerfilVendedor(
            nombre = "",
            correo = "",
            telefono = negocio.telefono,
            negocio = negocio.nombre,
            direccion = negocio.direccion,
            horario = negocio.horario
        )
    }

    /**
     * Lee el perfil del vendedor desde Firestore y avisa cuando termina.
     * Si ya estaba cargado para esta cuenta, avisa de inmediato.
     */
    fun cargarDeSesion(alTerminar: () -> Unit) {
        val usuario = FirebaseAuth.getInstance().currentUser ?: return
        if (usuario.uid == uidCargado) {
            alTerminar()
            return
        }
        FirebaseFirestore.getInstance().collection("usuarios").document(usuario.uid).get()
            .addOnSuccessListener { doc ->
                correo = usuario.email.orEmpty()
                nombre = doc.getString("nombre").orEmpty().ifBlank { correo.substringBefore("@") }
                telefono = doc.getString("telefono").orEmpty()
                uidCargado = usuario.uid
                alTerminar()
            }
    }

    /**
     * Guarda el nombre y el teléfono personal en Firestore. [alTerminar] recibe null si
     * salió bien, o el mensaje de error. El negocio no se guarda aquí.
     */
    fun guardar(nuevo: PerfilVendedor, alTerminar: (String?) -> Unit = {}) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            alTerminar("No hay sesión iniciada")
            return
        }
        FirebaseFirestore.getInstance().collection("usuarios").document(uid)
            .update(mapOf("nombre" to nuevo.nombre, "telefono" to nuevo.telefono))
            .addOnSuccessListener {
                nombre = nuevo.nombre
                telefono = nuevo.telefono
                uidCargado = uid
                alTerminar(null)
            }
            .addOnFailureListener {
                alTerminar("No se pudo guardar. Revisa tu conexión e intenta de nuevo.")
            }
    }
}