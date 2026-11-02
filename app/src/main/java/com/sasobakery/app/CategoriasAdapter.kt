package com.sasobakery.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Categoria(
    val nombre: String,
    val cantidadProductos: Int
)

class CategoriasAdapter(
    private val items: List<Categoria>,
    private val onClick: (Categoria) -> Unit
) : RecyclerView.Adapter<CategoriasAdapter.VH>() {

    class VH(vista: View) : RecyclerView.ViewHolder(vista) {
        val fila: View = vista.findViewById(R.id.rowCategoria)
        val tvNombre: TextView = vista.findViewById(R.id.tvNombre)
        val tvCantidad: TextView = vista.findViewById(R.id.tvCantidad)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_categoria, parent, false)
        return VH(vista)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.tvNombre.text = c.nombre
        holder.tvCantidad.text =
            if (c.cantidadProductos == 1) "1 producto" else "${c.cantidadProductos} productos"
        holder.fila.setOnClickListener { onClick(c) }
    }
}