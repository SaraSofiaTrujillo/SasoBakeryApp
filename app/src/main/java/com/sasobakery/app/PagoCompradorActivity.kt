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

// ultimo paso de la compra, elegir metodo de pago y confirmar el pedido
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
    // evita que el boton se toque dos veces mientras se procesa
    private var enCurso = false

    // pago ya aprobado cuyo pedido fallo, se guarda para reintentar sin cobrar otra vez
    private var pagoAprobadoSinPedido: RespuestaPayu? = null

    // recibe lo que devuelve la pantalla de payu
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

        // sin productos no hay nada que pagar
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

    // actualiza metodos y el resumen de valores
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

    // contra entrega crea el pedido directo, en linea abre payu
    private fun pagar() {
        if (enCurso) return
        if (CarritoRepositorio.cantidadTotal() == 0) return

        val metodo = PagoRepositorio.metodoElegido()
        PagoRepositorio.idSeleccionado = metodo.id

        if (metodo.tipo == TipoPago.CONTRA_ENTREGA) {
            crearPedido(null)
            return
        }

        // si ya se pago antes solo se reintenta crear el pedido
        val pendiente = pagoAprobadoSinPedido
        if (pendiente != null) {
            crearPedido(pendiente)
            return
        }
        pagarEnLinea()
    }

    // revisa telefono y stock antes de abrir payu
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

    // segun el estado que devuelve payu, solo se crea el pedido si esta aprobado y verificado
    private fun alResponderPayu(r: RespuestaPayu) {
        when {
            r.aprobada && r.verificada -> crearPedido(r)

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

    // dialogo simple con un solo boton
    private fun avisar(titulo: String, texto: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setMessage(texto)
            .setPositiveButton("Entendido", null)
            .show()
    }

    // guarda el pedido, si fue un pago en linea y falla se avisa con la referencia
    private fun crearPedido(pago: RespuestaPayu?) {
        enCurso = true
        mensaje("Procesando tu pedido…")

        PagoRepositorio.confirmarPedido(pago) { pedido ->
            enCurso = false
            if (isFinishing || isDestroyed) return@confirmarPedido

            if (pedido == null) {
                val motivo = PagoRepositorio.mensajeError ?: "No se pudo crear el pedido"
                if (pago != null) {
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

    // explica que el pago esta aprobado pero el pedido no se guardo
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