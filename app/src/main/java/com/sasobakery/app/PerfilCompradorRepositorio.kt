package com.sasobakery.app

// Datos del comprador
data class PerfilComprador(
    val nombre: String,
    val correo: String
) {
    // "Valentina Mendoza" -> "VM" (para el avatar)
    val iniciales: String
        get() = nombre.trim()
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
}

// Perfil del comprador y sus números (solo en memoria).
// Cuando exista la base de datos, esta clase es la que se reemplaza.
object PerfilCompradorRepositorio {

    // ===== VALOR QUE PUEDES CAMBIAR =====
    const val PESOS_POR_PUNTO = 1000 // 1 punto Saso por cada $ 1.000 comprados
    // ====================================

    // Datos de ejemplo (el nombre coincide con el comprador de los pedidos)
    private var perfil = PerfilComprador(
        nombre = PedidosVendedorRepositorio.COMPRADOR_DE_PRUEBA,
        correo = "valentina@email.com"
    )

    // Preferencia guardada (todavía no se envían avisos reales)
    var notificaciones = true

    fun obtener(): PerfilComprador = perfil

    fun guardar(nuevo: PerfilComprador) {
        perfil = nuevo
    }

    /** Todos los pedidos del comprador (los mismos que ve el vendedor). */
    fun misPedidos(): List<PedidoGestion> =
        PedidosVendedorRepositorio.obtenerTodos()
            .filter { it.cliente == PedidosVendedorRepositorio.COMPRADOR_DE_PRUEBA }

    /** Pedidos que todavía no terminan: ni entregados ni cancelados. */
    fun pedidosActivos(): Int =
        misPedidos().count { !it.cancelado && it.estado != EstadoPedido.ENTREGADO }

    /** Puntos Saso: salen de lo comprado en pedidos no cancelados. */
    fun puntos(): Int =
        misPedidos().filter { !it.cancelado }.sumOf { it.total } / PESOS_POR_PUNTO
}