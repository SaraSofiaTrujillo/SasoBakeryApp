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
    val clienteTelefono: String = "", // teléfono del comprador para que la tienda lo contacte
    // Ubicación GPS de la entrega a domicilio (null si el comprador no la capturó)
    val latitud: Double? = null,
    val longitud: Double? = null,
    // Cómo se pagó el pedido
    val pagoEstado: String = "",      // APROBADO (pago en línea) o CONTRA_ENTREGA
    val pagoProveedor: String = "",   // PayU, si fue pago en línea
    val pagoReferencia: String = "",  // referencia que la app envió a PayU
    val pagoTransaccion: String = ""  // número de la transacción en PayU
) {
    val total: Int get() = productos.sumOf { it.subtotal }

    // ¿El pedido trae las coordenadas de entrega?
    val tieneUbicacion: Boolean get() = latitud != null && longitud != null

    // Cómo se pagó el pedido, para mostrárselo al equipo. Los pedidos anteriores,
    // que no guardaron el estado del pago, muestran el método que tenían.
    val textoPago: String
        get() = when (pagoEstado) {
            "APROBADO" -> "Pagado en línea con PayU" +
                    if (pagoReferencia.isNotBlank()) " · ref. $pagoReferencia" else ""
            "CONTRA_ENTREGA" ->
                if (tipo == TipoEntrega.ENTREGA) "Pago contra entrega: cobrar al entregar"
                else "Pago al recoger: cobrar en tienda"
            else -> metodoPago.ifBlank { "Sin dato" }
        }

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