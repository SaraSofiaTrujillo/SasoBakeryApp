package com.sasobakery.app

import java.util.Calendar

// las dos formas de pago
enum class TipoPago { ONLINE, CONTRA_ENTREGA }

// una opcion de la pantalla de pago
data class MetodoPago(
    val id: String,
    val tipo: TipoPago,
    val badge: String,
    val titulo: String,
    val detalle: String
)

// guarda el metodo elegido y arma el pedido final
object PagoRepositorio {
    const val ID_ONLINE = "online"
    const val ID_CONTRA_ENTREGA = "contra_entrega"

    // metodo que eligio el comprador
    var idSeleccionado: String? = null

    // opciones de pago, el texto cambia si es domicilio o recoger
    fun metodos(): List<MetodoPago> {
        val alRecibir = EntregaRepositorio.tipo == TipoEntrega.ENTREGA
        return listOf(
            MetodoPago(
                ID_ONLINE, TipoPago.ONLINE, "PayU",
                "Pagar en línea", "Tarjeta, PSE u otros medios con PayU"
            ),
            MetodoPago(
                ID_CONTRA_ENTREGA, TipoPago.CONTRA_ENTREGA, "$",
                if (alRecibir) "Pago contra entrega" else "Pago al recoger",
                if (alRecibir) "Pagas cuando recibas tu pedido" else "Pagas cuando recojas tu pedido"
            )
        )
    }

    // el elegido o el primero si no ha elegido
    fun metodoElegido(): MetodoPago {
        val todos = metodos()
        return todos.firstOrNull { it.id == idSeleccionado } ?: todos.first()
    }

    // ultimo error para mostrarlo en pantalla
    var mensajeError: String? = null
        private set

    // revisa carrito, telefono y stock antes de abrir payu, devuelve el problema o null
    fun validarAntesDePagar(): String? {
        val items = CarritoRepositorio.obtenerItems()
        if (items.isEmpty()) return "Tu carrito está vacío"
        if (PerfilCompradorRepositorio.obtener().telefono.isBlank()) {
            return "Agrega tu teléfono en Perfil → Editar para que la tienda pueda contactarte"
        }
        val sinStock = items.firstOrNull { it.cantidad > it.producto.stock }
        if (sinStock != null) {
            return "Ya no hay suficiente stock de ${sinStock.producto.nombre}. Revisa tu carrito."
        }
        return null
    }

    // crea el pedido, si fue pago en linea solo si payu aprobo y se verifico
    fun confirmarPedido(pago: RespuestaPayu? = null, alTerminar: (PedidoGestion?) -> Unit) {
        mensajeError = null

        if (pago != null && !(pago.aprobada && pago.verificada)) {
            mensajeError = "El pago no está aprobado"
            alTerminar(null)
            return
        }

        val items = CarritoRepositorio.obtenerItems()
        if (items.isEmpty()) {
            mensajeError = "Tu carrito está vacío"
            alTerminar(null)
            return
        }

        if (PerfilCompradorRepositorio.obtener().telefono.isBlank()) {
            mensajeError = "Agrega tu teléfono en Perfil → Editar para que la tienda pueda contactarte"
            alTerminar(null)
            return
        }

        // datos del pedido, entrega y pago
        val domicilio = EntregaRepositorio.tipo == TipoEntrega.ENTREGA
        val metodo = metodoElegido()
        val tienda = PerfilVendedorRepositorio.tienda()
        val ahora = Calendar.getInstance()
        val hora = "${ahora.get(Calendar.HOUR_OF_DAY)}:" +
                String.format(java.util.Locale.US, "%02d", ahora.get(Calendar.MINUTE))

        val base = PedidoGestion(
            codigo = "",
            cliente = PerfilCompradorRepositorio.obtener().nombre,
            clienteTelefono = PerfilCompradorRepositorio.obtener().telefono,
            // ubicacion solo si es a domicilio
            latitud = if (domicilio) EntregaRepositorio.latitud else null,
            longitud = if (domicilio) EntregaRepositorio.longitud else null,
            fecha = "Hoy, $hora",
            horario = EntregaRepositorio.horario,
            tipo = EntregaRepositorio.tipo,
            productos = emptyList(),
            estado = EstadoPedido.PENDIENTE,
            fechaEntrega = EntregaRepositorio.fechaElegida().texto,
            direccion = if (domicilio) EntregaRepositorio.direccion else "",
            envio = EntregaRepositorio.costoEnvio(),
            metodoPago = if (pago != null) "Pago en línea (PayU)" else metodo.titulo,
            pagoProveedor = if (pago != null) "PayU" else "",
            pagoEstado = if (pago != null) "APROBADO" else "CONTRA_ENTREGA",
            pagoReferencia = pago?.referencia.orEmpty(),
            pagoTransaccion = pago?.transaccion.orEmpty(),
            tiendaNombre = tienda.negocio,
            tiendaTelefono = tienda.telefono,
            tiendaDireccion = tienda.direccion
        )

        // si se crea bien se vacia el carrito
        PedidosRepositorio.crear(items, base) { pedido, error ->
            if (pedido == null) {
                mensajeError = error
                alTerminar(null)
                return@crear
            }

            CarritoRepositorio.vaciar()
            alTerminar(pedido)
        }
    }
}