package com.sasobakery.app

enum class ResultadoCodigo {
    CORRECTO,
    INCORRECTO,
    EXPIRADO
}

object RecuperacionClaveRepositorio {

    const val CODIGO_DE_PRUEBA = "123456"
    const val LARGO_CODIGO = 6
    const val SEGUNDOS_PARA_REENVIAR = 30
    private const val VIGENCIA_CODIGO_MS = 10 * 60 * 1000L // 10 minutos

    private var correoEnProceso: String? = null
    private var emitidoEn = 0L
    private var codigoVerificado = false

    private val clavesNuevas = mutableMapOf<String, String>()

    fun solicitarCodigo(correo: String) {
        val limpio = correo.trim().lowercase()
        codigoVerificado = false
        if (CuentasRepositorio.existe(limpio)) {
            correoEnProceso = limpio
            emitidoEn = System.currentTimeMillis()
        } else {
            correoEnProceso = null
            emitidoEn = 0L
        }
    }

    fun verificarCodigo(correo: String, codigo: String): ResultadoCodigo {
        val limpio = correo.trim().lowercase()
        if (correoEnProceso == null || correoEnProceso != limpio) return ResultadoCodigo.INCORRECTO
        if (System.currentTimeMillis() - emitidoEn > VIGENCIA_CODIGO_MS) return ResultadoCodigo.EXPIRADO
        if (codigo != CODIGO_DE_PRUEBA) return ResultadoCodigo.INCORRECTO
        codigoVerificado = true
        return ResultadoCodigo.CORRECTO
    }

    fun cambiarClave(correo: String, nuevaClave: String): Boolean {
        val limpio = correo.trim().lowercase()
        if (!codigoVerificado || correoEnProceso != limpio) return false
        clavesNuevas[limpio] = nuevaClave
        correoEnProceso = null
        emitidoEn = 0L
        codigoVerificado = false
        return true
    }
}
