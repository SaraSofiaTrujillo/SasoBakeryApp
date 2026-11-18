package com.sasobakery.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

/**
 * Paso 3 de la compra: elegir cómo pagar y ver el resumen. Hay dos métodos:
 * contra entrega (crea el pedido de inmediato) y pago en línea con PayU (abre el
 * checkout en la pantalla "Pago seguro" y, si el pago se aprueba y se verifica,
 * crea el pedido).
 */
class PagoCompradorActivity : AppCompatActivity() {

    private val adapter = MetodoPagoAdapter { metodo ->
        PagoRepositorio.idSeleccionado = metodo.id
        refrescar()
    }

    private lateinit var tvResumenProductos: TextView
    private lateinit var tvResumenSubtotal: TextView
    private lateinit var tvResumenEnvio: TextView
    private lateinit var tvResumenTotal: TextView
    private lateinit var btnPagar: TextView
    private var enCurso = false // evita crear dos pedidos o abrir dos pagos por un doble toque

    // Pago ya aprobado cuyo pedido no se pudo guardar: se reintenta sin cobrar otra vez
    private var pagoAprobadoSinPedido: RespuestaPayu? = null

    // Recibe el resultado de la pantalla "Pago seguro" (PayU)
    private val checkoutPayu = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        enCurso = false
        val datos = resultado.data
        if (resultado.resultCode != RESULT_OK || datos == null) {
            mensaje("Pago cancelado. No se realizó ningún cobro.")
            return@registerForActivityResult
        }
        val respuesta = IntentCompat.getSerializableExtra(
            datos, PayuCheckoutActivity.EXTRA_RESPUESTA, RespuestaPayu::class.java
        )
        if (respuesta == null) {
            mensaje("No se pudo leer la respuesta de PayU")
            return@registerForActivityResult
        }
        alResponderPayu(respuesta)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sin productos en el carrito no tiene sentido esta pantalla
        if (CarritoRepositorio.cantidadTotal() == 0) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_pago_comprador)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        tvResumenProductos = findViewById(R.id.tvResumenProductos)
        tvResumenSubtotal = findViewById(R.id.tvResumenSubtotal)
        tvResumenEnvio = findViewById(R.id.tvResumenEnvio)
        tvResumenTotal = findViewById(R.id.tvResumenTotal)
        btnPagar = findViewById(R.id.btnPagar)

        val rv = findViewById<RecyclerView>(R.id.rvMetodos)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<View>(R.id.btnAtras).setOnClickListener { finish() }
        btnPagar.setOnClickListener { pagar() }

        refrescar()
    }

    // ---------- Pantalla ----------

    /** Vuelve a dibujar las opciones, el resumen y el botón con los datos actuales. */
    private fun refrescar() {
        adapter.submit(PagoRepositorio.metodos(), PagoRepositorio.metodoElegido().id)

        val unidades = CarritoRepositorio.cantidadTotal()
        tvResumenProductos.text = if (unidades == 1) "1 producto" else "$unidades productos"
        tvResumenSubtotal.text = formatearCop(CarritoRepositorio.total())

        val envio = EntregaRepositorio.costoEnvio()
        tvResumenEnvio.text = if (envio == 0) "Sin costo" else formatearCop(envio)

        val total = EntregaRepositorio.totalConEnvio()
        tvResumenTotal.text = formatearCop(total)
        btnPagar.text = "Pagar ${formatearCop(total)}"
    }

    // ---------- Pagar ----------

    /** Según el método elegido: crea el pedido contra entrega o inicia el pago en línea. */
    private fun pagar() {
        if (enCurso) return
        // Si ya se pagó (carrito vacío), un segundo toque no hace nada
        if (CarritoRepositorio.cantidadTotal() == 0) return

        val metodo = PagoRepositorio.metodoElegido()
        PagoRepositorio.idSeleccionado = metodo.id

        if (metodo.tipo == TipoPago.CONTRA_ENTREGA) {
            crearPedido(null)
            return
        }

        // Si ya se cobró un pago pero el pedido no se guardó, se reintenta sin cobrar otra vez
        val pendiente = pagoAprobadoSinPedido
        if (pendiente != null) {
            crearPedido(pendiente)
            return
        }
        pagarEnLinea()
    }

    /** Revisa el carrito, el teléfono y el stock, y abre la pantalla "Pago seguro". */
    private fun pagarEnLinea() {
        val problema = PagoRepositorio.validarAntesDePagar()
        if (problema != null) {
            Toast.makeText(this, problema, Toast.LENGTH_LONG).show()
            return
        }

        enCurso = true
        val intent = Intent(this, PayuCheckoutActivity::class.java)
            .putExtra(PayuCheckoutActivity.EXTRA_REFERENCIA, PayuConfig.nuevaReferencia())
            .putExtra(PayuCheckoutActivity.EXTRA_MONTO, EntregaRepositorio.totalConEnvio())
            .putExtra(PayuCheckoutActivity.EXTRA_DESCRIPCION, "PedidoSasoBakery")
            .putExtra(
                PayuCheckoutActivity.EXTRA_CORREO,
                FirebaseAuth.getInstance().currentUser?.email.orEmpty()
            )
        checkoutPayu.launch(intent)
    }

    /** Decide qué hacer con lo que devolvió PayU. */
    private fun alResponderPayu(r: RespuestaPayu) {
        when {
            // Aprobado y verificado: ahora sí se crea el pedido
            r.aprobada && r.verificada -> crearPedido(r)

            // PayU dice aprobado pero los datos no coinciden: no se crea el pedido
            r.aprobada -> avisar(
                "No pudimos verificar el pago",
                "PayU reportó el pago como aprobado, pero la firma, la referencia o el valor " +
                        "no coinciden. No se creó el pedido.\n\n" +
                        "Si ya te cobraron, contacta a la tienda con la referencia ${r.referencia}."
            )

            r.estado == "7" -> avisar(
                "Pago pendiente",
                "Tu pago quedó pendiente de confirmación en PayU y todavía no se creó el pedido.\n\n" +
                        "Si ya te cobraron, contacta a la tienda con la referencia ${r.referencia}."
            )

            else -> avisar(
                "Pago no completado",
                "Estado: ${r.textoEstado}. No se hizo ningún cobro y tu carrito sigue igual.\n\n" +
                        "Puedes intentar de nuevo, o elegir pago contra entrega."
            )
        }
    }

    private fun avisar(titulo: String, texto: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setMessage(texto)
            .setPositiveButton("Entendido", null)
            .show()
    }

    /**
     * Crea el pedido en Firestore, descuenta el stock y vacía el carrito. [pago] es el pago
     * de PayU ya aprobado, o null si es contra entrega.
     */
    private fun crearPedido(pago: RespuestaPayu?) {
        enCurso = true
        mensaje("Procesando tu pedido…")

        PagoRepositorio.confirmarPedido(pago) { pedido ->
            enCurso = false
            if (isFinishing || isDestroyed) return@confirmarPedido

            if (pedido == null) {
                val motivo = PagoRepositorio.mensajeError ?: "No se pudo crear el pedido"
                if (pago != null) {
                    // El dinero ya se cobró: se conserva el pago para poder reintentar
                    pagoAprobadoSinPedido = pago
                    mostrarPagoSinPedido(pago, motivo)
                } else {
                    Toast.makeText(this, motivo, Toast.LENGTH_LONG).show()
                }
                return@confirmarPedido
            }

            pagoAprobadoSinPedido = null
            val intent = Intent(this, PedidoConfirmadoActivity::class.java)
            intent.putExtra(PedidoConfirmadoActivity.EXTRA_PEDIDO_ID, pedido.id)
            intent.putExtra(PedidoConfirmadoActivity.EXTRA_DESDE_COMPRA, true)
            startActivity(intent)
        }
    }

    /** Pago aprobado pero pedido sin guardar: se ofrece reintentar sin volver a cobrar. */
    private fun mostrarPagoSinPedido(pago: RespuestaPayu, motivo: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Pago aprobado, pedido sin guardar")
            .setMessage(
                "Tu pago se aprobó (referencia ${pago.referencia}), pero no pudimos crear " +
                        "el pedido: $motivo\n\n" +
                        "No se te volverá a cobrar. Puedes reintentar, o contactar a la tienda " +
                        "con esta referencia."
            )
            .setNegativeButton("Cerrar", null)
            .setPositiveButton("Reintentar") { _, _ -> crearPedido(pago) }
            .show()
    }

    private fun mensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }
}