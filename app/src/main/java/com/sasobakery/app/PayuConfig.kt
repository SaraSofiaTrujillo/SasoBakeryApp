package com.sasobakery.app

import android.net.Uri
import java.io.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.security.MessageDigest

/** Resultado que PayU devuelve al terminar un pago, ya interpretado por la app. */
data class RespuestaPayu(
    val estado: String,       // código de PayU: 4 aprobada, 6 rechazada, 5 expirada, 7 pendiente
    val referencia: String,   // la referencia que la app envió
    val transaccion: String,  // número de la transacción en PayU
    val valor: String,        // valor cobrado, tal como lo devolvió PayU
    val medio: String,        // medio de pago usado (VISA, PSE...)
    val mensaje: String,
    val verificada: Boolean   // true si la firma, la referencia y el valor corresponden al pago
) : Serializable {
    val aprobada: Boolean get() = estado == "4"

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

/**
 * Configuración y ayudas para el WebCheckout de PayU Latam en ambiente de PRUEBAS.
 *
 * Los datos de abajo son las credenciales de sandbox PÚBLICAS que PayU publica en su
 * guía de pruebas, así que pueden estar en el código. En producción la llave secreta
 * (ApiKey) NUNCA debe ir en la app: la firma y la confirmación se hacen en un servidor.
 */
object PayuConfig {

    const val URL_CHECKOUT = "https://sandbox.checkout.payulatam.com/ppp-web-gateway-payu/"
    const val MERCHANT_ID = "508029"
    const val ACCOUNT_ID = "512321" // cuenta de Colombia del comercio de pruebas
    const val API_KEY = "4Vj8eK4rloUd272L48hsrarnUA"
    const val MONEDA = "COP"
    const val MODO_PRUEBA = "1" // 1 = pruebas, 0 = producción

    // Dirección a la que PayU devuelve al comprador. No hace falta que exista: la app
    // la intercepta dentro del WebView antes de que se cargue.
    const val URL_RESPUESTA = "https://sasobakery.app/pago/respuesta"

    /** Referencia única por intento de pago (PayU no permite repetirla). */
    fun nuevaReferencia(): String = "SB${System.currentTimeMillis()}"

    private fun md5(texto: String): String =
        MessageDigest.getInstance("MD5").digest(texto.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Firma de la solicitud: ApiKey~merchantId~referencia~monto~moneda, en MD5. */
    fun firmaSolicitud(referencia: String, monto: Int): String =
        md5("$API_KEY~$MERCHANT_ID~$referencia~$monto~$MONEDA")

    /** Cuerpo del formulario (POST) que se envía a PayU, ya codificado. */
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

    /**
     * Interpreta la dirección a la que PayU devolvió al comprador. Valida la firma de la
     * respuesta (ApiKey~merchantId~referencia~valor redondeado a 1 decimal~moneda~estado) y
     * que la referencia y el valor sean los del pago que se inició.
     */
    fun interpretarRespuesta(uri: Uri, referenciaEsperada: String, montoEsperado: Int): RespuestaPayu {
        fun dato(nombre: String): String = uri.getQueryParameter(nombre).orEmpty()

        val estado = dato("transactionState")
        val referencia = dato("referenceCode")
        val valor = dato("TX_VALUE")

        // PayU pide redondear el valor a un decimal (mitad al par) antes de calcular la firma
        val valorNumerico = valor.toBigDecimalOrNull()
        val valorFirma = valorNumerico?.setScale(1, RoundingMode.HALF_EVEN)?.toPlainString()
        val firmaCalculada = valorFirma?.let {
            md5("$API_KEY~${dato("merchantId")}~$referencia~$it~${dato("currency")}~$estado")
        }
        val firmaCorrecta = firmaCalculada != null &&
                firmaCalculada.equals(dato("signature"), ignoreCase = true)

        val valorCorrecto = valorNumerico != null &&
                valorNumerico.compareTo(BigDecimal(montoEsperado)) == 0

        // El pago debe ser de NUESTRO comercio y en NUESTRA moneda
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