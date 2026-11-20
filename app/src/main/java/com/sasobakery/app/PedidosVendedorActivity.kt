package com.sasobakery.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.ActivityNotFoundException

// filtros de la lista de pedidos del vendedor
enum class FiltroGestion(val etiqueta: String) {
    TODAS("Todas"),
    NUEVOS("Nuevos"),
    EN_PROCESO("En proceso"),
    LISTOS("Listos")
}

// lista de pedidos para el vendedor, con filtros y acciones para avanzar o cancelar
class PedidosVendedorActivity : AppCompatActivity() {
    private val adapter = PedidosVendedorAdapter(
        onDetalle = { pedido -> mostrarDetalle(pedido) },
        onAccion = { pedido -> avanzar(pedido) }
    )
    private var filtroActual = FiltroGestion.TODAS

    private var pedidos: List<PedidoGestion> = emptyList()
    private var cargado = false
    // evita doble toque mientras se actualiza un pedido
    private var enCurso = false
    // escucha de firestore, se activa en onStart y se quita en onStop
    private var registro: ListenerRegistration? = null

    private lateinit var tvNuevos: TextView
    private lateinit var tvPreparando: TextView
    private lateinit var tvListos: TextView
    private lateinit var tvVacio: TextView
    private lateinit var chips: List<Pair<TextView, FiltroGestion>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pedidos_vendedor)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        val rv = findViewById<RecyclerView>(R.id.rvPedidos)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        tvNuevos = findViewById(R.id.tvNuevos)
        tvPreparando = findViewById(R.id.tvPreparando)
        tvListos = findViewById(R.id.tvListos)
        tvVacio = findViewById(R.id.tvVacio)

        chips = listOf(
            findViewById<TextView>(R.id.chipTodas) to FiltroGestion.TODAS,
            findViewById<TextView>(R.id.chipNuevos) to FiltroGestion.NUEVOS,
            findViewById<TextView>(R.id.chipEnProceso) to FiltroGestion.EN_PROCESO,
            findViewById<TextView>(R.id.chipListos) to FiltroGestion.LISTOS
        )
        chips.forEach { (chip, filtro) ->
            chip.setOnClickListener {
                filtroActual = filtro
                refrescar()
            }
        }

        findViewById<View>(R.id.btnNotificaciones).setOnClickListener { proximamente() }

        findViewById<View>(R.id.navResumen).setOnClickListener { finish() }
        findViewById<View>(R.id.navProductos).setOnClickListener {
            startActivity(Intent(this, ProductosInventarioActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navMas).setOnClickListener {
            startActivity(Intent(this, PerfilVendedorActivity::class.java))
            finish()
        }
    }

    // pedidos en tiempo real
    override fun onStart() {
        super.onStart()
        registro = PedidosRepositorio.escucharTodos(
            alCambiar = { lista ->
                pedidos = lista
                cargado = true
                refrescar()
            },
            alFallar = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        )
    }

    override fun onStop() {
        super.onStop()
        registro?.remove()
        registro = null
    }

    // si el pedido entra en el filtro, los cancelados solo salen en todas
    private fun coincide(p: PedidoGestion, filtro: FiltroGestion): Boolean = when (filtro) {
        FiltroGestion.TODAS -> true
        FiltroGestion.NUEVOS -> !p.cancelado && p.estado == EstadoPedido.PENDIENTE
        FiltroGestion.EN_PROCESO -> !p.cancelado && p.estado == EstadoPedido.EN_PREPARACION
        FiltroGestion.LISTOS -> !p.cancelado && p.estado == EstadoPedido.LISTO
    }

    // actualiza contadores, chips y lista
    private fun refrescar() {
        tvNuevos.text = pedidos.count { coincide(it, FiltroGestion.NUEVOS) }.toString()
        tvPreparando.text = pedidos.count { coincide(it, FiltroGestion.EN_PROCESO) }.toString()
        tvListos.text = pedidos.count { coincide(it, FiltroGestion.LISTOS) }.toString()

        chips.forEach { (chip, filtro) ->
            chip.text = filtro.etiqueta
            if (filtro == filtroActual) {
                chip.setBackgroundResource(R.drawable.bg_users_chip_selected)
                chip.setTextColor(0xFFFFFFFF.toInt())
            } else {
                chip.setBackgroundResource(R.drawable.bg_users_chip)
                chip.setTextColor(0xFF5D477F.toInt())
            }
        }

        val lista = pedidos.filter { coincide(it, filtroActual) }
        adapter.submit(lista)
        tvVacio.visibility = if (cargado && lista.isEmpty()) View.VISIBLE else View.GONE
    }

    // pasa el pedido al siguiente estado
    private fun avanzar(pedido: PedidoGestion) {
        if (enCurso) return
        enCurso = true
        PedidosRepositorio.avanzar(pedido) { error ->
            enCurso = false
            if (isFinishing || isDestroyed) return@avanzar
            val mensaje = error
                ?: "${pedido.codigo}: ${pedido.etiquetaEstado} → ${pedido.siguienteEstado?.etiqueta}"
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        }
    }

    // hoja con todos los datos del pedido y sus botones
    private fun mostrarDetalle(p: PedidoGestion) {
        val dialogo = BottomSheetDialog(this)
        val vista = layoutInflater.inflate(R.layout.dialog_detalle_pedido, null)

        vista.findViewById<TextView>(R.id.tvDetCodigo).text = "Pedido ${p.codigo}"
        val tvEstado = vista.findViewById<TextView>(R.id.tvDetEstado)
        tvEstado.text = p.etiquetaEstado
        tvEstado.backgroundTintList = ColorStateList.valueOf(Color.parseColor(p.fondoEstado))
        tvEstado.setTextColor(Color.parseColor(p.colorTextoEstado))
        vista.findViewById<TextView>(R.id.tvDetFecha).text = "Hecho: ${p.fecha}"

        vista.findViewById<TextView>(R.id.tvDetCliente).text = p.cliente
        val tvTelefono = vista.findViewById<TextView>(R.id.tvDetTelefono)
        val btnLlamar = vista.findViewById<View>(R.id.btnDetLlamar)
        if (p.clienteTelefono.isBlank()) {
            tvTelefono.text = "Sin teléfono registrado"
            btnLlamar.visibility = View.GONE
        } else {
            tvTelefono.text = Telefono.mostrar(p.clienteTelefono)
            btnLlamar.setOnClickListener {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.clienteTelefono}")))
            }
        }

        vista.findViewById<TextView>(R.id.tvDetEntrega).text = p.textoHorario
        val tvDireccionTitulo = vista.findViewById<TextView>(R.id.tvDetDireccionTitulo)
        val tvDireccion = vista.findViewById<TextView>(R.id.tvDetDireccion)
        if (p.tipo == TipoEntrega.ENTREGA) {
            tvDireccionTitulo.text = "Dirección de entrega"
            tvDireccion.text = p.direccion.ifBlank { "Sin dirección" }
        } else {
            tvDireccionTitulo.text = "Recoge en tienda"
            tvDireccion.text = p.direccionTiendaMostrada
        }

        // el mapa solo sale si es domicilio y se guardo el gps
        val btnMapa = vista.findViewById<View>(R.id.btnDetMapa)
        if (p.tipo == TipoEntrega.ENTREGA && p.tieneUbicacion) {
            btnMapa.setOnClickListener { abrirMapa(p) }
        } else {
            btnMapa.visibility = View.GONE
        }
        val densidad = resources.displayMetrics.density
        // una fila de texto por cada producto
        val contenedor = vista.findViewById<LinearLayout>(R.id.llDetProductos)
        p.productos.forEach { linea ->
            val fila = TextView(this)
            fila.text = "${linea.cantidad} × ${linea.nombre} · ${formatearCop(linea.subtotal)}"
            fila.setTextColor(0xFF2E2340.toInt())
            fila.textSize = 13f
            fila.setPadding(0, (4 * densidad).toInt(), 0, (4 * densidad).toInt())
            contenedor.addView(fila)
        }
        val tvEnvio = vista.findViewById<TextView>(R.id.tvDetEnvio)
        if (p.envio > 0) {
            tvEnvio.text = "Domicilio: ${formatearCop(p.envio)}"
        } else {
            tvEnvio.visibility = View.GONE
        }
        vista.findViewById<TextView>(R.id.tvDetTotal).text =
            "Total: ${formatearCop(p.total + p.envio)}"
        vista.findViewById<TextView>(R.id.tvDetPago).text = "Pago: ${p.textoPago}"

        val btnAccion = vista.findViewById<TextView>(R.id.btnDetAccion)
        val btnCancelar = vista.findViewById<View>(R.id.btnDetCancelar)
        val accion = p.textoAccion
        if (accion == null) {
            btnAccion.visibility = View.GONE
        } else {
            btnAccion.text = accion
            btnAccion.setOnClickListener {
                dialogo.dismiss()
                avanzar(p)
            }
        }
        // oculta cancelar si ya no se puede, y si no hay ninguna accion oculta la barra
        if (!p.sePuedeCancelar) btnCancelar.visibility = View.GONE
        btnCancelar.setOnClickListener {
            dialogo.dismiss()
            confirmarCancelar(p)
        }
        if (accion == null && !p.sePuedeCancelar) {
            vista.findViewById<View>(R.id.llDetAcciones).visibility = View.GONE
        }

        dialogo.setContentView(vista)
        dialogo.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialogo.behavior.skipCollapsed = true
        dialogo.show()
    }
    // abre la app de mapas, si no hay usa el navegador
    private fun abrirMapa(p: PedidoGestion) {
        val coordenadas = "${p.latitud},${p.longitud}"
        val enApp = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:$coordenadas?q=$coordenadas(${Uri.encode(p.codigo)})")
        )
        try {
            startActivity(enApp)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/search/?api=1&query=$coordenadas")
                    )
                )
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(this, "No hay una app para abrir el mapa", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // pide confirmacion porque devolver el stock no se deshace
    private fun confirmarCancelar(p: PedidoGestion) {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Cancelar el pedido ${p.codigo}?")
            .setMessage(
                "Los productos volverán al inventario y el comprador verá el pedido " +
                        "como cancelado. Esta acción no se puede deshacer."
            )
            .setNegativeButton("Volver", null)
            .setPositiveButton("Sí, cancelar") { _, _ -> cancelar(p) }
            .show()
    }

    // cancela el pedido y regresa el stock
    private fun cancelar(pedido: PedidoGestion) {
        if (enCurso) return
        enCurso = true
        PedidosRepositorio.cancelar(pedido) { error ->
            enCurso = false
            if (isFinishing || isDestroyed) return@cancelar
            Toast.makeText(this, error ?: "${pedido.codigo} cancelado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun proximamente() {
        Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
    }
}