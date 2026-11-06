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

// Paso 2 de la compra: entrega a domicilio o recojo, dirección, fecha y horario.
// Todo lo que se elige se guarda en EntregaRepositorio.
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

        // Sin productos en el carrito no tiene sentido esta pantalla
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

        // Costo del domicilio en pesos colombianos
        findViewById<TextView>(R.id.tvCostoDomicilio).text =
            formatearCop(EntregaRepositorio.COSTO_DOMICILIO)

        findViewById<View>(R.id.btnAtras).setOnClickListener { finish() }

        // Domicilio o recoger
        cardDomicilio.setOnClickListener {
            EntregaRepositorio.tipo = TipoEntrega.ENTREGA
            refrescar()
        }
        cardRecoger.setOnClickListener {
            EntregaRepositorio.tipo = TipoEntrega.RECOJO
            refrescar()
        }

        // Dirección
        findViewById<View>(R.id.btnDireccion).setOnClickListener { mostrarDialogoDireccion() }

        // Fecha
        chipsFecha.forEachIndexed { indice, chip ->
            chip.setOnClickListener {
                EntregaRepositorio.indiceFecha = indice
                refrescar()
            }
        }

        // Horario
        findViewById<View>(R.id.btnHorario).setOnClickListener { mostrarDialogoHorario() }

        // Continuar al pago
        findViewById<View>(R.id.btnContinuarPago).setOnClickListener { continuarAlPago() }

        refrescar()
    }

    // ---------- Pantalla ----------

    /** Vuelve a dibujar todo según lo que esté elegido en EntregaRepositorio. */
    private fun refrescar() {
        val domicilio = EntregaRepositorio.tipo == TipoEntrega.ENTREGA

        // Tarjetas: la elegida lleva borde morado
        cardDomicilio.setBackgroundResource(
            if (domicilio) R.drawable.bg_entrega_opcion_seleccionada else R.drawable.bg_users_input
        )
        cardRecoger.setBackgroundResource(
            if (domicilio) R.drawable.bg_users_input else R.drawable.bg_entrega_opcion_seleccionada
        )

        // La dirección solo se pide si es a domicilio
        seccionDireccion.visibility = if (domicilio) View.VISIBLE else View.GONE
        seccionRecoger.visibility = if (domicilio) View.GONE else View.VISIBLE
        tvTituloFecha.text = if (domicilio) "Fecha y hora" else "Fecha y hora de recogida"

        // Tarjeta de dirección
        if (EntregaRepositorio.tieneDireccion()) {
            tvDirEtiqueta.text = EntregaRepositorio.etiquetaDireccion
            tvDirDetalle.text = EntregaRepositorio.direccion
        } else {
            tvDirEtiqueta.text = "Agregar dirección"
            tvDirDetalle.text = "Toca para escribir tu dirección de entrega"
        }

        // Chips de fecha: el elegido en morado
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

    // ---------- Diálogos ----------

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

        // Se llenan con lo que ya se haya escrito antes
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
        // Se abre completa y el teclado no tapa los campos
        dialogo.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialogo.behavior.skipCollapsed = true
        dialogo.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialogo.show()
    }

    // ---------- Continuar ----------

    private fun continuarAlPago() {
        // A domicilio es obligatorio tener dirección
        if (EntregaRepositorio.tipo == TipoEntrega.ENTREGA && !EntregaRepositorio.tieneDireccion()) {
            mensaje("Agrega la dirección de entrega")
            mostrarDialogoDireccion()
            return
        }

        // Todo queda guardado en EntregaRepositorio para la pantalla de pago
        startActivity(android.content.Intent(this, PagoCompradorActivity::class.java))
    }

    private fun mensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }
}