package com.sasobakery.app

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MetodoPagoAdapter(
    private val onClick: (MetodoPago) -> Unit
) : RecyclerView.Adapter<MetodoPagoAdapter.VH>() {

    private var items: List<MetodoPago> = emptyList()
    private var idSeleccionado: String? = null

    class VH(vista: View) : RecyclerView.ViewHolder(vista) {
        val tvBadge: TextView = vista.findViewById(R.id.tvBadge)
        val tvTitulo: TextView = vista.findViewById(R.id.tvTitulo)
        val tvDetalle: TextView = vista.findViewById(R.id.tvDetalle)
        val tvRadio: TextView = vista.findViewById(R.id.tvRadio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_metodo_pago, parent, false)
        return VH(vista)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        val elegido = m.id == idSeleccionado

        holder.tvBadge.text = m.badge
        holder.tvTitulo.text = m.titulo
        holder.tvDetalle.text = m.detalle

        holder.itemView.setBackgroundResource(
            if (elegido) R.drawable.bg_entrega_opcion_seleccionada else R.drawable.bg_users_input
        )

        if (elegido) {
            holder.tvRadio.setBackgroundResource(R.drawable.bg_users_avatar)
            holder.tvRadio.backgroundTintList = ColorStateList.valueOf(0xFF7B5EA7.toInt())
            holder.tvRadio.text = "✓"
        } else {
            holder.tvRadio.setBackgroundResource(R.drawable.bg_users_input)
            holder.tvRadio.backgroundTintList = null
            holder.tvRadio.text = ""
        }

        holder.itemView.setOnClickListener { onClick(m) }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(nuevos: List<MetodoPago>, seleccionado: String) {
        items = nuevos
        idSeleccionado = seleccionado
        notifyDataSetChanged()
    }
}