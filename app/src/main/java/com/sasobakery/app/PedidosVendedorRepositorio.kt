package com.sasobakery.app

// una linea del pedido, con el precio de cuando se compro
data class ProductoPedido(
    val nombre: String,
    val cantidad: Int,
    val precioUnitario: Int
) {
    val subtotal: Int get() = cantidad * precioUnitario
}

// domicilio o recojo en tienda
enum class TipoEntrega(val etiqueta: String) {
    ENTREGA("Entrega"),
    RECOJO("Recojo")
}

// un pedido con todo lo que necesitan el vendedor, el admin y el comprador
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
    val id: String = "",
    val compradorUid: String = "",
    val tiendaNombre: String = "",
    val tiendaTelefono: String = "",
    val tiendaDireccion: String = "",
    val clienteTelefono: String = "",
    val latitud: Double? = null,
    val longitud: Double? = null,
    val pagoEstado: String = "",
    val pagoProveedor: String = "",
    val pagoReferencia: String = "",
    val pagoTransaccion: String = ""
) {
    // total de productos, sin envio
    val total: Int get() = productos.sumOf { it.subtotal }

    val tieneUbicacion: Boolean get() = latitud != null && longitud != null

    // como se pago, lo que ve el vendedor para saber si cobrar
    val textoPago: String
        get() = when (pagoEstado) {
            "APROBADO" -> "Pagado en línea con PayU" +
                    if (pagoReferencia.isNotBlank()) " · ref. $pagoReferencia" else ""
            "CONTRA_ENTREGA" ->
                if (tipo == TipoEntrega.ENTREGA) "Pago contra entrega: cobrar al entregar"
                else "Pago al recoger: cobrar en tienda"
            else -> metodoPago.ifBlank { "Sin dato" }
        }

    // datos de la tienda guardados en el pedido, si faltan se usan los actuales
    val negocioMostrado: String
        get() = tiendaNombre.ifBlank { PerfilVendedorRepositorio.tienda().negocio }
    val telefonoMostrado: String
        get() = tiendaTelefono.ifBlank { PerfilVendedorRepositorio.tienda().telefono }
    val direccionTiendaMostrada: String
        get() = tiendaDireccion.ifBlank { PerfilVendedorRepositorio.tienda().direccion }
    val cantidadProductos: Int get() = productos.sumOf { it.cantidad }

    val resumenProductos: String
        get() = productos.joinToString(", ") { "${it.nombre} x${it.cantidad}" }

    // tipo de entrega con fecha y franja
    val textoHorario: String
        get() = if (fechaEntrega.isBlank()) "${tipo.etiqueta}: $horario"
        else "${tipo.etiqueta}: $fechaEntrega · $horario"

    // un pedido cancelado se muestra gris sin importar su estado
    val etiquetaEstado: String get() = if (cancelado) "Cancelado" else estado.etiqueta
    val fondoEstado: String get() = if (cancelado) "#EFEAF5" else estado.fondo
    val colorTextoEstado: String get() = if (cancelado) "#8A8494" else estado.texto

    // el pedido avanza en orden, pendiente a preparacion a listo a entregado
    val siguienteEstado: EstadoPedido?
        get() = if (cancelado) null else when (estado) {
            EstadoPedido.PENDIENTE -> EstadoPedido.EN_PREPARACION
            EstadoPedido.EN_PREPARACION -> EstadoPedido.LISTO
            EstadoPedido.LISTO -> EstadoPedido.ENTREGADO
            EstadoPedido.ENTREGADO -> null
        }

    // texto del boton que lo hace avanzar
    val textoAccion: String?
        get() = if (cancelado) null else when (estado) {
            EstadoPedido.PENDIENTE -> "Aceptar"
            EstadoPedido.EN_PREPARACION -> "Marcar listo"
            EstadoPedido.LISTO -> "Entregar"
            EstadoPedido.ENTREGADO -> null
        }

    // se cancela mientras no se haya entregado
    val sePuedeCancelar: Boolean
        get() = !cancelado && estado != EstadoPedido.ENTREGADO
}