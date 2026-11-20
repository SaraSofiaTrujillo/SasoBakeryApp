package com.sasobakery.app

import android.net.Uri
import java.io.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.security.MessageDigest

// respuesta de payu, verificada es true si firma, referencia y valor coinciden
data class RespuestaPayu(
    val estado: String,
    val referencia: String,
    val transaccion: String,
    val valor: String,
    val medio: String,
    val mensaje: String,
    val verificada: Boolean
) : Serializable {
    // el codigo 4 es aprobado
    val aprobada: Boolean get() = estado == "4"

    // nombre del estado para mostrar
    val textoEstado: String
        get() = when (estado) {
            "4" -> "Aprobada"
            "6" -> "Rechazada"
            "5" -> "Expirada"
            "7" -> "Pendiente"
            "104" -> "Error"
            else -> "Desconocido ($estado)"
        }
}

// datos de payu en modo pruebas, son las credenciales publicas de sandbox
object PayuConfig {
    const val URL_CHECKOUT = "https://sandbox.checkout.payulatam.com/ppp-web-gateway-payu/"
    const val MERCHANT_ID = "508029"
    // cuenta de colombia
    const val ACCOUNT_ID = "512321"
    const val API_KEY = "4Vj8eK4rloUd272L48hsrarnUA"
    const val MONEDA = "COP"
    // 1 pruebas, 0 produccion
    const val MODO_PRUEBA = "1"

    // payu devuelve al comprador aqui, la app la intercepta en el webview
    const val URL_RESPUESTA = "https://sasobakery.app/pago/respuesta"

    // referencia nueva por cada pago, payu no deja repetir
    fun nuevaReferencia(): String = "SB${System.currentTimeMillis()}"

    // payu pide md5 en las firmas
    private fun md5(texto: String): String =
        MessageDigest.getInstance("MD5").digest(texto.toByteArray())
            .joinToString("") { "%02x".format(it) }

    // firma de lo que se envia, para que no cambien el monto
    fun firmaSolicitud(referencia: String, monto: Int): String =
        md5("$API_KEY~$MERCHANT_ID~$referencia~$monto~$MONEDA")

    // arma el formulario que se manda por post a payu
    fun cuerpoSolicitud(referencia: String, monto: Int, descripcion: String, correo: String): ByteArray {
        val campos = linkedMapOf(
            "merchantId" to MERCHANT_ID,
            "accountId" to ACCOUNT_ID,
            "description" to descripcion,
            "referenceCode" to referencia,
            "amount" to monto.toString(),
            "tax" to "0",
            "taxReturnBase" to "0",
            "currency" to MONEDA,
            "signature" to firmaSolicitud(referencia, monto),
            "test" to MODO_PRUEBA,
            "buyerEmail" to correo,
            "responseUrl" to URL_RESPUESTA
        )
        return campos.entries
            .joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
            .toByteArray()
    }

    // lee la respuesta de payu y revisa firma, referencia, comercio y valor
    fun interpretarRespuesta(uri: Uri, referenciaEsperada: String, montoEsperado: Int): RespuestaPayu {
        fun dato(nombre: String): String = uri.getQueryParameter(nombre).orEmpty()

        val estado = dato("transactionState")
        val referencia = dato("referenceCode")
        val valor = dato("TX_VALUE")

        // payu firma el valor con un decimal, por eso se redondea
        val valorNumerico = valor.toBigDecimalOrNull()
        val valorFirma = valorNumerico?.setScale(1, RoundingMode.HALF_EVEN)?.toPlainString()
        val firmaCalculada = valorFirma?.let {
            md5("$API_KEY~${dato("merchantId")}~$referencia~$it~${dato("currency")}~$estado")
        }
        val firmaCorrecta = firmaCalculada != null &&
                firmaCalculada.equals(dato("signature"), ignoreCase = true)

        val valorCorrecto = valorNumerico != null &&
                valorNumerico.compareTo(BigDecimal(montoEsperado)) == 0

        val comercioCorrecto = dato("merchantId") == MERCHANT_ID && dato("currency") == MONEDA

        return RespuestaPayu(
            estado = estado,
            referencia = referencia,
            transaccion = dato("reference_pol").ifBlank { dato("transactionId") },
            valor = valor,
            medio = dato("lapPaymentMethod").ifBlank { dato("polPaymentMethod") },
            mensaje = dato("message"),
            verificada = firmaCorrecta && comercioCorrecto &&
                    referencia == referenciaEsperada && valorCorrecto
        )
    }
}