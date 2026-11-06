package com.sasobakery.app

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

enum class EstadoPedido(val etiqueta: String, val fondo: String, val texto: String) {
    PENDIENTE("Nuevo", "#FBE0E5", "#C2485F"),
    EN_PREPARACION("Preparando", "#E6DDF5", "#5D477F"),
    LISTO("Listo", "#D6F0EC", "#2F7F76"),
    ENTREGADO("Entregado", "#DDF2E6", "#3F7D5A")
}

data class Pedido(
    val codigo: String,
    val cliente: String,
    val atiende: String,
    val fecha: String,
    val total: Int,
    val estado: EstadoPedido
)

private fun formatoPesos(valor: Int): String =
    String.format(Locale.US, "%,d", valor).replace(",", ".")

class PedidosAdapter(
    private val onClick: (Pedido) -> Unit
) : RecyclerView.Adapter<PedidosAdapter.VH>() {

    private var items: List<Pedido> = emptyList()

    class VH(vista: View) : RecyclerView.ViewHolder(vista) {
        val card: View = vista.findViewById(R.id.cardPedido)
        val tvCodigo: TextView = vista.findViewById(R.id.tvCodigo)
        val tvEstado: TextView = vista.findViewById(R.id.tvEstado)
        val tvCliente: TextView = vista.findViewById(R.id.tvCliente)
        val tvAtiende: TextView = vista.findViewById(R.id.tvAtiende)
        val tvFecha: TextView = vista.findViewById(R.id.tvFecha)
        val tvTotal: TextView = vista.findViewById(R.id.tvTotal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pedido, parent, false)
        return VH(vista)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.tvCodigo.text = p.codigo
        holder.tvCliente.text = p.cliente
        holder.tvAtiende.text = "Atendido por ${p.atiende}"
        holder.tvFecha.text = p.fecha
        holder.tvTotal.text = "\$ ${formatoPesos(p.total)}"

        holder.tvEstado.text = p.estado.etiqueta
        holder.tvEstado.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(p.estado.fondo))
        holder.tvEstado.setTextColor(Color.parseColor(p.estado.texto))

        holder.card.setOnClickListener { onClick(p) }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(nuevos: List<Pedido>) {
        items = nuevos
        notifyDataSetChanged()
    }
}