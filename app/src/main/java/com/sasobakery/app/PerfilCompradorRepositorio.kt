package com.sasobakery.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Datos del comprador
data class PerfilComprador(
    val nombre: String,
    val correo: String
) {
    // "Valentina Mendoza" -> "VM" (para el avatar)
    val iniciales: String
        get() = nombre.trim()
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
}

/**
 * Perfil del comprador que tiene la sesión iniciada. El nombre se lee de Firestore
 * (colección "usuarios") y el correo viene de Firebase Authentication.
 * Los pedidos y los puntos siguen en memoria hasta que los conectemos (Día 4).
 */
object PerfilCompradorRepositorio {

    // ===== VALOR QUE PUEDES CAMBIAR =====
    const val PESOS_POR_PUNTO = 1000 // 1 punto Saso por cada $ 1.000 comprados
    // ====================================

    private var perfil = PerfilComprador(nombre = "", correo = "")
    private var uidCargado: String? = null // de qué cuenta es el perfil guardado en memoria

    // Preferencia guardada (todavía no se envían avisos reales)
    var notificaciones = true

    /**
     * Devuelve el perfil ya cargado. Si todavía no llegó de Firestore, o si cambió la
     * cuenta, muestra algo provisional (la parte del correo antes de la @).
     */
    fun obtener(): PerfilComprador {
        val usuario = FirebaseAuth.getInstance().currentUser
        if (usuario?.uid != uidCargado) {
            val correo = usuario?.email.orEmpty()
            perfil = PerfilComprador(nombre = correo.substringBefore("@"), correo = correo)
        }
        return perfil
    }

    /**
     * Lee el nombre real desde Firestore y avisa cuando termina. Si el perfil de esta
     * cuenta ya estaba cargado, avisa de inmediato sin volver a consultar.
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
                perfil = PerfilComprador(
                    nombre = doc.getString("nombre").orEmpty()
                        .ifBlank { correo.substringBefore("@") },
                    correo = correo
                )
                uidCargado = usuario.uid
                alTerminar()
            }
    }

    /** Guarda el perfil en memoria y el nombre nuevo en Firestore. */
    fun guardar(nuevo: PerfilComprador) {
        perfil = nuevo
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("usuarios").document(uid)
            .update("nombre", nuevo.nombre)
    }

    /** Pedidos del comprador en sesión (por ahora se reconocen por su nombre). */
    fun misPedidos(): List<PedidoGestion> =
        PedidosVendedorRepositorio.obtenerTodos()
            .filter { it.cliente == obtener().nombre }

    /** Pedidos que todavía no terminan: ni entregados ni cancelados. */
    fun pedidosActivos(): Int =
        misPedidos().count { !it.cancelado && it.estado != EstadoPedido.ENTREGADO }

    /** Puntos Saso: salen de lo comprado en pedidos no cancelados. */
    fun puntos(): Int =
        misPedidos().filter { !it.cancelado }.sumOf { it.total } / PESOS_POR_PUNTO
}