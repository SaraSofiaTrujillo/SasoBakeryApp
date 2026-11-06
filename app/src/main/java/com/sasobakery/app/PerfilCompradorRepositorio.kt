package com.sasobakery.app

data class PerfilComprador(
    val nombre: String,
    val correo: String
) {
    val iniciales: String
        get() = nombre.trim()
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
}

object PerfilCompradorRepositorio {

    const val PESOS_POR_PUNTO = 1000

    private var perfil = PerfilComprador(
        nombre = PedidosVendedorRepositorio.COMPRADOR_DE_PRUEBA,
        correo = "valentina@email.com"
    )

    var notificaciones = true

    fun obtener(): PerfilComprador = perfil

    fun guardar(nuevo: PerfilComprador) {
        perfil = nuevo
    }

    fun misPedidos(): List<PedidoGestion> =
        PedidosVendedorRepositorio.obtenerTodos()
            .filter { it.cliente == PedidosVendedorRepositorio.COMPRADOR_DE_PRUEBA }

    fun pedidosActivos(): Int =
        misPedidos().count { !it.cancelado && it.estado != EstadoPedido.ENTREGADO }

    fun puntos(): Int =
        misPedidos().filter { !it.cancelado }.sumOf { it.total } / PESOS_POR_PUNTO
}