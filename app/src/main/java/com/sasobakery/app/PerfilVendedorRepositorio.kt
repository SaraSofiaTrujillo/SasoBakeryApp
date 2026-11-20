package com.sasobakery.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// datos del vendedor mas los de la tienda
data class PerfilVendedor(
    val nombre: String,
    val correo: String,
    val telefono: String,
    val negocio: String,
    val direccion: String,
    val horario: String
) {
    // primeras letras de las dos primeras palabras del nombre
    val iniciales: String
        get() = nombre.trim()
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
}

// perfil del vendedor en memoria, el negocio sale de NegocioRepositorio
object PerfilVendedorRepositorio {
    private var nombre = ""
    private var correo = ""
    private var telefono = ""
    // cuenta a la que pertenece el perfil cargado
    private var uidCargado: String? = null

    // devuelve el perfil, si cambio la cuenta lo reinicia con el correo
    fun obtener(): PerfilVendedor {
        val usuario = FirebaseAuth.getInstance().currentUser
        if (usuario?.uid != uidCargado) {
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

    // solo los datos de la tienda, se usan en los pedidos
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

    // trae nombre y telefono de firestore, si ya estaban cargados avisa de una
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

    // guarda nombre y telefono en firestore
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