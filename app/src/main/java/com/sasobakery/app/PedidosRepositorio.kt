package com.sasobakery.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction

/**
 * Pedidos guardados en la colección "pedidos" de Firestore.
 * Por ahora solo sabe crear un pedido; las consultas y el cambio de estado
 * se agregan en las tareas siguientes.
 */
object PedidosRepositorio {

    private const val COLECCION = "pedidos"
    private const val PRIMER_NUMERO = 1051 // el primer pedido real es #SB-1051
    private val db get() = FirebaseFirestore.getInstance()

    // Un producto del pedido, tal como está en Firestore en el momento de pagar
    private class Linea(
        val id: String,
        val nombre: String,
        val precio: Int,
        val cantidad: Int,
        val stock: Int
    )

    // Resultado de la transacción: o se creó el pedido, o se rechazó con un motivo
    private sealed class Resultado {
        class Listo(val numero: Int, val lineas: List<Linea>) : Resultado()
        class Rechazado(val mensaje: String) : Resultado()
    }

    /**
     * Crea el pedido y descuenta el stock en una sola operación: si algo falla,
     * no se guarda nada. [base] trae los datos de entrega, pago y negocio; el código,
     * el comprador y los productos se completan aquí con los datos reales de Firestore.
     * [alTerminar] recibe el pedido creado, o null y el motivo del error.
     */
    fun crear(
        items: List<ItemCarrito>,
        base: PedidoGestion,
        alTerminar: (PedidoGestion?, String?) -> Unit
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            alTerminar(null, "Inicia sesión para hacer tu pedido")
            return
        }

        val refPedido = db.collection(COLECCION).document() // Firestore inventa el id
        db.runTransaction { tx -> ejecutar(tx, uid, refPedido, items, base) }
            .addOnSuccessListener { resultado ->
                when (resultado) {
                    is Resultado.Rechazado -> alTerminar(null, resultado.mensaje)
                    is Resultado.Listo -> alTerminar(
                        base.copy(
                            id = refPedido.id,
                            codigo = "#SB-${resultado.numero}",
                            compradorUid = uid,
                            productos = resultado.lineas.map {
                                ProductoPedido(it.nombre, it.cantidad, it.precio)
                            }
                        ),
                        null
                    )
                }
            }
            .addOnFailureListener { alTerminar(null, mensajeDeError(it)) }
    }

    // Lo que ocurre dentro de la transacción. Regla de Firestore: primero TODAS las
    // lecturas y después las escrituras.
    private fun ejecutar(
        tx: Transaction,
        uid: String,
        refPedido: DocumentReference,
        items: List<ItemCarrito>,
        base: PedidoGestion
    ): Resultado {
        val refContador = db.collection("contadores").document("pedidos")

        // ---- 1) Lecturas ----
        val contador = tx.get(refContador)
        val ultimo = if (contador.exists()) {
            (contador.getLong("ultimo") ?: (PRIMER_NUMERO - 1).toLong()).toInt()
        } else {
            PRIMER_NUMERO - 1
        }

        val refsProductos = items.map { db.collection("productos").document(it.producto.id) }
        val lineas = ArrayList<Linea>()
        items.forEachIndexed { i, item ->
            val doc = tx.get(refsProductos[i])
            val nombre = doc.getString("nombre").orEmpty().ifBlank { item.producto.nombre }
            if (!doc.exists() || doc.getBoolean("disponible") == false) {
                return Resultado.Rechazado("$nombre ya no está disponible. Revisa tu carrito.")
            }
            val stock = (doc.getLong("stock") ?: 0L).toInt()
            if (item.cantidad > stock) {
                return Resultado.Rechazado(
                    "Ya no hay suficiente stock de $nombre. Revisa tu carrito."
                )
            }
            val precio = (doc.getLong("precio") ?: 0L).toInt() // el precio manda el de Firestore
            lineas.add(Linea(doc.id, nombre, precio, item.cantidad, stock))
        }

        // ---- 2) Escrituras ----
        val numero = ultimo + 1
        val subtotal = lineas.sumOf { it.precio * it.cantidad }

        if (contador.exists()) {
            tx.update(refContador, "ultimo", numero)
        } else {
            tx.set(refContador, mapOf("ultimo" to numero))
        }

        lineas.forEachIndexed { i, linea ->
            tx.update(refsProductos[i], "stock", linea.stock - linea.cantidad)
        }

        tx.set(
            refPedido,
            mapOf(
                "codigo" to "#SB-$numero",
                "compradorUid" to uid,
                "cliente" to base.cliente,
                "estado" to base.estado.name,
                "cancelado" to false,
                "tipo" to base.tipo.name,
                "fechaEntrega" to base.fechaEntrega,
                "horario" to base.horario,
                "direccion" to base.direccion,
                "metodoPago" to base.metodoPago,
                "envio" to base.envio,
                "subtotal" to subtotal,
                "total" to subtotal + base.envio,
                "productos" to lineas.map {
                    mapOf(
                        "productoId" to it.id,
                        "nombre" to it.nombre,
                        "cantidad" to it.cantidad,
                        "precioUnitario" to it.precio
                    )
                },
                "tiendaNombre" to base.tiendaNombre,
                "tiendaTelefono" to base.tiendaTelefono,
                "tiendaDireccion" to base.tiendaDireccion,
                "fechaTexto" to base.fecha,
                "creado" to FieldValue.serverTimestamp()
            )
        )
        return Resultado.Listo(numero, lineas)
    }

    private fun mensajeDeError(error: Exception): String =
        if (error is FirebaseFirestoreException &&
            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        ) {
            "No tienes permiso para crear el pedido."
        } else {
            "No se pudo crear el pedido. Revisa tu conexión e intenta de nuevo."
        }
}