package com.sasobakery.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// datos del comprador
data class PerfilComprador(
    val nombre: String,
    val correo: String,
    val telefono: String = ""
) {
    // primeras letras de las dos primeras palabras del nombre
    val iniciales: String
        get() = nombre.trim()
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
}

// perfil del comprador en memoria, con sus pedidos y puntos para la pantalla de perfil
object PerfilCompradorRepositorio {
    // cada 1000 pesos en pedidos es un punto
    const val PESOS_POR_PUNTO = 1000

    // perfil de la cuenta actual
    private var perfil = PerfilComprador(nombre = "", correo = "")
    // cuenta a la que pertenece el perfil cargado
    private var uidCargado: String? = null

    // copia de los pedidos para contar activos y puntos
    private var pedidos: List<PedidoGestion> = emptyList()

    // interruptor de la pantalla, solo queda en memoria
    var notificaciones = true

    // devuelve el perfil, si cambio la cuenta lo reinicia con el correo
    fun obtener(): PerfilComprador {
        val usuario = FirebaseAuth.getInstance().currentUser
        if (usuario?.uid != uidCargado) {
            pedidos = emptyList()
            val correo = usuario?.email.orEmpty()
            perfil = PerfilComprador(nombre = correo.substringBefore("@"), correo = correo)
        }
        return perfil
    }

    // trae nombre y telefono de firestore, si ya estaban cargados avisa de una
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

    // guarda nombre y telefono en firestore
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

    // guarda la lista de pedidos para los contadores
    fun actualizarPedidos(lista: List<PedidoGestion>) {
        pedidos = lista
    }

    fun misPedidos(): List<PedidoGestion> = pedidos

    // pedidos que no estan cancelados ni entregados
    fun pedidosActivos(): Int =
        misPedidos().count { !it.cancelado && it.estado != EstadoPedido.ENTREGADO }

    // un punto por cada 1000 pesos de pedidos no cancelados
    fun puntos(): Int =
        misPedidos().filter { !it.cancelado }.sumOf { it.total } / PESOS_POR_PUNTO
}