package com.sasobakery.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Historial de pedidos del comprador.
// Lee los pedidos de PedidosVendedorRepositorio, así que cuando el vendedor cambia
// un estado, aquí se ve reflejado.
class MisPedidosCompradorActivity : AppCompatActivity() {

    private val adapter = MisPedidosAdapter { pedido -> abrirSeguimiento(pedido) }

    private lateinit var tvVacio: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_mis_pedidos)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        tvVacio = findViewById(R.id.tvVacio)
        findViewById<View>(R.id.btnAtras).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvPedidos)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
    }

    // Cada vez que se vuelve a esta pantalla se leen de nuevo los pedidos
    override fun onResume() {
        super.onResume()
        val lista = PerfilCompradorRepositorio.misPedidos()
        adapter.submit(lista)
        tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun abrirSeguimiento(p: PedidoGestion) {
        val intent = Intent(this, PedidoConfirmadoActivity::class.java)
        intent.putExtra(PedidoConfirmadoActivity.EXTRA_CODIGO, p.codigo)
        startActivity(intent)
    }
}