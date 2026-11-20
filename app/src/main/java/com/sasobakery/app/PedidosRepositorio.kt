package com.sasobakery.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.Locale
import com.google.firebase.firestore.GeoPoint

// todo lo de pedidos en firestore, crear, escuchar, avanzar y cancelar
object PedidosRepositorio {
    private const val COLECCION = "pedidos"
    // el primer pedido es el 1051
    private const val PRIMER_NUMERO = 1051
    private val db get() = FirebaseFirestore.getInstance()

    // producto leido en la transaccion con su precio y stock real
    private class Linea(
        val id: String,
        val nombre: String,
        val precio: Int,
        val cantidad: Int,
        val stock: Int
    )

    // lo que devuelve la transaccion, listo o rechazado
    private sealed class Resultado {
        class Listo(val numero: Int, val lineas: List<Linea>) : Resultado()
        class Rechazado(val mensaje: String) : Resultado()
    }

    // crea el pedido en una transaccion, si algo falla no se guarda nada
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

        val refPedido = db.collection(COLECCION).document()
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

    // dentro de la transaccion van primero las lecturas y despues las escrituras
    private fun ejecutar(
        tx: Transaction,
        uid: String,
        refPedido: DocumentReference,
        items: List<ItemCarrito>,
        base: PedidoGestion
    ): Resultado {
        val refContador = db.collection("contadores").document("pedidos")

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
            // el precio es el de firestore, no el del carrito
            val precio = (doc.getLong("precio") ?: 0L).toInt()
            lineas.add(Linea(doc.id, nombre, precio, item.cantidad, stock))
        }

        // desde aqui son escrituras
        val numero = ultimo + 1
        val subtotal = lineas.sumOf { it.precio * it.cantidad }

        if (contador.exists()) {
            tx.update(refContador, "ultimo", numero)
        } else {
            tx.set(refContador, mapOf("ultimo" to numero))
        }

        // descuenta el stock
        lineas.forEachIndexed { i, linea ->
            tx.update(refsProductos[i], "stock", linea.stock - linea.cantidad)
        }

        // guarda el pedido con los datos opcionales al final
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
            ) + datosDeContacto(base)
        )
        return Resultado.Listo(numero, lineas)
    }

    // pedidos del comprador en tiempo real, el mas nuevo primero
    fun escucharMios(
        alCambiar: (List<PedidoGestion>) -> Unit,
        alFallar: (String) -> Unit
    ): ListenerRegistration {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            alCambiar(emptyList())
            return ListenerRegistration { }
        }
        return db.collection(COLECCION).whereEqualTo("compradorUid", uid)
            .addSnapshotListener { instantanea, error ->
                if (error != null || instantanea == null) {
                    alFallar("No se pudieron cargar tus pedidos.")
                    return@addSnapshotListener
                }
                val lista = instantanea.documents
                    .sortedByDescending { creadoEnMilisegundos(it) }
                    .map { aPedido(it) }
                alCambiar(lista)
            }
    }

    // escucha un solo pedido para el seguimiento
    fun escucharPedido(
        id: String,
        alCambiar: (PedidoGestion?) -> Unit,
        alFallar: (String) -> Unit
    ): ListenerRegistration =
        db.collection(COLECCION).document(id).addSnapshotListener { doc, error ->
            if (error != null || doc == null) {
                alFallar("No se pudo cargar el pedido.")
                return@addSnapshotListener
            }
            alCambiar(if (doc.exists()) aPedido(doc) else null)
        }

    // fecha de creacion, si el servidor aun no la pone queda de primero
    private fun creadoEnMilisegundos(doc: DocumentSnapshot): Long =
        doc.getTimestamp("creado", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time ?: Long.MAX_VALUE

    // pasa el documento de firestore a PedidoGestion
    private fun aPedido(doc: DocumentSnapshot): PedidoGestion {
        val creado = creadoEnMilisegundos(doc)
        val productos = (doc.get("productos") as? List<*>).orEmpty().mapNotNull { item ->
            val linea = item as? Map<*, *> ?: return@mapNotNull null
            ProductoPedido(
                nombre = linea["nombre"] as? String ?: "",
                cantidad = (linea["cantidad"] as? Number)?.toInt() ?: 0,
                precioUnitario = (linea["precioUnitario"] as? Number)?.toInt() ?: 0
            )
        }
        return PedidoGestion(
            codigo = doc.getString("codigo").orEmpty(),
            cliente = doc.getString("cliente").orEmpty(),
            clienteTelefono = doc.getString("clienteTelefono").orEmpty(),
            latitud = doc.getGeoPoint("ubicacion")?.latitude,
            longitud = doc.getGeoPoint("ubicacion")?.longitude,
            pagoEstado = doc.getString("pagoEstado").orEmpty(),
            pagoProveedor = doc.getString("pagoProveedor").orEmpty(),
            pagoReferencia = doc.getString("pagoReferencia").orEmpty(),
            pagoTransaccion = doc.getString("pagoTransaccion").orEmpty(),
            fecha = if (creado == Long.MAX_VALUE) doc.getString("fechaTexto").orEmpty()
            else textoFecha(creado),
            horario = doc.getString("horario").orEmpty(),
            tipo = TipoEntrega.values().firstOrNull { it.name == doc.getString("tipo") }
                ?: TipoEntrega.RECOJO,
            productos = productos,
            estado = EstadoPedido.values().firstOrNull { it.name == doc.getString("estado") }
                ?: EstadoPedido.PENDIENTE,
            cancelado = doc.getBoolean("cancelado") == true,
            fechaEntrega = doc.getString("fechaEntrega").orEmpty(),
            direccion = doc.getString("direccion").orEmpty(),
            envio = (doc.getLong("envio") ?: 0L).toInt(),
            metodoPago = doc.getString("metodoPago").orEmpty(),
            id = doc.id,
            compradorUid = doc.getString("compradorUid").orEmpty(),
            tiendaNombre = doc.getString("tiendaNombre").orEmpty(),
            tiendaTelefono = doc.getString("tiendaTelefono").orEmpty(),
            tiendaDireccion = doc.getString("tiendaDireccion").orEmpty()
        )
    }

    private val meses = listOf(
        "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"
    )

    // pone la fecha como hoy, ayer o dia y mes
    private fun textoFecha(milisegundos: Long): String {
        val fecha = Calendar.getInstance().apply { timeInMillis = milisegundos }
        val hoy = Calendar.getInstance()
        val ayer = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val hora = String.format(
            Locale.US, "%d:%02d", fecha.get(Calendar.HOUR_OF_DAY), fecha.get(Calendar.MINUTE)
        )
        fun mismoDia(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                    a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        return when {
            mismoDia(fecha, hoy) -> "Hoy, $hora"
            mismoDia(fecha, ayer) -> "Ayer, $hora"
            else -> "${fecha.get(Calendar.DAY_OF_MONTH)} ${meses[fecha.get(Calendar.MONTH)]}, $hora"
        }
    }

    // campos opcionales, solo se guardan los que tienen algo
    private fun datosDeContacto(base: PedidoGestion): Map<String, Any> {
        val datos = mutableMapOf<String, Any>()
        if (base.clienteTelefono.isNotBlank()) datos["clienteTelefono"] = base.clienteTelefono
        val latitud = base.latitud
        val longitud = base.longitud
        if (latitud != null && longitud != null) datos["ubicacion"] = GeoPoint(latitud, longitud)
        if (base.pagoEstado.isNotBlank()) datos["pagoEstado"] = base.pagoEstado
        if (base.pagoProveedor.isNotBlank()) datos["pagoProveedor"] = base.pagoProveedor
        if (base.pagoReferencia.isNotBlank()) datos["pagoReferencia"] = base.pagoReferencia
        if (base.pagoTransaccion.isNotBlank()) datos["pagoTransaccion"] = base.pagoTransaccion
        return datos
    }

    // todos los pedidos, para vendedor y admin
    fun escucharTodos(
        alCambiar: (List<PedidoGestion>) -> Unit,
        alFallar: (String) -> Unit
    ): ListenerRegistration =
        db.collection(COLECCION).addSnapshotListener { instantanea, error ->
            if (error != null || instantanea == null) {
                alFallar("No se pudieron cargar los pedidos.")
                return@addSnapshotListener
            }
            alCambiar(
                instantanea.documents
                    .sortedByDescending { creadoEnMilisegundos(it) }
                    .map { aPedido(it) }
            )
        }

    // pasa al siguiente estado, revisa el estado actual para no saltarse pasos
    fun avanzar(pedido: PedidoGestion, alTerminar: (String?) -> Unit) {
        if (pedido.id.isEmpty()) {
            alTerminar("Este pedido no se puede modificar")
            return
        }
        val ref = db.collection(COLECCION).document(pedido.id)
        db.runTransaction<String?> { tx ->
            val doc = tx.get(ref)
            if (!doc.exists()) return@runTransaction "El pedido ya no existe"
            if (doc.getBoolean("cancelado") == true) return@runTransaction "El pedido fue cancelado"
            if (doc.getString("estado") != pedido.estado.name) {
                return@runTransaction "El pedido ya cambió de estado. Revisa la lista."
            }
            val siguiente = pedido.siguienteEstado ?: return@runTransaction "El pedido ya terminó"
            tx.update(ref, "estado", siguiente.name)
            null
        }
            .addOnSuccessListener { alTerminar(it) }
            .addOnFailureListener { alTerminar(mensajeDeError(it)) }
    }

    // cancela y devuelve el stock, un pedido entregado no se cancela
    fun cancelar(pedido: PedidoGestion, alTerminar: (String?) -> Unit) {
        if (pedido.id.isEmpty()) {
            alTerminar("Este pedido no se puede modificar")
            return
        }
        val ref = db.collection(COLECCION).document(pedido.id)
        db.runTransaction<String?> { tx ->
            val doc = tx.get(ref)
            if (!doc.exists()) return@runTransaction "El pedido ya no existe"
            if (doc.getBoolean("cancelado") == true) return@runTransaction "El pedido ya estaba cancelado"
            if (doc.getString("estado") == EstadoPedido.ENTREGADO.name) {
                return@runTransaction "Un pedido entregado no se puede cancelar"
            }
            // se leen los productos y despues se les suma lo devuelto
            val devoluciones = (doc.get("productos") as? List<*>).orEmpty().mapNotNull { item ->
                val linea = item as? Map<*, *> ?: return@mapNotNull null
                val id = linea["productoId"] as? String ?: return@mapNotNull null
                val cantidad = (linea["cantidad"] as? Number)?.toInt() ?: return@mapNotNull null
                val refProducto = db.collection("productos").document(id)
                Triple(refProducto, tx.get(refProducto), cantidad)
            }
            devoluciones.forEach { (refProducto, producto, cantidad) ->
                if (producto.exists()) {
                    tx.update(refProducto, "stock", (producto.getLong("stock") ?: 0L) + cantidad)
                }
            }
            tx.update(ref, "cancelado", true)
            null
        }
            .addOnSuccessListener { alTerminar(it) }
            .addOnFailureListener { alTerminar(mensajeDeError(it)) }
    }

    // mensaje segun el error
    private fun mensajeDeError(error: Exception): String =
        if (error is FirebaseFirestoreException &&
            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        ) {
            "No tienes permiso para crear el pedido."
        } else {
            "No se pudo crear el pedido. Revisa tu conexión e intenta de nuevo."
        }
}