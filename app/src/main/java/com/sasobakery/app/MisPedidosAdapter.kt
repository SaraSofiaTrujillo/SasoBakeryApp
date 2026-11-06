package com.sasobakery.app

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MisPedidosAdapter(
    private val onClick: (PedidoGestion) -> Unit
) : RecyclerView.Adapter<MisPedidosAdapter.VH>() {

    private var items: List<PedidoGestion> = emptyList()

    class VH(vista: View) : RecyclerView.ViewHolder(vista) {
        val card: View = vista.findViewById(R.id.cardPedido)
        val tvCodigo: TextView = vista.findViewById(R.id.tvCodigo)
        val tvEstado: TextView = vista.findViewById(R.id.tvEstado)
        val tvFecha: TextView = vista.findViewById(R.id.tvFecha)
        val tvEntrega: TextView = vista.findViewById(R.id.tvEntrega)
        val tvResumen: TextView = vista.findViewById(R.id.tvResumen)
        val tvTotal: TextView = vista.findViewById(R.id.tvTotal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pedido_comprador, parent, false)
        return VH(vista)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]

        holder.tvCodigo.text = "Pedido ${p.codigo}"
        holder.tvEstado.text = p.etiquetaEstado
        holder.tvEstado.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(p.fondoEstado))
        holder.tvEstado.setTextColor(Color.parseColor(p.colorTextoEstado))

        holder.tvFecha.text = "Hecho: ${p.fecha}"
        holder.tvEntrega.text = p.textoHorario
        holder.tvResumen.text = p.resumenProductos
        holder.tvTotal.text = formatearCop(p.total + p.envio) // pesos colombianos (COP)

        holder.card.setOnClickListener { onClick(p) }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(nuevos: List<PedidoGestion>) {
        items = nuevos
        notifyDataSetChanged()
    }
}