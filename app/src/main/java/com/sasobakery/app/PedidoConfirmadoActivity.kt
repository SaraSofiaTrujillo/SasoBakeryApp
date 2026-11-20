package com.sasobakery.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration

// seguimiento del pedido para el comprador, se actualiza en vivo
class PedidoConfirmadoActivity : AppCompatActivity() {
    companion object {
        // datos que llegan en el intent, el id es el que se usa para buscar
        const val EXTRA_CODIGO = "codigo_pedido"
        const val EXTRA_PEDIDO_ID = "pedido_id"
        const val EXTRA_DESDE_COMPRA = "desde_compra"
    }

    private var codigo = ""
    private var pedidoId = ""
    private var pedido: PedidoGestion? = null
    private var registro: ListenerRegistration? = null
    // true si viene de pagar, asi atras lleva al catalogo y no al carrito
    private var desdeCompra = false

    private lateinit var tvTitulo: TextView
    private lateinit var tvCheck: TextView
    private lateinit var tvConfirmado: TextView
    private lateinit var tvLlegada: TextView
    private lateinit var tvEstadoActual: TextView
    private lateinit var tvTipoEntrega: TextView
    private lateinit var layoutPasos: LinearLayout
    private lateinit var tvDireccionEtiqueta: TextView
    private lateinit var tvDireccion: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // sin id no hay pedido que mostrar
        pedidoId = intent.getStringExtra(EXTRA_PEDIDO_ID).orEmpty()
        desdeCompra = intent.getBooleanExtra(EXTRA_DESDE_COMPRA, false)

        if (pedidoId.isEmpty()) {
            Toast.makeText(this, "No se encontró el pedido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_pedido_confirmado)

        // ajusta los margenes para barras del sistema
        val cabecera = findViewById<View>(R.id.cabecera)
        val scroll = findViewById<View>(R.id.scrollSeguimiento)
        val baseArriba = cabecera.paddingTop
        val baseAbajo = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            cabecera.updatePadding(top = baseArriba + barras.top)
            scroll.updatePadding(bottom = baseAbajo + barras.bottom)
            insets
        }

        tvTitulo = findViewById(R.id.tvTitulo)
        tvCheck = findViewById(R.id.tvCheck)
        tvConfirmado = findViewById(R.id.tvConfirmado)
        tvLlegada = findViewById(R.id.tvLlegada)
        tvEstadoActual = findViewById(R.id.tvEstadoActual)
        tvTipoEntrega = findViewById(R.id.tvTipoEntrega)
        layoutPasos = findViewById(R.id.layoutPasos)
        tvDireccionEtiqueta = findViewById(R.id.tvDireccionEtiqueta)
        tvDireccion = findViewById(R.id.tvDireccion)

        findViewById<View>(R.id.btnAtras).setOnClickListener { salir() }
        // atras hace lo mismo que la flecha
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                salir()
            }
        })

        findViewById<View>(R.id.btnAyuda).setOnClickListener { mostrarAyuda() }
    }

    // escucha el pedido para ver los cambios de estado al instante
    override fun onStart() {
        super.onStart()
        if (pedidoId.isEmpty()) return
        registro = PedidosRepositorio.escucharPedido(
            pedidoId,
            alCambiar = { p ->
                if (p == null) {
                    mensaje("No se encontró el pedido")
                    finish()
                } else {
                    pedido = p
                    codigo = p.codigo
                    mostrar()
                }
            },
            alFallar = { mensaje(it) }
        )
    }

    override fun onStop() {
        super.onStop()
        registro?.remove()
        registro = null
    }

    // pinta el pedido segun su estado
    private fun mostrar() {
        val p = pedido ?: return
        val domicilio = p.tipo == TipoEntrega.ENTREGA

        tvTitulo.text = "Pedido ${p.codigo}"

        if (p.cancelado) {
            tvCheck.text = "✕"
            tvCheck.setTextColor(0xFFC2485F.toInt())
            tvConfirmado.text = "Pedido cancelado"
            tvLlegada.text = "Este pedido fue cancelado"
        } else {
            tvCheck.text = "✓"
            tvCheck.setTextColor(0xFF7B5EA7.toInt())
            tvConfirmado.text = "¡Pedido confirmado!"
            tvLlegada.text = textoLlegada(p, domicilio)
        }

        // cuatro pasos, el texto cambia si es domicilio o recoger
        val titulos = listOf(
            "Pedido confirmado",
            "Preparando tus dulces",
            if (domicilio) "Listo para enviar" else "Listo para recoger",
            if (domicilio) "Entregado" else "Recogido"
        )

        // cuantos pasos van completos y cual esta en curso, -1 si ninguno
        val (completados, actual) = when {
            p.cancelado -> Pair(1, -1)
            p.estado == EstadoPedido.PENDIENTE -> Pair(1, -1)
            p.estado == EstadoPedido.EN_PREPARACION -> Pair(1, 1)
            p.estado == EstadoPedido.LISTO -> Pair(2, 2)
            else -> Pair(4, -1)
        }

        tvEstadoActual.text = when {
            p.cancelado -> "Cancelado"
            p.estado == EstadoPedido.PENDIENTE -> "Pedido recibido"
            p.estado == EstadoPedido.EN_PREPARACION -> "En preparación"
            p.estado == EstadoPedido.LISTO -> titulos[2]
            else -> titulos[3]
        }
        tvTipoEntrega.text = if (domicilio) "A domicilio" else "Recojo en tienda"

        dibujarPasos(p, titulos, completados, actual)

        if (domicilio) {
            tvDireccionEtiqueta.text = "DIRECCIÓN DE ENTREGA"
            tvDireccion.text = p.direccion.ifBlank { "Sin dirección registrada" }
        } else {
            tvDireccionEtiqueta.text = "RECOGIDA EN TIENDA"
            tvDireccion.text = "${p.negocioMostrado} · ${p.direccionTiendaMostrada}"
        }
    }

    // arma la linea de tiempo, completado, en curso o pendiente
    private fun dibujarPasos(
        p: PedidoGestion,
        titulos: List<String>,
        completados: Int,
        actual: Int
    ) {
        layoutPasos.removeAllViews()

        titulos.forEachIndexed { i, titulo ->
            val fila = LayoutInflater.from(this).inflate(R.layout.item_paso_seguimiento, layoutPasos, false)
            val icono = fila.findViewById<TextView>(R.id.tvPasoIcono)
            val linea = fila.findViewById<View>(R.id.lineaPaso)
            val tvTituloPaso = fila.findViewById<TextView>(R.id.tvPasoTitulo)
            val tvDetallePaso = fila.findViewById<TextView>(R.id.tvPasoDetalle)

            val hecho = i < completados
            val enCurso = i == actual

            tvTituloPaso.text = titulo
            tvDetallePaso.text = when {
                i == 0 -> p.fecha
                enCurso -> "Ahora"
                hecho -> "Completado"
                else -> "Pendiente"
            }
            tvTituloPaso.setTextColor(if (hecho || enCurso) 0xFF2E2340.toInt() else 0xFF8A8494.toInt())

            when {
                hecho -> {
                    icono.setBackgroundResource(R.drawable.bg_users_avatar)
                    icono.backgroundTintList = ColorStateList.valueOf(0xFF7B5EA7.toInt())
                    icono.text = "✓"
                    icono.setTextColor(0xFFFFFFFF.toInt())
                }
                enCurso -> {
                    icono.setBackgroundResource(R.drawable.bg_users_avatar)
                    icono.backgroundTintList = null
                    icono.text = "●"
                    icono.setTextColor(0xFF7B5EA7.toInt())
                }
                else -> {
                    icono.setBackgroundResource(R.drawable.bg_users_input)
                    icono.backgroundTintList = null
                    icono.text = ""
                }
            }

            if (i == titulos.lastIndex) {
                linea.visibility = View.GONE
            } else {
                linea.visibility = View.VISIBLE
                val avanzo = (i + 1 < completados) || (i + 1 == actual)
                linea.setBackgroundColor(Color.parseColor(if (avanzo) "#7B5EA7" else "#E6DDF5"))
            }

            layoutPasos.addView(fila)
        }
    }

    // frase con el dia y la franja que eligio
    private fun textoLlegada(p: PedidoGestion, domicilio: Boolean): String {
        if (p.fechaEntrega.isBlank()) return p.textoHorario

        val dia = p.fechaEntrega.substringBefore(" ")
        val resto = p.fechaEntrega.substringAfter(" ", "")
        val cuando = if (dia == "Mañana") "mañana" else "el ${dia.lowercase()} $resto".trim()
        val entre = p.horario.replace(" – ", " y ")

        return if (domicilio) {
            "Tu pedido llegará $cuando entre $entre"
        } else {
            "Podrás recoger tu pedido $cuando entre $entre"
        }
    }

    // datos de contacto de la tienda con boton para llamar
    private fun mostrarAyuda() {
        val pedido = pedido
        val negocio = pedido?.negocioMostrado ?: PerfilVendedorRepositorio.tienda().negocio
        val telefono = pedido?.telefonoMostrado ?: PerfilVendedorRepositorio.tienda().telefono
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Necesitas ayuda?")
            .setMessage("Comunícate con $negocio al $telefono y menciona tu pedido $codigo.")
            .setPositiveButton("Llamar") { _, _ ->
                val numero = telefono.filter { it.isDigit() || it == '+' }
                try {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numero")))
                } catch (e: Exception) {
                    mensaje("No se pudo abrir el teléfono")
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    // si venia de pagar vuelve al catalogo
    private fun salir() {
        if (desdeCompra) {
            val intent = Intent(this, CatalogoCompradorActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
        finish()
    }

    private fun mensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }
}