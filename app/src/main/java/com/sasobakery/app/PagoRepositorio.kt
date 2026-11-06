package com.sasobakery.app

import java.util.Calendar

enum class TipoPago { TARJETA, BILLETERA, CONTRA_ENTREGA }

// Una opción de pago tal como se ve en la lista.
// "badge" es el texto corto del cuadrito (VISA, N·D, $)
data class MetodoPago(
    val id: String,
    val tipo: TipoPago,
    val badge: String,
    val titulo: String,
    val detalle: String
)

// Por seguridad NUNCA se guarda el número completo: solo los últimos 4 dígitos.
private data class TarjetaGuardada(
    val id: String,
    val marca: String,
    val ultimos4: String,
    val vence: String
)

// Lo que el comprador elige en la pantalla de Método de pago (solo en memoria).
// Esta versión es una simulación: no se procesa ningún pago real.
object PagoRepositorio {

    const val ID_BILLETERA = "billetera"
    const val ID_CONTRA_ENTREGA = "contra_entrega"

    private val tarjetas = mutableListOf<TarjetaGuardada>()
    private var contador = 0

    // null = el comprador todavía no ha tocado ninguna opción
    var idSeleccionado: String? = null

    /** Tarjetas agregadas + Nequi/Daviplata + pago contra entrega (o al recoger). */
    fun metodos(): List<MetodoPago> {
        val lista = mutableListOf<MetodoPago>()

        tarjetas.forEach { t ->
            lista.add(
                MetodoPago(t.id, TipoPago.TARJETA, t.marca, "•••• ${t.ultimos4}", "Vence ${t.vence}")
            )
        }

        lista.add(
            MetodoPago(
                ID_BILLETERA, TipoPago.BILLETERA, "N·D",
                "Nequi o Daviplata", "Confirma desde tu celular"
            )
        )

        // El texto cambia según el comprador reciba en casa o recoja en tienda
        val alRecibir = EntregaRepositorio.tipo == TipoEntrega.ENTREGA
        lista.add(
            MetodoPago(
                ID_CONTRA_ENTREGA, TipoPago.CONTRA_ENTREGA, "$",
                if (alRecibir) "Pago contra entrega" else "Pago al recoger",
                if (alRecibir) "Pagas cuando recibas tu pedido" else "Pagas cuando recojas tu pedido"
            )
        )
        return lista
    }

    /** El método elegido; si todavía no eligió ninguno, el primero de la lista. */
    fun metodoElegido(): MetodoPago {
        val todos = metodos()
        return todos.firstOrNull { it.id == idSeleccionado } ?: todos.first()
    }

    /** Guarda solo los últimos 4 dígitos y deja la tarjeta seleccionada. */
    fun agregarTarjeta(digitos: String, vence: String) {
        contador++
        val marca = when (digitos.firstOrNull()) {
            '4' -> "VISA"
            '5' -> "MC"
            else -> "TDC"
        }
        val tarjeta = TarjetaGuardada("tarjeta_$contador", marca, digitos.takeLast(4), vence)
        tarjetas.add(tarjeta)
        idSeleccionado = tarjeta.id
    }

    /** Acepta MM/AA con mes entre 01 y 12 y que no esté vencida. */
    fun vencimientoValido(texto: String): Boolean {
        val partes = texto.split("/")
        if (partes.size != 2) return false
        if (partes[0].length != 2 || partes[1].length != 2) return false
        val mes = partes[0].toIntOrNull() ?: return false
        val anio = partes[1].toIntOrNull() ?: return false
        if (mes !in 1..12) return false

        val hoy = Calendar.getInstance()
        val anioActual = hoy.get(Calendar.YEAR) % 100
        val mesActual = hoy.get(Calendar.MONTH) + 1
        return anio > anioActual || (anio == anioActual && mes >= mesActual)
    }

    // Si confirmarPedido() devuelve null, aquí queda el motivo para mostrárselo al comprador
    var mensajeError: String? = null
        private set

    /**
     * Convierte el carrito en un pedido: lo agrega a los pedidos del vendedor (como "Nuevo"),
     * descuenta el stock y vacía el carrito. Devuelve el pedido creado o null si no se pudo.
     */
    fun confirmarPedido(): PedidoGestion? {
        mensajeError = null

        val items = CarritoRepositorio.obtenerItems()
        if (items.isEmpty()) {
            mensajeError = "Tu carrito está vacío"
            return null
        }

        // El stock pudo cambiar mientras el comprador elegía: se revisa otra vez
        val sinStock = items.firstOrNull { it.cantidad > it.producto.stock }
        if (sinStock != null) {
            mensajeError = "Ya no hay suficiente stock de ${sinStock.producto.nombre}. Revisa tu carrito."
            return null
        }

        val domicilio = EntregaRepositorio.tipo == TipoEntrega.ENTREGA
        val metodo = metodoElegido()
        val ahora = Calendar.getInstance()
        val hora = "${ahora.get(Calendar.HOUR_OF_DAY)}:" +
                String.format(java.util.Locale.US, "%02d", ahora.get(Calendar.MINUTE))

        val pedido = PedidoGestion(
            codigo = PedidosVendedorRepositorio.siguienteCodigo(),
            cliente = PedidosVendedorRepositorio.COMPRADOR_DE_PRUEBA,
            fecha = "Hoy, $hora",
            horario = EntregaRepositorio.horario,
            tipo = EntregaRepositorio.tipo,
            productos = items.map { ProductoPedido(it.producto.nombre, it.cantidad, it.producto.precio) },
            estado = EstadoPedido.PENDIENTE,
            fechaEntrega = EntregaRepositorio.fechaElegida().texto,
            direccion = if (domicilio) EntregaRepositorio.direccion else "",
            envio = EntregaRepositorio.costoEnvio(),
            metodoPago = if (metodo.tipo == TipoPago.TARJETA) "Tarjeta ${metodo.titulo}" else metodo.titulo
        )

        items.forEach { InventarioRepositorio.descontarStock(it.producto.id, it.cantidad) }
        PedidosVendedorRepositorio.agregar(pedido)
        CarritoRepositorio.vaciar()
        return pedido
    }
}