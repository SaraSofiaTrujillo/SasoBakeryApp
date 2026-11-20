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

// Aqui probamos que el pago de PayU se verifique bien
// Simulamos respuestas reales y otras alteradas para ver que solo pase la buena
// Corren en el emulador porque necesitan android net Uri
@RunWith(AndroidJUnit4::class)
class PayuVerificacionTest {

    // Datos del pedido de prueba que esperamos que PayU nos confirme
    private val referencia = "SB1700000000000"
    private val monto = 15000

    // Convierte un texto en su huella MD5 que es lo que usa PayU para firmar
    private fun md5(texto: String): String =
        MessageDigest.getInstance("MD5").digest(texto.toByteArray())
            .joinToString("") { "%02x".format(it) }

    // Calcula la firma igual que lo haria PayU con el valor redondeado a un decimal
    private fun firmaDe(
        merchantId: String, ref: String, valor: String, moneda: String, estado: String
    ): String {
        val v = BigDecimal(valor).setScale(1, RoundingMode.HALF_EVEN).toPlainString()
        return md5("${PayuConfig.API_KEY}~$merchantId~$ref~$v~$moneda~$estado")
    }

    // Arma la url de respuesta y si no le pasamos firma usa la correcta
    // Cada dato se puede cambiar para simular una respuesta alterada
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
        // Solo agregamos la firma si existe para poder probar el caso sin firma
        if (firma != null) b.appendQueryParameter("signature", firma)
        return b.build()
    }

    // Le pasa la respuesta a la app junto con el pedido que esperamos
    private fun interpretar(uri: Uri) =
        PayuConfig.interpretarRespuesta(uri, referencia, monto)

    // Un pago real y aprobado debe quedar verificado y con su numero de transaccion
    @Test
    fun respuestaAutenticaAprobada_seVerifica() {
        val r = interpretar(respuesta())
        assertTrue(r.aprobada)
        assertTrue(r.verificada)
        assertEquals("999111", r.transaccion)
    }

    // Un pago real pero rechazado se verifica pero no cuenta como aprobado
    @Test
    fun respuestaAutenticaRechazada_seVerificaPeroNoEstaAprobada() {
        val r = interpretar(respuesta(estado = "6"))
        assertFalse(r.aprobada)
        assertTrue(r.verificada)
    }

    // Si alguien cambia un solo caracter de la firma el pago no se acepta
    @Test
    fun firmaAlterada_seRechaza() {
        val buena = firmaDe(PayuConfig.MERCHANT_ID, referencia, "15000.00", PayuConfig.MONEDA, "4")
        val mala = buena.dropLast(1) + (if (buena.last() == '0') '1' else '0')
        assertFalse(interpretar(respuesta(firma = mala)).verificada)
    }

    // Una respuesta que llega sin firma no se puede confiar
    @Test
    fun sinFirma_seRechaza() {
        assertFalse(interpretar(respuesta(firma = null)).verificada)
    }

    // Aunque la firma sea valida si es de otro pedido no sirve para este
    @Test
    fun referenciaDistinta_seRechazaAunqueLaFirmaSeaValida() {
        assertFalse(interpretar(respuesta(ref = "SB1700000009999")).verificada)
    }

    // Si pagaron menos de lo que cuesta el pedido se rechaza
    @Test
    fun valorMenor_seRechazaAunqueLaFirmaSeaValida() {
        assertFalse(interpretar(respuesta(valor = "100.00")).verificada)
    }

    // Si el valor no es un numero la app no debe fallar sino rechazarlo
    @Test
    fun valorNoNumerico_seRechaza() {
        assertFalse(interpretar(respuesta(valor = "abc", firma = "x")).verificada)
    }

    // Revisa que no se pueda hacer pasar un pago rechazado como aprobado
    @Test
    fun estadoCambiadoDeRechazadoAAprobado_seRechaza() {
        // La firma es de un pago rechazado pero le cambiamos el estado a aprobado
        val firmaDeRechazada =
            firmaDe(PayuConfig.MERCHANT_ID, referencia, "15000.00", PayuConfig.MONEDA, "6")
        val r = interpretar(respuesta(estado = "4", firma = firmaDeRechazada))
        assertTrue(r.aprobada)
        assertFalse(r.verificada)
    }

    // Un pago hecho a otro comercio no cuenta para nuestra tienda
    @Test
    fun comercioAjeno_seRechazaAunqueLaFirmaSeaValida() {
        assertFalse(interpretar(respuesta(merchantId = "111111")).verificada)
    }

    // Si el pago llega en otra moneda que no es la nuestra se rechaza
    @Test
    fun monedaDistinta_seRechazaAunqueLaFirmaSeaValida() {
        assertFalse(interpretar(respuesta(moneda = "USD")).verificada)
    }

    // Si el pago no esta verificado no se debe crear el pedido
    // Revisamos que avise con el mensaje de error y no devuelva pedido
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

    // Si el pago es real pero fue rechazado tampoco se crea el pedido
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