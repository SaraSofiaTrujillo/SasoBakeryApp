package com.sasobakery.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration

// datos de la tienda que ven todos los usuarios
data class DatosNegocio(
    val nombre: String,
    val telefono: String,
    val direccion: String,
    val horario: String
)

// datos del negocio, un solo documento que leen todos y solo edita el admin
object NegocioRepositorio {
    private const val COLECCION = "negocio"
    private const val DOCUMENTO = "principal"
    private val db get() = FirebaseFirestore.getInstance()

    // se usan si el documento aun no existe
    val porDefecto = DatosNegocio(
        nombre = "Saso Bakery",
        telefono = "+57 300 123 4567",
        direccion = "Cra 7 # 45-20, Bogotá",
        horario = "Lun - Sáb · 8:00 a. m. - 6:00 p. m."
    )

    // ultimos datos que llegaron
    private var actual = porDefecto

    fun obtener(): DatosNegocio = actual

    // escucha cambios del negocio, los campos vacios usan los por defecto
    fun escuchar(
        alCambiar: (DatosNegocio) -> Unit,
        alFallar: (String) -> Unit
    ): ListenerRegistration =
        db.collection(COLECCION).document(DOCUMENTO).addSnapshotListener { doc, error ->
            if (error != null || doc == null) {
                alFallar("No se pudieron cargar los datos del negocio.")
                return@addSnapshotListener
            }
            actual = if (doc.exists()) {
                DatosNegocio(
                    nombre = doc.getString("nombre").orEmpty().ifBlank { porDefecto.nombre },
                    telefono = doc.getString("telefono").orEmpty().ifBlank { porDefecto.telefono },
                    direccion = doc.getString("direccion").orEmpty().ifBlank { porDefecto.direccion },
                    horario = doc.getString("horario").orEmpty().ifBlank { porDefecto.horario }
                )
            } else {
                porDefecto
            }
            alCambiar(actual)
        }

    // guarda los datos, solo el admin tiene permiso
    fun guardar(datos: DatosNegocio, alTerminar: (String?) -> Unit) {
        db.collection(COLECCION).document(DOCUMENTO).set(
            mapOf(
                "nombre" to datos.nombre,
                "telefono" to datos.telefono,
                "direccion" to datos.direccion,
                "horario" to datos.horario
            )
        )
            .addOnSuccessListener { alTerminar(null) }
            .addOnFailureListener { alTerminar(mensajeDeError(it)) }
    }

    // mensaje segun el error
    private fun mensajeDeError(error: Exception): String =
        if (error is FirebaseFirestoreException &&
            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        ) {
            "Solo un administrador puede modificar los datos del negocio."
        } else {
            "No se pudo guardar. Revisa tu conexión e intenta de nuevo."
        }
}