package com.sasobakery.app

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CarritoAdapter(
    private val onMas: (ProductoInventario) -> Unit,
    private val onMenos: (ProductoInventario) -> Unit,
    private val onQuitar: (ProductoInventario) -> Unit
) : RecyclerView.Adapter<CarritoAdapter.VH>() {

    private var items: List<ItemCarrito> = emptyList()
    private var modoEdicion = false

    class VH(vista: View) : RecyclerView.ViewHolder(vista) {
        val ivFoto: ImageView = vista.findViewById(R.id.ivFoto)
        val tvNombre: TextView = vista.findViewById(R.id.tvNombre)
        val tvSubtotal: TextView = vista.findViewById(R.id.tvSubtotal)
        val tvDetalle: TextView = vista.findViewById(R.id.tvDetalle)
        val tvCantidad: TextView = vista.findViewById(R.id.tvCantidad)
        val btnMenos: View = vista.findViewById(R.id.btnMenos)
        val btnMas: View = vista.findViewById(R.id.btnMas)
        val btnQuitar: View = vista.findViewById(R.id.btnQuitar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_carrito, parent, false)
        return VH(vista)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val p = item.producto

        holder.ivFoto.mostrarFotoProducto(p, 20)
        holder.tvNombre.text = p.nombre
        holder.tvSubtotal.text = formatearCop(item.subtotal)
        holder.tvDetalle.text = if (p.porciones == 1) {
            "${p.categoria} · 1 porción"
        } else {
            "${p.categoria} · ${p.porciones} porciones"
        }
        holder.tvCantidad.text = item.cantidad.toString()

        holder.btnMas.alpha = if (CarritoRepositorio.unidadesAgregables(p.id) > 0) 1f else 0.35f

        holder.btnQuitar.visibility = if (modoEdicion) View.VISIBLE else View.GONE

        holder.btnMas.setOnClickListener { onMas(p) }
        holder.btnMenos.setOnClickListener { onMenos(p) }
        holder.btnQuitar.setOnClickListener { onQuitar(p) }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(nuevos: List<ItemCarrito>, edicion: Boolean) {
        items = nuevos
        modoEdicion = edicion
        notifyDataSetChanged()
    }
}