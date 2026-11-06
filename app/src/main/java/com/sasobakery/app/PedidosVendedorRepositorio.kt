package com.sasobakery.app

// Un producto dentro de un pedido
data class ProductoPedido(
    val nombre: String,
    val cantidad: Int,
    val precioUnitario: Int // pesos colombianos (COP)
) {
    val subtotal: Int get() = cantidad * precioUnitario
}

enum class TipoEntrega(val etiqueta: String) {
    ENTREGA("Entrega"),
    RECOJO("Recojo")
}

// Pedido tal como lo gestiona el vendedor.
// Usa el EstadoPedido que ya existe (PedidosAdapter.kt) y agrega "cancelado"
// aparte, para no tocar las pantallas del administrador.
data class PedidoGestion(
    val codigo: String,
    val cliente: String,
    val fecha: String,      // cuándo se hizo el pedido, ej. "Hoy, 9:02"
    val horario: String,    // ej. "10:00 - 10:30" o "11:30"
    val tipo: TipoEntrega,
    val productos: List<ProductoPedido>,
    val estado: EstadoPedido,
    val cancelado: Boolean = false,
    // Datos que llenan los pedidos hechos por el comprador (los de ejemplo los dejan vacíos)
    val fechaEntrega: String = "",  // ej. "Mañana 6 Nov"
    val direccion: String = "",     // solo si es entrega a domicilio
    val envio: Int = 0,             // costo del domicilio en pesos colombianos (COP)
    val metodoPago: String = ""     // ej. "Nequi o Daviplata"
) {
    val total: Int get() = productos.sumOf { it.subtotal }

    val cantidadProductos: Int get() = productos.sumOf { it.cantidad }

    // "Torta Aurora x1, Galletas de casa x2"
    val resumenProductos: String
        get() = productos.joinToString(", ") { "${it.nombre} x${it.cantidad}" }

    // "Entrega: 10:00 - 10:30" o "Recojo: 11:30"
    val textoHorario: String
        get() = if (fechaEntrega.isBlank()) "${tipo.etiqueta}: $horario"
        else "${tipo.etiqueta}: $fechaEntrega · $horario"

    // ---------- Cómo se ve el estado (si está cancelado, manda "Cancelado") ----------
    val etiquetaEstado: String get() = if (cancelado) "Cancelado" else estado.etiqueta
    val fondoEstado: String get() = if (cancelado) "#EFEAF5" else estado.fondo
    val colorTextoEstado: String get() = if (cancelado) "#8A8494" else estado.texto

    // ---------- Qué puede hacer el vendedor ----------
    // Siguiente estado del pedido (null si ya terminó o está cancelado)
    val siguienteEstado: EstadoPedido?
        get() = if (cancelado) null else when (estado) {
            EstadoPedido.PENDIENTE -> EstadoPedido.EN_PREPARACION
            EstadoPedido.EN_PREPARACION -> EstadoPedido.LISTO
            EstadoPedido.LISTO -> EstadoPedido.ENTREGADO
            EstadoPedido.ENTREGADO -> null
        }

    // Texto del botón principal de la tarjeta (null = no hay acción)
    val textoAccion: String?
        get() = if (cancelado) null else when (estado) {
            EstadoPedido.PENDIENTE -> "Aceptar"
            EstadoPedido.EN_PREPARACION -> "Marcar listo"
            EstadoPedido.LISTO -> "Entregar"
            EstadoPedido.ENTREGADO -> null
        }

    // Se puede cancelar mientras no esté entregado ni cancelado
    val sePuedeCancelar: Boolean
        get() = !cancelado && estado != EstadoPedido.ENTREGADO
}

// Lista de pedidos compartida entre pantallas (solo en memoria).
// Cuando exista la base de datos, esta clase es la que se reemplaza.
object PedidosVendedorRepositorio {

    // Datos de ejemplo (precios iguales a los del inventario)
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

    /** Copia de la lista actual (así nadie la modifica por accidente). */
    fun obtenerTodos(): List<PedidoGestion> = pedidos.toList()

    fun buscarPorCodigo(codigo: String): PedidoGestion? =
        pedidos.firstOrNull { it.codigo == codigo }

    /** Pasa el pedido al siguiente estado y devuelve el pedido actualizado. */
    fun avanzarEstado(codigo: String): PedidoGestion? {
        val posicion = pedidos.indexOfFirst { it.codigo == codigo }
        if (posicion < 0) return null
        val siguiente = pedidos[posicion].siguienteEstado ?: return pedidos[posicion]
        pedidos[posicion] = pedidos[posicion].copy(estado = siguiente)
        return pedidos[posicion]
    }

    /** Cancela el pedido (si se puede) y devuelve el pedido actualizado. */
    fun cancelar(codigo: String): PedidoGestion? {
        val posicion = pedidos.indexOfFirst { it.codigo == codigo }
        if (posicion < 0) return null
        if (!pedidos[posicion].sePuedeCancelar) return pedidos[posicion]
        pedidos[posicion] = pedidos[posicion].copy(cancelado = true)
        return pedidos[posicion]
    }

    // Comprador de prueba (es el mismo que aparece en la lista de usuarios del administrador)
    const val COMPRADOR_DE_PRUEBA = "Valentina Mendoza"

    /** Código del siguiente pedido: sigue la numeración (#SB-1051, #SB-1052...). */
    fun siguienteCodigo(): String {
        val mayor = pedidos.maxOfOrNull { p ->
            p.codigo.filter { it.isDigit() }.toIntOrNull() ?: 0
        } ?: 1000
        return "#SB-${mayor + 1}"
    }

    /** Agrega un pedido nuevo al inicio de la lista (el vendedor lo ve como "Nuevo"). */
    fun agregar(pedido: PedidoGestion) {
        pedidos.add(0, pedido)
    }
}