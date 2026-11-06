package com.sasobakery.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Calendar
import java.util.Locale

data class PedidoVendedor(
    val codigo: String,
    val hace: String,
    val cliente: String,
    val detalle: String,
    val total: Int,
    val estado: EstadoPedido
)

class VendedorPrincipalActivity : AppCompatActivity() {

    private val nombreVendedor = "Camila"
    private val pedidosHoy = 12
    private val porPreparar = 5
    private val stockBajo = 3
    private val ventasHoy = 648000

    private val pedidosEnCurso = listOf(
        PedidoVendedor("#1048", "Hace 8 min", "Valentina M.", "2 productos · Recojo", 86000, EstadoPedido.EN_PREPARACION),
        PedidoVendedor("#1047", "Hace 8 min", "María F.", "1 producto · Entrega", 62000, EstadoPedido.PENDIENTE),
        PedidoVendedor("#1045", "Hace 8 min", "Diego R.", "3 productos · Recojo", 76000, EstadoPedido.LISTO)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_vendedor_principal)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        mostrarEncabezado()
        mostrarEstadisticas()
        mostrarPedidos()
        configurarBotones()
    }

    private fun mostrarEncabezado() {
        val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val saludo = when {
            hora < 12 -> "Buenos días,"
            hora < 19 -> "Buenas tardes,"
            else -> "Buenas noches,"
        }
        findViewById<TextView>(R.id.tvSaludo).text = saludo
        findViewById<TextView>(R.id.tvNombre).text = nombreVendedor
    }

    private fun mostrarEstadisticas() {
        findViewById<TextView>(R.id.tvPedidosHoy).text = pedidosHoy.toString()
        findViewById<TextView>(R.id.tvPorPreparar).text = porPreparar.toString()
        findViewById<TextView>(R.id.tvStockBajo).text = stockBajo.toString()
        findViewById<TextView>(R.id.tvVentasHoy).text = "\$ ${formatoPesos(ventasHoy)}"
    }

    private fun mostrarPedidos() {
        val contenedor = findViewById<LinearLayout>(R.id.llPedidosEnCurso)
        contenedor.removeAllViews()
        val inflater = LayoutInflater.from(this)

        pedidosEnCurso.forEachIndexed { indice, p ->
            val fila = inflater.inflate(R.layout.item_pedido_vendedor, contenedor, false)

            fila.findViewById<TextView>(R.id.tvCodigo).text = p.codigo
            fila.findViewById<TextView>(R.id.tvHace).text = p.hace
            fila.findViewById<TextView>(R.id.tvCliente).text = p.cliente
            fila.findViewById<TextView>(R.id.tvDetalle).text = p.detalle
            fila.findViewById<TextView>(R.id.tvTotal).text = "\$ ${formatoPesos(p.total)}"

            val estado = fila.findViewById<TextView>(R.id.tvEstado)
            estado.text = p.estado.etiqueta
            estado.backgroundTintList = ColorStateList.valueOf(Color.parseColor(p.estado.fondo))
            estado.setTextColor(Color.parseColor(p.estado.texto))

            fila.findViewById<View>(R.id.divider).visibility =
                if (indice == pedidosEnCurso.lastIndex) View.GONE else View.VISIBLE

            fila.setOnClickListener { proximamente() }
            contenedor.addView(fila)
        }
    }

    private fun configurarBotones() {
        findViewById<View>(R.id.btnNotificaciones).setOnClickListener { proximamente() }
        findViewById<View>(R.id.tvVerTodos).setOnClickListener { abrirPedidos() }
        findViewById<View>(R.id.btnNuevoProducto).setOnClickListener { proximamente() }
        findViewById<View>(R.id.btnActualizarStock).setOnClickListener { proximamente() }

        findViewById<View>(R.id.navPedidos).setOnClickListener { abrirPedidos() }
        findViewById<View>(R.id.navProductos).setOnClickListener { abrirInventario() }
        findViewById<View>(R.id.btnActualizarStock).setOnClickListener { abrirInventario() }
        findViewById<View>(R.id.navMas).setOnClickListener { abrirPerfil() }
    }

    private fun formatoPesos(valor: Int): String =
        String.format(Locale.US, "%,d", valor).replace(",", ".")
    private fun abrirInventario() {
        startActivity(android.content.Intent(this, ProductosInventarioActivity::class.java))
    }
    private fun abrirPedidos() {
        startActivity(android.content.Intent(this, PedidosVendedorActivity::class.java))
    }
    private fun abrirPerfil() {
        startActivity(android.content.Intent(this, PerfilVendedorActivity::class.java))
    }
    private fun proximamente() {
        Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
    }
}