package com.sasobakery.app

import java.util.Calendar

// Los dos tipos de pago de la tienda: en línea (pasarela PayU) o al recibir/recoger
enum class TipoPago { ONLINE, CONTRA_ENTREGA }

// Una opción de pago tal como se ve en la lista.
// "badge" es el texto corto del cuadrito (PayU, $)
data class MetodoPago(
    val id: String,
    val tipo: TipoPago,
    val badge: String,
    val titulo: String,
    val detalle: String
)

// Lo que el comprador elige en la pantalla de Método de pago (solo en memoria).
// La app nunca guarda datos de tarjeta: el pago en línea se hace en la página de PayU.
object PagoRepositorio {

    const val ID_ONLINE = "online"
    const val ID_CONTRA_ENTREGA = "contra_entrega"

    // null = el comprador todavía no ha tocado ninguna opción
    var idSeleccionado: String? = null

    /** Las opciones de pago. El texto de contra entrega cambia si el comprador recoge en tienda. */
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

    /** El método elegido; si todavía no eligió ninguno, el primero de la lista. */
    fun metodoElegido(): MetodoPago {
        val todos = metodos()
        return todos.firstOrNull { it.id == idSeleccionado } ?: todos.first()
    }

    // Si confirmarPedido() devuelve null, aquí queda el motivo para mostrárselo al comprador
    var mensajeError: String? = null
        private set

    /**
     * Revisa lo que se puede comprobar ANTES de abrir PayU, para no cobrar un pedido que
     * luego no se pueda crear. Devuelve el motivo del problema, o null si todo está bien.
     */
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

    /**
     * Convierte el carrito en un pedido guardado en Firestore: crea el pedido, descuenta
     * el stock y vacía el carrito. [alTerminar] recibe el pedido, o null si no se pudo
     * (el motivo queda en [mensajeError]).
     */
    fun confirmarPedido(pago: RespuestaPayu? = null, alTerminar: (PedidoGestion?) -> Unit) {
        mensajeError = null

        // Con pago en línea solo se crea el pedido si PayU lo aprobó y se pudo verificar
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

        // Sin teléfono la tienda no podría contactar al comprador por su pedido
        if (PerfilCompradorRepositorio.obtener().telefono.isBlank()) {
            mensajeError = "Agrega tu teléfono en Perfil → Editar para que la tienda pueda contactarte"
            alTerminar(null)
            return
        }

        val domicilio = EntregaRepositorio.tipo == TipoEntrega.ENTREGA
        val metodo = metodoElegido()
        val tienda = PerfilVendedorRepositorio.tienda()
        val ahora = Calendar.getInstance()
        val hora = "${ahora.get(Calendar.HOUR_OF_DAY)}:" +
                String.format(java.util.Locale.US, "%02d", ahora.get(Calendar.MINUTE))

        // Datos que ya conoce la app. El código, el comprador y los productos con su
        // precio real los completa PedidosRepositorio dentro de la transacción.
        val base = PedidoGestion(
            codigo = "",
            cliente = PerfilCompradorRepositorio.obtener().nombre,
            clienteTelefono = PerfilCompradorRepositorio.obtener().telefono,
            // Las coordenadas solo viajan si la entrega es a domicilio
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