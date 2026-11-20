package com.sasobakery.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

// hoja con el detalle de un pedido, la usa la pantalla del admin
object DetallePedidoDialogo {
    // arma la hoja, avanzar y cancelar los resuelve la pantalla que la llama
    fun mostrar(
        actividad: AppCompatActivity,
        p: PedidoGestion,
        alAvanzar: (PedidoGestion) -> Unit,
        alCancelar: (PedidoGestion) -> Unit
    ) {
        val dialogo = BottomSheetDialog(actividad)
        val vista = actividad.layoutInflater.inflate(R.layout.dialog_detalle_pedido, null)

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
                actividad.startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.clienteTelefono}"))
                )
            }
        }

        vista.findViewById<TextView>(R.id.tvDetEntrega).text = p.textoHorario
        val tvDireccionTitulo = vista.findViewById<TextView>(R.id.tvDetDireccionTitulo)
        val tvDireccion = vista.findViewById<TextView>(R.id.tvDetDireccion)
        val btnMapa = vista.findViewById<View>(R.id.btnDetMapa)
        // domicilio muestra la direccion del cliente, recoger la de la tienda
        if (p.tipo == TipoEntrega.ENTREGA) {
            tvDireccionTitulo.text = "Dirección de entrega"
            tvDireccion.text = p.direccion.ifBlank { "Sin dirección" }
        } else {
            tvDireccionTitulo.text = "Recoge en tienda"
            tvDireccion.text = p.direccionTiendaMostrada
        }
        // el boton de mapa solo sale si se guardo el gps
        if (p.tipo == TipoEntrega.ENTREGA && p.tieneUbicacion) {
            btnMapa.visibility = View.VISIBLE
            btnMapa.setOnClickListener { abrirMapa(actividad, p) }
        } else {
            btnMapa.visibility = View.GONE
        }

        val densidad = actividad.resources.displayMetrics.density
        // una fila de texto por cada producto
        val contenedor = vista.findViewById<LinearLayout>(R.id.llDetProductos)
        p.productos.forEach { linea ->
            val fila = TextView(actividad)
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
                alAvanzar(p)
            }
        }
        // oculta cancelar si ya no se puede, y si no hay ninguna accion oculta la barra
        if (!p.sePuedeCancelar) btnCancelar.visibility = View.GONE
        btnCancelar.setOnClickListener {
            dialogo.dismiss()
            alCancelar(p)
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
    private fun abrirMapa(actividad: AppCompatActivity, p: PedidoGestion) {
        val latitud = p.latitud ?: return
        val longitud = p.longitud ?: return
        val geo = Uri.parse("geo:$latitud,$longitud?q=$latitud,$longitud(${Uri.encode(p.codigo)})")
        try {
            actividad.startActivity(Intent(Intent.ACTION_VIEW, geo))
        } catch (e: ActivityNotFoundException) {
            val web = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitud,$longitud")
            actividad.startActivity(Intent(Intent.ACTION_VIEW, web))
        }
    }
}