package com.sasobakery.app

import java.util.Calendar

// Un día que el comprador puede elegir. Ej: dia = "Mañana", fecha = "6 Nov"
data class OpcionFecha(val dia: String, val fecha: String) {
    val texto: String get() = "$dia $fecha"
}

// Lo que el comprador elige en la pantalla de Entrega (solo en memoria).
// Usa el TipoEntrega que ya existe en PedidosVendedorRepositorio.kt (ENTREGA o RECOJO).
// Cuando exista la base de datos, esta clase es la que se reemplaza.
object EntregaRepositorio {

    // ===== VALORES QUE PUEDES CAMBIAR =====
    const val COSTO_DOMICILIO = 6000 // pesos colombianos (COP)
    val HORARIOS = listOf("10:00 – 12:00", "12:00 – 14:00", "14:00 – 16:00", "16:00 – 18:00")
    // ======================================

    var tipo = TipoEntrega.ENTREGA
    var etiquetaDireccion = ""
    var direccion = ""
    var indiceFecha = 0
    var horario = HORARIOS[1]

    private val DIAS = listOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")
    private val MESES = listOf(
        "Ene", "Feb", "Mar", "Abr", "May", "Jun",
        "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    )

    fun tieneDireccion(): Boolean = direccion.isNotBlank()

    /** Entrega a domicilio cuesta COSTO_DOMICILIO; recojo en tienda no cuesta nada. */
    fun costoEnvio(): Int = if (tipo == TipoEntrega.ENTREGA) COSTO_DOMICILIO else 0

    /** Total de los productos más el envío (lo usará la pantalla de pago). */
    fun totalConEnvio(): Int = CarritoRepositorio.total() + costoEnvio()

    /** Mañana y los 2 días siguientes, según la fecha real del celular. */
    fun fechasDisponibles(): List<OpcionFecha> =
        (1..3).map { sumar ->
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, sumar)
            val dia = if (sumar == 1) "Mañana" else DIAS[c.get(Calendar.DAY_OF_WEEK) - 1]
            OpcionFecha(dia, "${c.get(Calendar.DAY_OF_MONTH)} ${MESES[c.get(Calendar.MONTH)]}")
        }

    fun fechaElegida(): OpcionFecha = fechasDisponibles()[indiceFecha.coerceIn(0, 2)]
}