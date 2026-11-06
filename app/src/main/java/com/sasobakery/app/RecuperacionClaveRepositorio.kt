package com.sasobakery.app

enum class ResultadoCodigo {
    CORRECTO,
    INCORRECTO,
    EXPIRADO
}

// Recuperación de contraseña SIMULADA (solo en memoria).
//
// ┌─ BACKEND ────────────────────────────────────────────────────────────┐
// │ Esta clase es el ÚNICO lugar que habla con el "servidor". Cuando     │
// │ exista el servicio de autenticación, solo se reemplaza el interior   │
// │ de las 3 funciones; la pantalla no cambia. Lo que el servidor debe   │
// │ hacer de verdad:                                                     │
// │  1. solicitarCodigo: generar un código aleatorio de un solo uso,     │
// │     guardarlo (con vencimiento) y ENVIARLO POR CORREO. Responder     │
// │     igual exista o no el correo (para no revelar quién tiene cuenta).│
// │  2. verificarCodigo: comparar el código, limitar intentos (ej. 5) y  │
// │     devolver un token temporal de restablecimiento.                  │
// │  3. cambiarClave: usar ese token para guardar la nueva contraseña    │
// │     CIFRADA (hash) e invalidar el código y las sesiones anteriores.  │
// └──────────────────────────────────────────────────────────────────────┘
object RecuperacionClaveRepositorio {

    // Solo para pruebas: con el servicio real el código llega al correo
    const val CODIGO_DE_PRUEBA = "123456"
    const val LARGO_CODIGO = 6
    const val SEGUNDOS_PARA_REENVIAR = 30
    private const val VIGENCIA_CODIGO_MS = 10 * 60 * 1000L // 10 minutos

    private var correoEnProceso: String? = null
    private var emitidoEn = 0L
    private var codigoVerificado = false

    // Contraseñas nuevas guardadas en memoria. El login simulado todavía no las
    // consulta (acepta cualquier contraseña de 6 o más caracteres).
    private val clavesNuevas = mutableMapOf<String, String>()

    /**
     * BACKEND: pide el envío del código al correo.
     * Si el correo no es de una cuenta, no se emite código, pero la pantalla se
     * comporta igual (por seguridad no se revela qué correos están registrados).
     */
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

    /** BACKEND: comprueba el código que escribió el comprador. */
    fun verificarCodigo(correo: String, codigo: String): ResultadoCodigo {
        val limpio = correo.trim().lowercase()
        if (correoEnProceso == null || correoEnProceso != limpio) return ResultadoCodigo.INCORRECTO
        if (System.currentTimeMillis() - emitidoEn > VIGENCIA_CODIGO_MS) return ResultadoCodigo.EXPIRADO
        if (codigo != CODIGO_DE_PRUEBA) return ResultadoCodigo.INCORRECTO
        codigoVerificado = true
        return ResultadoCodigo.CORRECTO
    }

    /**
     * BACKEND: guarda la nueva contraseña. Devuelve false si el código no fue
     * verificado antes (o ya venció la sesión de recuperación).
     */
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
