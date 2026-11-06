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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class EntregaCompradorActivity : AppCompatActivity() {

    private lateinit var cardDomicilio: View
    private lateinit var cardRecoger: View
    private lateinit var seccionDireccion: View
    private lateinit var seccionRecoger: View
    private lateinit var tvTituloFecha: TextView
    private lateinit var tvDirEtiqueta: TextView
    private lateinit var tvDirDetalle: TextView
    private lateinit var tvHorario: TextView
    private lateinit var chipsFecha: List<TextView>
    private lateinit var fechas: List<OpcionFecha>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (CarritoRepositorio.cantidadTotal() == 0) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_entrega_comprador)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        cardDomicilio = findViewById(R.id.cardDomicilio)
        cardRecoger = findViewById(R.id.cardRecoger)
        seccionDireccion = findViewById(R.id.seccionDireccion)
        seccionRecoger = findViewById(R.id.seccionRecoger)
        tvTituloFecha = findViewById(R.id.tvTituloFecha)
        tvDirEtiqueta = findViewById(R.id.tvDirEtiqueta)
        tvDirDetalle = findViewById(R.id.tvDirDetalle)
        tvHorario = findViewById(R.id.tvHorario)
        chipsFecha = listOf(
            findViewById(R.id.chipFecha1),
            findViewById(R.id.chipFecha2),
            findViewById(R.id.chipFecha3)
        )
        fechas = EntregaRepositorio.fechasDisponibles()

        findViewById<TextView>(R.id.tvCostoDomicilio).text =
            formatearCop(EntregaRepositorio.COSTO_DOMICILIO)

        findViewById<View>(R.id.btnAtras).setOnClickListener { finish() }

        cardDomicilio.setOnClickListener {
            EntregaRepositorio.tipo = TipoEntrega.ENTREGA
            refrescar()
        }
        cardRecoger.setOnClickListener {
            EntregaRepositorio.tipo = TipoEntrega.RECOJO
            refrescar()
        }

        findViewById<View>(R.id.btnDireccion).setOnClickListener { mostrarDialogoDireccion() }

        chipsFecha.forEachIndexed { indice, chip ->
            chip.setOnClickListener {
                EntregaRepositorio.indiceFecha = indice
                refrescar()
            }
        }

        findViewById<View>(R.id.btnHorario).setOnClickListener { mostrarDialogoHorario() }

        findViewById<View>(R.id.btnContinuarPago).setOnClickListener { continuarAlPago() }

        refrescar()
    }


    private fun refrescar() {
        val domicilio = EntregaRepositorio.tipo == TipoEntrega.ENTREGA

        cardDomicilio.setBackgroundResource(
            if (domicilio) R.drawable.bg_entrega_opcion_seleccionada else R.drawable.bg_users_input
        )
        cardRecoger.setBackgroundResource(
            if (domicilio) R.drawable.bg_users_input else R.drawable.bg_entrega_opcion_seleccionada
        )

        seccionDireccion.visibility = if (domicilio) View.VISIBLE else View.GONE
        seccionRecoger.visibility = if (domicilio) View.GONE else View.VISIBLE
        tvTituloFecha.text = if (domicilio) "Fecha y hora" else "Fecha y hora de recogida"

        if (EntregaRepositorio.tieneDireccion()) {
            tvDirEtiqueta.text = EntregaRepositorio.etiquetaDireccion
            tvDirDetalle.text = EntregaRepositorio.direccion
        } else {
            tvDirEtiqueta.text = "Agregar dirección"
            tvDirDetalle.text = "Toca para escribir tu dirección de entrega"
        }

        chipsFecha.forEachIndexed { indice, chip ->
            val f = fechas[indice]
            chip.text = "${f.dia}\n${f.fecha}"
            if (indice == EntregaRepositorio.indiceFecha) {
                chip.setBackgroundResource(R.drawable.bg_users_chip_selected)
                chip.setTextColor(0xFFFFFFFF.toInt())
            } else {
                chip.setBackgroundResource(R.drawable.bg_users_chip)
                chip.setTextColor(0xFF5D477F.toInt())
            }
        }

        tvHorario.text = EntregaRepositorio.horario
    }

    private fun mostrarDialogoHorario() {
        val opciones = EntregaRepositorio.HORARIOS.toTypedArray()
        val actual = EntregaRepositorio.HORARIOS.indexOf(EntregaRepositorio.horario)

        MaterialAlertDialogBuilder(this)
            .setTitle("Horario")
            .setSingleChoiceItems(opciones, actual) { dialogo, posicion ->
                EntregaRepositorio.horario = EntregaRepositorio.HORARIOS[posicion]
                refrescar()
                dialogo.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoDireccion() {
        val dialogo = BottomSheetDialog(this)
        val vista = layoutInflater.inflate(R.layout.dialog_direccion_entrega, null)

        val etEtiqueta = vista.findViewById<EditText>(R.id.etDirEtiqueta)
        val etDireccion = vista.findViewById<EditText>(R.id.etDirDireccion)

        etEtiqueta.setText(EntregaRepositorio.etiquetaDireccion)
        etDireccion.setText(EntregaRepositorio.direccion)

        vista.findViewById<View>(R.id.btnCancelarDireccion).setOnClickListener {
            dialogo.dismiss()
        }

        vista.findViewById<View>(R.id.btnGuardarDireccion).setOnClickListener {
            val direccion = etDireccion.text.toString().trim()
            if (direccion.isEmpty()) {
                etDireccion.error = "Escribe tu dirección"
                etDireccion.requestFocus()
                return@setOnClickListener
            }

            EntregaRepositorio.etiquetaDireccion =
                etEtiqueta.text.toString().trim().ifEmpty { "Dirección" }
            EntregaRepositorio.direccion = direccion

            refrescar()
            dialogo.dismiss()
            mensaje("Dirección guardada")
        }

        dialogo.setContentView(vista)
        dialogo.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialogo.behavior.skipCollapsed = true
        dialogo.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialogo.show()
    }

    private fun continuarAlPago() {
        if (EntregaRepositorio.tipo == TipoEntrega.ENTREGA && !EntregaRepositorio.tieneDireccion()) {
            mensaje("Agrega la dirección de entrega")
            mostrarDialogoDireccion()
            return
        }

        startActivity(android.content.Intent(this, PagoCompradorActivity::class.java))
    }

    private fun mensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }
}