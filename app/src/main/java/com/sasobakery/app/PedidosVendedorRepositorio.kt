package com.sasobakery.app

data class ProductoPedido(
    val nombre: String,
    val cantidad: Int,
    val precioUnitario: Int
) {
    val subtotal: Int get() = cantidad * precioUnitario
}

enum class TipoEntrega(val etiqueta: String) {
    ENTREGA("Entrega"),
    RECOJO("Recojo")
}

data class PedidoGestion(
    val codigo: String,
    val cliente: String,
    val fecha: String,
    val horario: String,
    val tipo: TipoEntrega,
    val productos: List<ProductoPedido>,
    val estado: EstadoPedido,
    val cancelado: Boolean = false,
    val fechaEntrega: String = "",
    val direccion: String = "",
    val envio: Int = 0,
    val metodoPago: String = "",
    // Datos que llegan con el pedido guardado en Firestore
    val id: String = "",              // id del documento en Firestore
    val compradorUid: String = "",    // cuenta del comprador que hizo el pedido
    val tiendaNombre: String = "",    // datos de contacto del negocio al momento de la compra
    val tiendaTelefono: String = "",
    val tiendaDireccion: String = "",
    val clienteTelefono: String = "") { //telefono del comprador para que la tienda lo contacte
    val total: Int get() = productos.sumOf { it.subtotal }

    // Datos del negocio guardados con el pedido. Los pedidos de ejemplo no los tienen
    // y usan los datos fijos de la tienda.
    val negocioMostrado: String
        get() = tiendaNombre.ifBlank { PerfilVendedorRepositorio.tienda().negocio }
    val telefonoMostrado: String
        get() = tiendaTelefono.ifBlank { PerfilVendedorRepositorio.tienda().telefono }
    val direccionTiendaMostrada: String
        get() = tiendaDireccion.ifBlank { PerfilVendedorRepositorio.tienda().direccion }
    val cantidadProductos: Int get() = productos.sumOf { it.cantidad }

    // "Torta Aurora x1, Galletas de casa x2"
    val resumenProductos: String
        get() = productos.joinToString(", ") { "${it.nombre} x${it.cantidad}" }

    val textoHorario: String
        get() = if (fechaEntrega.isBlank()) "${tipo.etiqueta}: $horario"
        else "${tipo.etiqueta}: $fechaEntrega · $horario"

    val etiquetaEstado: String get() = if (cancelado) "Cancelado" else estado.etiqueta
    val fondoEstado: String get() = if (cancelado) "#EFEAF5" else estado.fondo
    val colorTextoEstado: String get() = if (cancelado) "#8A8494" else estado.texto

    val siguienteEstado: EstadoPedido?
        get() = if (cancelado) null else when (estado) {
            EstadoPedido.PENDIENTE -> EstadoPedido.EN_PREPARACION
            EstadoPedido.EN_PREPARACION -> EstadoPedido.LISTO
            EstadoPedido.LISTO -> EstadoPedido.ENTREGADO
            EstadoPedido.ENTREGADO -> null
        }

    val textoAccion: String?
        get() = if (cancelado) null else when (estado) {
            EstadoPedido.PENDIENTE -> "Aceptar"
            EstadoPedido.EN_PREPARACION -> "Marcar listo"
            EstadoPedido.LISTO -> "Entregar"
            EstadoPedido.ENTREGADO -> null
        }

    val sePuedeCancelar: Boolean
        get() = !cancelado && estado != EstadoPedido.ENTREGADO
}


object PedidosVendedorRepositorio {

    private val pedidos = mutableListOf(
        PedidoGestion(
            "#SB-1048", "Valentina Mendoza", "Hoy, 8:40", "10:00 - 10:30", TipoEntrega.ENTREGA,
            listOf(
                ProductoPedido("Torta Aurora", 1, 68000),
                ProductoPedido("Brownie intenso", 2, 10000)
            ),
            EstadoPedido.EN_PREPARACION
        ),
        PedidoGestion(
            "#SB-1047", "María Flores", "Hoy, 10:32", "11:30", TipoEntrega.RECOJO,
            listOf(
                ProductoPedido("Cheesecake fresa", 1, 28000),
                ProductoPedido("Galletas de casa", 2, 14000)
            ),
            EstadoPedido.PENDIENTE
        ),
        PedidoGestion(
            "#SB-1046", "Diego Rojas", "Hoy, 8:50", "14:00 - 15:00", TipoEntrega.ENTREGA,
            listOf(ProductoPedido("Torta de chocolate", 1, 72000)),
            EstadoPedido.LISTO
        ),
        PedidoGestion(
            "#SB-1049", "Camilo Torres", "Hoy, 11:05", "16:00 - 17:00", TipoEntrega.ENTREGA,
            listOf(ProductoPedido("Torta red velvet", 1, 75000)),
            EstadoPedido.PENDIENTE
        ),
        PedidoGestion(
            "#SB-1050", "Sofía Castro", "Hoy, 9:15", "12:00", TipoEntrega.RECOJO,
            listOf(
                ProductoPedido("Tres leches", 1, 22000),
                ProductoPedido("Tiramisú", 1, 24000)
            ),
            EstadoPedido.EN_PREPARACION
        ),
        PedidoGestion(
            "#SB-1045", "Lucía Vega", "Hoy, 7:58", "9:00", TipoEntrega.RECOJO,
            listOf(ProductoPedido("Galletas de avena", 3, 12000)),
            EstadoPedido.ENTREGADO
        ),
        PedidoGestion(
            "#SB-1044", "Julián Mora", "Ayer, 18:20", "10:30", TipoEntrega.RECOJO,
            listOf(ProductoPedido("Brownie con nueces", 4, 12000)),
            EstadoPedido.PENDIENTE,
            cancelado = true
        )
    )

    fun obtenerTodos(): List<PedidoGestion> = pedidos.toList()

    fun buscarPorCodigo(codigo: String): PedidoGestion? =
        pedidos.firstOrNull { it.codigo == codigo }

    fun avanzarEstado(codigo: String): PedidoGestion? {
        val posicion = pedidos.indexOfFirst { it.codigo == codigo }
        if (posicion < 0) return null
        val siguiente = pedidos[posicion].siguienteEstado ?: return pedidos[posicion]
        pedidos[posicion] = pedidos[posicion].copy(estado = siguiente)
        return pedidos[posicion]
    }

    fun cancelar(codigo: String): PedidoGestion? {
        val posicion = pedidos.indexOfFirst { it.codigo == codigo }
        if (posicion < 0) return null
        if (!pedidos[posicion].sePuedeCancelar) return pedidos[posicion]
        pedidos[posicion] = pedidos[posicion].copy(cancelado = true)
        return pedidos[posicion]
    }

    const val COMPRADOR_DE_PRUEBA = "Valentina Mendoza"

    fun siguienteCodigo(): String {
        val mayor = pedidos.maxOfOrNull { p ->
            p.codigo.filter { it.isDigit() }.toIntOrNull() ?: 0
        } ?: 1000
        return "#SB-${mayor + 1}"
    }

    fun agregar(pedido: PedidoGestion) {
        pedidos.add(0, pedido)
    }
}