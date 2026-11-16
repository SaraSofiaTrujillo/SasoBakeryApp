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
 * Perfil del vendedor con la sesión iniciada. Todo viene de su documento en Firestore
 * (usuarios/{uid}): nombre, teléfono, negocio, dirección y horario; el correo viene
 * de Firebase Authentication.
 */
object PerfilVendedorRepositorio {

    private const val NEGOCIO = "Saso Bakery"

    private var perfil = PerfilVendedor(
        nombre = "",
        correo = "",
        telefono = "",
        negocio = NEGOCIO,
        direccion = "",
        horario = ""
    )
    private var uidCargado: String? = null // de qué cuenta es el perfil guardado en memoria

    // Datos del negocio que ve el comprador (recogida en tienda y ayuda). Por ahora son
    // fijos, porque un comprador no puede leer el perfil de un vendedor. En el Día 4
    // se guardarán dentro de cada pedido.
    private val tiendaPublica = PerfilVendedor(
        nombre = "",
        correo = "",
        telefono = "+57 300 123 4567",
        negocio = NEGOCIO,
        direccion = "Cra 7 # 45-20, Bogotá",
        horario = "Lun - Sáb · 8:00 a. m. - 6:00 p. m."
    )

    /** El perfil del vendedor en sesión (para sus propias pantallas). */
    fun obtener(): PerfilVendedor = perfil

    /** Datos del negocio para mostrarlos al comprador. */
    fun tienda(): PerfilVendedor = tiendaPublica

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
                val correo = usuario.email.orEmpty()
                perfil = PerfilVendedor(
                    nombre = doc.getString("nombre").orEmpty()
                        .ifBlank { correo.substringBefore("@") },
                    correo = correo,
                    telefono = doc.getString("telefono").orEmpty(),
                    negocio = doc.getString("negocio").orEmpty().ifBlank { NEGOCIO },
                    direccion = doc.getString("direccion").orEmpty(),
                    horario = doc.getString("horario").orEmpty()
                )
                uidCargado = usuario.uid
                alTerminar()
            }
    }

    /** Guarda el perfil en memoria y los datos editables en Firestore. */
    fun guardar(nuevo: PerfilVendedor) {
        perfil = nuevo
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("usuarios").document(uid)
            .update(
                mapOf(
                    "nombre" to nuevo.nombre,
                    "telefono" to nuevo.telefono,
                    "negocio" to nuevo.negocio,
                    "direccion" to nuevo.direccion,
                    "horario" to nuevo.horario
                )
            )
    }
}