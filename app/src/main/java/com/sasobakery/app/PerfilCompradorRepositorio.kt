package com.sasobakery.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Datos del comprador
data class PerfilComprador(
    val nombre: String,
    val correo: String,
    val telefono: String = ""
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
 * Perfil del comprador que tiene la sesión iniciada. El nombre y el teléfono se leen de
 * Firestore (colección "usuarios") y el correo viene de Firebase Authentication.
 * Los pedidos que se muestran en el perfil llegan de Firestore (los entrega
 * PerfilCompradorActivity al escucharlos).
 */
object PerfilCompradorRepositorio {

    // ===== VALOR QUE PUEDES CAMBIAR =====
    const val PESOS_POR_PUNTO = 1000 // 1 punto Saso por cada $ 1.000 comprados
    // ====================================

    private var perfil = PerfilComprador(nombre = "", correo = "")
    private var uidCargado: String? = null // de qué cuenta es el perfil guardado en memoria

    // Pedidos reales del comprador en sesión
    private var pedidos: List<PedidoGestion> = emptyList()

    // Preferencia guardada (todavía no se envían avisos reales)
    var notificaciones = true

    /**
     * Devuelve el perfil ya cargado. Si todavía no llegó de Firestore, o si cambió la
     * cuenta, muestra algo provisional (la parte del correo antes de la @).
     */
    fun obtener(): PerfilComprador {
        val usuario = FirebaseAuth.getInstance().currentUser
        if (usuario?.uid != uidCargado) {
            pedidos = emptyList() // los pedidos en memoria eran de otra cuenta
            val correo = usuario?.email.orEmpty()
            perfil = PerfilComprador(nombre = correo.substringBefore("@"), correo = correo)
        }
        return perfil
    }

    /**
     * Lee nombre y teléfono reales desde Firestore y avisa cuando termina. Si el perfil
     * de esta cuenta ya estaba cargado, avisa de inmediato sin volver a consultar.
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
                    correo = correo,
                    telefono = doc.getString("telefono").orEmpty()
                )
                uidCargado = usuario.uid
                alTerminar()
            }
    }

    /**
     * Guarda nombre y teléfono en Firestore. [alTerminar] recibe null si salió bien,
     * o el mensaje de error.
     */
    fun guardar(nuevo: PerfilComprador, alTerminar: (String?) -> Unit = {}) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            alTerminar("No hay sesión iniciada")
            return
        }
        FirebaseFirestore.getInstance().collection("usuarios").document(uid)
            .update(mapOf("nombre" to nuevo.nombre, "telefono" to nuevo.telefono))
            .addOnSuccessListener {
                perfil = nuevo
                uidCargado = uid
                alTerminar(null)
            }
            .addOnFailureListener {
                alTerminar("No se pudo guardar. Revisa tu conexión e intenta de nuevo.")
            }
    }

    /** Recibe los pedidos reales que llegan de Firestore. */
    fun actualizarPedidos(lista: List<PedidoGestion>) {
        pedidos = lista
    }

    /** Pedidos del comprador en sesión. */
    fun misPedidos(): List<PedidoGestion> = pedidos

    /** Pedidos que todavía no terminan: ni entregados ni cancelados. */
    fun pedidosActivos(): Int =
        misPedidos().count { !it.cancelado && it.estado != EstadoPedido.ENTREGADO }

    /** Puntos Saso: salen de lo comprado en pedidos no cancelados. */
    fun puntos(): Int =
        misPedidos().filter { !it.cancelado }.sumOf { it.total } / PESOS_POR_PUNTO
}