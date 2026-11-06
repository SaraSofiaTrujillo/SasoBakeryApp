package com.sasobakery.app

import java.util.Calendar

data class OpcionFecha(val dia: String, val fecha: String) {
    val texto: String get() = "$dia $fecha"
}

object EntregaRepositorio {

    const val COSTO_DOMICILIO = 6000
    val HORARIOS = listOf("10:00 – 12:00", "12:00 – 14:00", "14:00 – 16:00", "16:00 – 18:00")

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

    fun costoEnvio(): Int = if (tipo == TipoEntrega.ENTREGA) COSTO_DOMICILIO else 0

    fun totalConEnvio(): Int = CarritoRepositorio.total() + costoEnvio()

    fun fechasDisponibles(): List<OpcionFecha> =
        (1..3).map { sumar ->
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, sumar)
            val dia = if (sumar == 1) "Mañana" else DIAS[c.get(Calendar.DAY_OF_WEEK) - 1]
            OpcionFecha(dia, "${c.get(Calendar.DAY_OF_MONTH)} ${MESES[c.get(Calendar.MONTH)]}")
        }

    fun fechaElegida(): OpcionFecha = fechasDisponibles()[indiceFecha.coerceIn(0, 2)]
}