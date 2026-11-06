package com.sasobakery.app

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        findViewById<View>(R.id.btnNuevaTarjeta).setOnClickListener { mostrarDialogoTarjeta() }
        btnPagar.setOnClickListener { pagar() }

        refrescar()
    }

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


    private fun mostrarDialogoTarjeta() {
        val dialogo = BottomSheetDialog(this)
        val vista = layoutInflater.inflate(R.layout.dialog_nueva_tarjeta, null)

        val etNumero = vista.findViewById<EditText>(R.id.etTarjetaNumero)
        val etVence = vista.findViewById<EditText>(R.id.etTarjetaVence)

        vista.findViewById<View>(R.id.btnCancelarTarjeta).setOnClickListener {
            dialogo.dismiss()
        }

        vista.findViewById<View>(R.id.btnGuardarTarjeta).setOnClickListener {
            val digitos = etNumero.text.toString().filter { it.isDigit() }
            if (digitos.length !in 13..19) {
                etNumero.error = "Escribe un número de tarjeta válido"
                etNumero.requestFocus()
                return@setOnClickListener
            }

            val vence = etVence.text.toString().trim()
            if (!PagoRepositorio.vencimientoValido(vence)) {
                etVence.error = "Usa el formato MM/AA con una fecha vigente"
                etVence.requestFocus()
                return@setOnClickListener
            }

            PagoRepositorio.agregarTarjeta(digitos, vence)
            refrescar()
            dialogo.dismiss()
            mensaje("Tarjeta agregada")
        }

        dialogo.setContentView(vista)
        dialogo.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialogo.behavior.skipCollapsed = true
        dialogo.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialogo.show()
    }


    private fun pagar() {
        if (CarritoRepositorio.cantidadTotal() == 0) return

        PagoRepositorio.idSeleccionado = PagoRepositorio.metodoElegido().id

        val pedido = PagoRepositorio.confirmarPedido()
        if (pedido == null) {
            mensaje(PagoRepositorio.mensajeError ?: "No se pudo crear el pedido")
            return
        }

        val intent = android.content.Intent(this, PedidoConfirmadoActivity::class.java)
        intent.putExtra(PedidoConfirmadoActivity.EXTRA_CODIGO, pedido.codigo)
        intent.putExtra(PedidoConfirmadoActivity.EXTRA_DESDE_COMPRA, true)
        startActivity(intent)
    }

    private fun mensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }
}