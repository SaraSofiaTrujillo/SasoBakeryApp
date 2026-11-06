package com.sasobakery.app

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PedidosVendedorAdapter(
    private val onDetalle: (PedidoGestion) -> Unit,
    private val onAccion: (PedidoGestion) -> Unit
) : RecyclerView.Adapter<PedidosVendedorAdapter.VH>() {

    private var items: List<PedidoGestion> = emptyList()

    class VH(vista: View) : RecyclerView.ViewHolder(vista) {
        val card: View = vista.findViewById(R.id.cardPedido)
        val tvCodigo: TextView = vista.findViewById(R.id.tvCodigo)
        val tvEstado: TextView = vista.findViewById(R.id.tvEstado)
        val tvHorario: TextView = vista.findViewById(R.id.tvHorario)
        val tvCliente: TextView = vista.findViewById(R.id.tvCliente)
        val tvProductos: TextView = vista.findViewById(R.id.tvProductos)
        val btnDetalle: TextView = vista.findViewById(R.id.btnDetalle)
        val btnAccion: TextView = vista.findViewById(R.id.btnAccion)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pedido_gestion, parent, false)
        return VH(vista)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]

        holder.tvCodigo.text = p.codigo
        holder.tvHorario.text = p.textoHorario
        holder.tvCliente.text = p.cliente
        holder.tvProductos.text = p.resumenProductos

        holder.tvEstado.text = p.etiquetaEstado
        holder.tvEstado.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(p.fondoEstado))
        holder.tvEstado.setTextColor(Color.parseColor(p.colorTextoEstado))

        val accion = p.textoAccion
        if (accion == null) {
            holder.btnAccion.visibility = View.GONE
        } else {
            holder.btnAccion.visibility = View.VISIBLE
            holder.btnAccion.text = accion
        }

        holder.card.setOnClickListener { onDetalle(p) }
        holder.btnDetalle.setOnClickListener { onDetalle(p) }
        holder.btnAccion.setOnClickListener { onAccion(p) }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(nuevos: List<PedidoGestion>) {
        items = nuevos
        notifyDataSetChanged()
    }
}