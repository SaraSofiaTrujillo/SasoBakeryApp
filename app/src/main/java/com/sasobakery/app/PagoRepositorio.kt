package com.sasobakery.app

import java.util.Calendar

enum class TipoPago { TARJETA, BILLETERA, CONTRA_ENTREGA }

data class MetodoPago(
    val id: String,
    val tipo: TipoPago,
    val badge: String,
    val titulo: String,
    val detalle: String
)

private data class TarjetaGuardada(
    val id: String,
    val marca: String,
    val ultimos4: String,
    val vence: String
)

object PagoRepositorio {

    const val ID_BILLETERA = "billetera"
    const val ID_CONTRA_ENTREGA = "contra_entrega"

    private val tarjetas = mutableListOf<TarjetaGuardada>()
    private var contador = 0

    var idSeleccionado: String? = null

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

    fun metodoElegido(): MetodoPago {
        val todos = metodos()
        return todos.firstOrNull { it.id == idSeleccionado } ?: todos.first()
    }

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

    var mensajeError: String? = null
        private set

    fun confirmarPedido(): PedidoGestion? {
        mensajeError = null

        val items = CarritoRepositorio.obtenerItems()
        if (items.isEmpty()) {
            mensajeError = "Tu carrito está vacío"
            return null
        }

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