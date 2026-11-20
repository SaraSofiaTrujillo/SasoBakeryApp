package com.sasobakery.app

import java.util.Calendar

// una fecha que se puede elegir para la entrega
data class OpcionFecha(val dia: String, val fecha: String) {
    val texto: String get() = "$dia $fecha"
}

// datos de la entrega que se van llenando durante la compra, solo en memoria
object EntregaRepositorio {
    // costo fijo del domicilio
    const val COSTO_DOMICILIO = 6000
    val HORARIOS = listOf("10:00 – 12:00", "12:00 – 14:00", "14:00 – 16:00", "16:00 – 18:00")

    // lo que eligio el comprador, domicilio o recoger
    var tipo = TipoEntrega.ENTREGA
    var etiquetaDireccion = ""
    var direccion = ""
    var indiceFecha = 0
    var horario = HORARIOS[1]
    // coordenadas del gps, null si no las uso
    var latitud: Double? = null
    var longitud: Double? = null

    // nombres para armar las fechas en espanol
    private val DIAS = listOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")
    private val MESES = listOf(
        "Ene", "Feb", "Mar", "Abr", "May", "Jun",
        "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    )

    fun tieneDireccion(): Boolean = direccion.isNotBlank()

    fun tieneUbicacion(): Boolean = latitud != null && longitud != null

    // solo cobra envio si es domicilio
    fun costoEnvio(): Int = if (tipo == TipoEntrega.ENTREGA) COSTO_DOMICILIO else 0

    fun totalConEnvio(): Int = CarritoRepositorio.total() + costoEnvio()

    // los proximos 3 dias empezando por manana
    fun fechasDisponibles(): List<OpcionFecha> =
        (1..3).map { sumar ->
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, sumar)
            val dia = if (sumar == 1) "Mañana" else DIAS[c.get(Calendar.DAY_OF_WEEK) - 1]
            OpcionFecha(dia, "${c.get(Calendar.DAY_OF_MONTH)} ${MESES[c.get(Calendar.MONTH)]}")
        }

    // la fecha escogida, el indice se limita para que no se salga
    fun fechaElegida(): OpcionFecha = fechasDisponibles()[indiceFecha.coerceIn(0, 2)]
}