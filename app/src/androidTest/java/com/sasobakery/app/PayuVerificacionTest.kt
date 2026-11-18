package com.sasobakery.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest

/**
 * Pruebas de la verificación del pago. Simulan respuestas de PayU, auténticas y
 * manipuladas, y comprueban que solo la auténtica se marca como verificada.
 * Se ejecutan en el emulador porque usan android.net.Uri.
 */
@RunWith(AndroidJUnit4::class)
class PayuVerificacionTest {

    private val referencia = "SB1700000000000"
    private val monto = 15000

    private fun md5(texto: String): String =
        MessageDigest.getInstance("MD5").digest(texto.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Firma que PayU calcularía para la respuesta (valor redondeado a 1 decimal). */
    private fun firmaDe(
        merchantId: String, ref: String, valor: String, moneda: String, estado: String
    ): String {
        val v = BigDecimal(valor).setScale(1, RoundingMode.HALF_EVEN).toPlainString()
        return md5("${PayuConfig.API_KEY}~$merchantId~$ref~$v~$moneda~$estado")
    }

    /** Arma la dirección de respuesta; si no se indica la firma, usa la correcta. */
    private fun respuesta(
        estado: String = "4",
        ref: String = referencia,
        valor: String = "15000.00",
        merchantId: String = PayuConfig.MERCHANT_ID,
        moneda: String = PayuConfig.MONEDA,
        firma: String? = firmaDe(merchantId, ref, valor, moneda, estado)
    ): Uri {
        val b = Uri.parse(PayuConfig.URL_RESPUESTA).buildUpon()
            .appendQueryParameter("merchantId", merchantId)
            .appendQueryParameter("referenceCode", ref)
            .appendQueryParameter("TX_VALUE", valor)
            .appendQueryParameter("currency", moneda)
            .appendQueryParameter("transactionState", estado)
            .appendQueryParameter("reference_pol", "999111")
        if (firma != null) b.appendQueryParameter("signature", firma)
        return b.build()
    }

    private fun interpretar(uri: Uri) =
        PayuConfig.interpretarRespuesta(uri, referencia, monto)

    @Test
    fun respuestaAutenticaAprobada_seVerifica() {
        val r = interpretar(respuesta())
        assertTrue(r.aprobada)
        assertTrue(r.verificada)
        assertEquals("999111", r.transaccion)
    }

    @Test
    fun respuestaAutenticaRechazada_seVerificaPeroNoEstaAprobada() {
        val r = interpretar(respuesta(estado = "6"))
        assertFalse(r.aprobada)
        assertTrue(r.verificada)
    }

    @Test
    fun firmaAlterada_seRechaza() {
        val buena = firmaDe(PayuConfig.MERCHANT_ID, referencia, "15000.00", PayuConfig.MONEDA, "4")
        val mala = buena.dropLast(1) + (if (buena.last() == '0') '1' else '0')
        assertFalse(interpretar(respuesta(firma = mala)).verificada)
    }

    @Test
    fun sinFirma_seRechaza() {
        assertFalse(interpretar(respuesta(firma = null)).verificada)
    }

    @Test
    fun referenciaDistinta_seRechazaAunqueLaFirmaSeaValida() {
        assertFalse(interpretar(respuesta(ref = "SB1700000009999")).verificada)
    }

    @Test
    fun valorMenor_seRechazaAunqueLaFirmaSeaValida() {
        assertFalse(interpretar(respuesta(valor = "100.00")).verificada)
    }

    @Test
    fun valorNoNumerico_seRechaza() {
        assertFalse(interpretar(respuesta(valor = "abc", firma = "x")).verificada)
    }

    @Test
    fun estadoCambiadoDeRechazadoAAprobado_seRechaza() {
        // La firma corresponde al estado 6 (rechazada), pero se cambia el estado a 4
        val firmaDeRechazada =
            firmaDe(PayuConfig.MERCHANT_ID, referencia, "15000.00", PayuConfig.MONEDA, "6")
        val r = interpretar(respuesta(estado = "4", firma = firmaDeRechazada))
        assertTrue(r.aprobada)
        assertFalse(r.verificada)
    }

    @Test
    fun comercioAjeno_seRechazaAunqueLaFirmaSeaValida() {
        assertFalse(interpretar(respuesta(merchantId = "111111")).verificada)
    }

    @Test
    fun monedaDistinta_seRechazaAunqueLaFirmaSeaValida() {
        assertFalse(interpretar(respuesta(moneda = "USD")).verificada)
    }

    @Test
    fun pedidoNoSeCreaSiElPagoNoEstaVerificado() {
        val sinVerificar = RespuestaPayu("4", referencia, "999111", "15000.00", "VISA", "", false)
        var llamado = false
        var resultado: PedidoGestion? = null
        PagoRepositorio.confirmarPedido(sinVerificar) {
            llamado = true
            resultado = it
        }
        assertTrue(llamado)
        assertNull(resultado)
        assertEquals("El pago no está aprobado", PagoRepositorio.mensajeError)
    }

    @Test
    fun pedidoNoSeCreaSiElPagoFueRechazado() {
        val rechazado = RespuestaPayu("6", referencia, "", "15000.00", "VISA", "", true)
        var llamado = false
        var resultado: PedidoGestion? = null
        PagoRepositorio.confirmarPedido(rechazado) {
            llamado = true
            resultado = it
        }
        assertTrue(llamado)
        assertNull(resultado)
        assertEquals("El pago no está aprobado", PagoRepositorio.mensajeError)
    }
}