package com.sasobakery.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Datos del vendedor y de su negocio
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
 * Perfil compartido entre pantallas. El nombre y el correo del vendedor vienen de su
 * cuenta (Firestore y Firebase Authentication). Teléfono, negocio, dirección y horario
 * siguen en memoria: sus reglas de Firestore se pueden ampliar más adelante.
 */
object PerfilVendedorRepositorio {

    private var perfil = PerfilVendedor(
        nombre = "",
        correo = "",
        telefono = "+57 300 123 4567",
        negocio = "Saso Bakery",
        direccion = "Cra 7 # 45-20, Bogotá",
        horario = "Lun - Sáb · 8:00 a. m. - 6:00 p. m."
    )
    private var uidCargado: String? = null // de qué cuenta son el nombre y el correo guardados

    fun obtener(): PerfilVendedor = perfil

    /**
     * Lee el nombre real del vendedor desde Firestore y avisa cuando termina.
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
                val correo = usuario.email.orEmpty()
                perfil = perfil.copy(
                    nombre = doc.getString("nombre").orEmpty()
                        .ifBlank { correo.substringBefore("@") },
                    correo = correo
                )
                uidCargado = usuario.uid
                alTerminar()
            }
    }

    /** Guarda el perfil en memoria y el nombre nuevo en Firestore. */
    fun guardar(nuevo: PerfilVendedor) {
        perfil = nuevo
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("usuarios").document(uid)
            .update("nombre", nuevo.nombre)
    }
}