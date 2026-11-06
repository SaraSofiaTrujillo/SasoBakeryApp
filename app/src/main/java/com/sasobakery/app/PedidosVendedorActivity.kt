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

enum class FiltroGestion(val etiqueta: String) {
    TODAS("Todas"),
    NUEVOS("Nuevos"),
    EN_PROCESO("En proceso"),
    LISTOS("Listos")
}

class PedidosVendedorActivity : AppCompatActivity() {

    private val adapter = PedidosVendedorAdapter(
        onDetalle = { proximamente() }, // se conecta a la ventana de detalle en el Paso 4
        onAccion = { pedido -> avanzar(pedido) }
    )
    private var filtroActual = FiltroGestion.TODAS

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

        // Menú inferior: Pedidos ya es esta pantalla
        findViewById<View>(R.id.navResumen).setOnClickListener { finish() }
        findViewById<View>(R.id.navProductos).setOnClickListener {
            startActivity(Intent(this, ProductosInventarioActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navMas).setOnClickListener { proximamente() }
    }

    override fun onResume() {
        super.onResume()
        refrescar()
    }

    private fun coincide(p: PedidoGestion, filtro: FiltroGestion): Boolean = when (filtro) {
        FiltroGestion.TODAS -> true
        FiltroGestion.NUEVOS -> !p.cancelado && p.estado == EstadoPedido.PENDIENTE
        FiltroGestion.EN_PROCESO -> !p.cancelado && p.estado == EstadoPedido.EN_PREPARACION
        FiltroGestion.LISTOS -> !p.cancelado && p.estado == EstadoPedido.LISTO
    }

    private fun refrescar() {
        val todos = PedidosVendedorRepositorio.obtenerTodos()

        tvNuevos.text = todos.count { coincide(it, FiltroGestion.NUEVOS) }.toString()
        tvPreparando.text = todos.count { coincide(it, FiltroGestion.EN_PROCESO) }.toString()
        tvListos.text = todos.count { coincide(it, FiltroGestion.LISTOS) }.toString()

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

        val lista = todos.filter { coincide(it, filtroActual) }
        adapter.submit(lista)
        tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun avanzar(pedido: PedidoGestion) {
        val actualizado = PedidosVendedorRepositorio.avanzarEstado(pedido.codigo)
        refrescar()
        if (actualizado != null) {
            Toast.makeText(
                this,
                "${pedido.codigo}: ${pedido.etiquetaEstado} → ${actualizado.etiquetaEstado}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun proximamente() {
        Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
    }
}