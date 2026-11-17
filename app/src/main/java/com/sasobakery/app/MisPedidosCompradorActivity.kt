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
import android.widget.Toast
import com.google.firebase.firestore.ListenerRegistration

class MisPedidosCompradorActivity : AppCompatActivity() {

    private val adapter = MisPedidosAdapter { pedido -> abrirSeguimiento(pedido) }

    private lateinit var tvVacio: View
    private var registro: ListenerRegistration? = null

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

    // Escucha los pedidos reales del comprador mientras la pantalla se ve: si el vendedor
    // cambia un estado, aquí se actualiza solo.
    override fun onStart() {
        super.onStart()
        registro = PedidosRepositorio.escucharMios(
            alCambiar = { lista ->
                adapter.submit(lista)
                tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            },
            alFallar = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        )
    }

    override fun onStop() {
        super.onStop()
        registro?.remove()
        registro = null
    }

    private fun abrirSeguimiento(p: PedidoGestion) {
        val intent = Intent(this, PedidoConfirmadoActivity::class.java)
        intent.putExtra(PedidoConfirmadoActivity.EXTRA_PEDIDO_ID, p.id)
        startActivity(intent)
    }
}