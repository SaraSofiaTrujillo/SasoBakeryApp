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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration

// filtros de la lista de pedidos del admin
enum class FiltroPedidos(val nombre: String) {
    TODOS("Todos"),
    EN_CURSO("En curso"),
    COMPLETADOS("Completados")
}

// pedidos del admin con resumen, filtros y detalle
class PedidosActivity : AppCompatActivity() {
    private var pedidos: List<PedidoGestion> = emptyList()
    private var cargado = false
    // evita doble toque mientras se actualiza un pedido
    private var enCurso = false
    // escucha de firestore, se activa en onStart y se quita en onStop
    private var registro: ListenerRegistration? = null

    private val adapter = PedidosAdapter { pedido -> abrirDetalle(pedido.codigo) }
    private var filtroActual = FiltroPedidos.TODOS
    private lateinit var chips: List<Pair<TextView, FiltroPedidos>>

    private lateinit var tvTotalPedidos: TextView
    private lateinit var tvVentas: TextView
    private lateinit var tvAtencion: TextView
    private lateinit var tvVacio: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pedidos)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        val rv = findViewById<RecyclerView>(R.id.rvPedidos)
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = adapter

        tvTotalPedidos = findViewById(R.id.tvTotalPedidos)
        tvVentas = findViewById(R.id.tvVentas)
        tvAtencion = findViewById(R.id.tvAtencion)
        tvVacio = findViewById(R.id.tvVacioPedidos)

        chips = listOf(
            findViewById<TextView>(R.id.chipTodos) to FiltroPedidos.TODOS,
            findViewById<TextView>(R.id.chipEnCurso) to FiltroPedidos.EN_CURSO,
            findViewById<TextView>(R.id.chipCompletados) to FiltroPedidos.COMPLETADOS
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
            startActivity(Intent(this, ProductosCategoriasActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navUsuarios).setOnClickListener {
            startActivity(Intent(this, UsuariosRolesActivity::class.java))
            finish()
        }

        refrescar()
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

    // si el pedido entra en el filtro
    private fun coincide(p: PedidoGestion, filtro: FiltroPedidos): Boolean = when (filtro) {
        FiltroPedidos.TODOS -> true
        FiltroPedidos.EN_CURSO -> !p.cancelado && p.estado != EstadoPedido.ENTREGADO
        FiltroPedidos.COMPLETADOS -> !p.cancelado && p.estado == EstadoPedido.ENTREGADO
    }

    private fun contar(filtro: FiltroPedidos): Int = pedidos.count { coincide(it, filtro) }

    // las ventas y pendientes no cuentan los cancelados
    private fun refrescar() {
        val validos = pedidos.filter { !it.cancelado }
        tvTotalPedidos.text = pedidos.size.toString()
        tvVentas.text = formatearCop(validos.sumOf { it.total + it.envio })
        tvAtencion.text = validos.count { it.estado == EstadoPedido.PENDIENTE }.toString()

        chips.forEach { (chip, filtro) ->
            chip.text = "${filtro.nombre} ${contar(filtro)}"
            if (filtro == filtroActual) {
                chip.setBackgroundResource(R.drawable.bg_users_chip_selected)
                chip.setTextColor(0xFFFFFFFF.toInt())
            } else {
                chip.setBackgroundResource(R.drawable.bg_users_chip)
                chip.setTextColor(0xFF5D477F.toInt())
            }
        }

        val lista = pedidos.filter { coincide(it, filtroActual) }
        adapter.submit(lista.map { aTarjeta(it) })
        tvVacio.visibility = if (cargado && lista.isEmpty()) View.VISIBLE else View.GONE
    }

    // pasa el pedido a lo que muestra la tarjeta
    private fun aTarjeta(p: PedidoGestion) = Pedido(
        codigo = p.codigo,
        cliente = p.cliente,
        atiende = (if (p.tipo == TipoEntrega.ENTREGA) "Domicilio" else "Recoge en tienda") +
                " · " + (if (p.pagoEstado == "APROBADO") "Pago en línea" else "Contra entrega"),
        fecha = p.fecha,
        total = p.total + p.envio,
        estado = p.estado,
        cancelado = p.cancelado
    )

    // detalle compartido con el vendedor
    private fun abrirDetalle(codigo: String) {
        val pedido = pedidos.firstOrNull { it.codigo == codigo } ?: return
        DetallePedidoDialogo.mostrar(
            this, pedido,
            alAvanzar = { avanzar(it) },
            alCancelar = { confirmarCancelar(it) }
        )
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