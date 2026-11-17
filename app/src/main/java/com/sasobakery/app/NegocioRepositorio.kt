package com.sasobakery.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration

// Datos de contacto de la tienda
data class DatosNegocio(
    val nombre: String,
    val telefono: String,
    val direccion: String,
    val horario: String
)

/**
 * Datos de Saso Bakery, la única tienda de la app. Viven en un solo documento de
 * Firestore (negocio/principal): lo leen todos los usuarios con sesión y solo lo
 * modifica el administrador.
 */
object NegocioRepositorio {

    private const val COLECCION = "negocio"
    private const val DOCUMENTO = "principal"
    private val db get() = FirebaseFirestore.getInstance()

    // Lo que se muestra mientras el administrador no haya guardado los datos reales
    val porDefecto = DatosNegocio(
        nombre = "Saso Bakery",
        telefono = "+57 300 123 4567",
        direccion = "Cra 7 # 45-20, Bogotá",
        horario = "Lun - Sáb · 8:00 a. m. - 6:00 p. m."
    )

    // Última copia recibida de Firestore (se actualiza sola mientras hay una escucha activa)
    private var actual = porDefecto

    /** Los datos del negocio que ya llegaron (o los de por defecto). */
    fun obtener(): DatosNegocio = actual

    /**
     * Escucha el documento del negocio: [alCambiar] se llama al empezar y cada vez que el
     * administrador lo modifica. Hay que cancelar el ListenerRegistration cuando la
     * pantalla deja de verse.
     */
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

    /** Guarda los datos del negocio. [alTerminar] recibe null si salió bien, o el error. */
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

    private fun mensajeDeError(error: Exception): String =
        if (error is FirebaseFirestoreException &&
            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        ) {
            "Solo un administrador puede modificar los datos del negocio."
        } else {
            "No se pudo guardar. Revisa tu conexión e intenta de nuevo."
        }
}