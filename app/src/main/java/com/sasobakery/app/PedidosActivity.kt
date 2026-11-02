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

enum class FiltroPedidos(val nombre: String) {
    TODOS("Todos"),
    EN_CURSO("En curso"),
    COMPLETADOS("Completados")
}

class PedidosActivity : AppCompatActivity() {

    // Datos de ejemplo en pesos colombianos (luego vendrán de la base de datos).
    // Es "var" porque al cambiar el estado de un pedido se reemplaza la lista.
    private var pedidos: List<Pedido> = listOf(
        Pedido("#SB-1046", "Valentina Mendoza", "Camila R.", "Hoy, 9:02", 84000, EstadoPedido.EN_PREPARACION),
        Pedido("#SB-1047", "María Flores", "Camila R.", "Hoy, 10:32", 42000, EstadoPedido.PENDIENTE),
        Pedido("#SB-1044", "Diego Rojas", "Jorge M.", "Hoy, 8:50", 76000, EstadoPedido.LISTO),
        Pedido("#SB-1045", "Lucía Vega", "Jorge M.", "Hoy, 10:05", 62000, EstadoPedido.ENTREGADO)
    )

    private val adapter = PedidosAdapter { pedido -> avanzarEstado(pedido) }
    private var filtroActual = FiltroPedidos.TODOS
    private lateinit var chips: List<Pair<TextView, FiltroPedidos>>

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

        // Menú inferior
        findViewById<View>(R.id.navResumen).setOnClickListener { finish() }
        findViewById<View>(R.id.navProductos).setOnClickListener {
            startActivity(Intent(this, ProductosCategoriasActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navMas).setOnClickListener {
            startActivity(Intent(this, UsuariosRolesActivity::class.java))
            finish()
        }

        refrescar()
    }

    // ---------- Regla única: se usa para CONTAR y para FILTRAR ----------

    private fun coincide(p: Pedido, filtro: FiltroPedidos): Boolean = when (filtro) {
        FiltroPedidos.TODOS -> true
        FiltroPedidos.EN_CURSO -> p.estado != EstadoPedido.ENTREGADO
        FiltroPedidos.COMPLETADOS -> p.estado == EstadoPedido.ENTREGADO
    }

    private fun contar(filtro: FiltroPedidos): Int =
        pedidos.count { coincide(it, filtro) }

    // ---------- Actualización de pantalla ----------

    /** Recalcula los contadores, el estilo de los chips y la lista visible. */
    private fun refrescar() {
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
        adapter.submit(pedidos.filter { coincide(it, filtroActual) })
    }

    // ---------- Cambio de estado ----------

    /** Reemplaza el estado de un pedido y vuelve a calcular todo. */
    private fun cambiarEstado(codigo: String, nuevoEstado: EstadoPedido) {
        pedidos = pedidos.map { p ->
            if (p.codigo == codigo) p.copy(estado = nuevoEstado) else p
        }
        refrescar()
    }

    private fun siguienteEstado(estado: EstadoPedido): EstadoPedido? = when (estado) {
        EstadoPedido.PENDIENTE -> EstadoPedido.EN_PREPARACION
        EstadoPedido.EN_PREPARACION -> EstadoPedido.LISTO
        EstadoPedido.LISTO -> EstadoPedido.ENTREGADO
        EstadoPedido.ENTREGADO -> null
    }

    // TEMPORAL: para probar los contadores. Al tocar una tarjeta, el pedido avanza de estado.
    // Cuando exista la pantalla de detalle del pedido, se puede borrar esta función.
    private fun avanzarEstado(pedido: Pedido) {
        val siguiente = siguienteEstado(pedido.estado)
        if (siguiente == null) {
            Toast.makeText(this, "${pedido.codigo} ya fue entregado", Toast.LENGTH_SHORT).show()
            return
        }
        cambiarEstado(pedido.codigo, siguiente)
        Toast.makeText(
            this,
            "${pedido.codigo}: ${pedido.estado.etiqueta} → ${siguiente.etiqueta}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun proximamente() {
        Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
    }
}