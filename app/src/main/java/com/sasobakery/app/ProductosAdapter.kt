package com.sasobakery.app

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

// Estado con el que el administrador ve cada producto
enum class EstadoProducto(val etiqueta: String) {
    ACTIVO("Activo"),
    STOCK_BAJO("Stock bajo"),
    AGOTADO("Agotado"),
    DESACTIVADO("Desactivado")
}

data class Producto(
    val nombre: String,
    val categoria: String,
    val precio: Int, // pesos colombianos (COP)
    val estado: EstadoProducto,
    @DrawableRes val imagen: Int? = null,
    val imagenBase64: String? = null // foto guardada en Firestore
)

// 28000 -> "28.000"
private fun formatoCop(valor: Int): String =
    String.format(Locale.US, "%,d", valor).replace(",", ".")

class ProductosAdapter(
    private var items: List<Producto> = emptyList()
) : RecyclerView.Adapter<ProductosAdapter.VH>() {

    class VH(vista: View) : RecyclerView.ViewHolder(vista) {
        val ivFoto: ImageView = vista.findViewById(R.id.ivFoto)
        val tvNombre: TextView = vista.findViewById(R.id.tvNombre)
        val tvDetalle: TextView = vista.findViewById(R.id.tvDetalle)
        val tvEstado: TextView = vista.findViewById(R.id.tvEstado)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_producto, parent, false)
        return VH(vista)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.tvNombre.text = p.nombre
        holder.tvDetalle.text = "${p.categoria} · \$ ${formatoCop(p.precio)}"
        holder.tvEstado.text = p.estado.etiqueta

        // Foto de Firestore; si no hay, la del proyecto o un icono provisional
        holder.ivFoto.mostrarFotoDesde(p.imagenBase64, p.imagen, 12)

        // Colores de la etiqueta: fondo a texto
        val (fondo, texto) = when (p.estado) {
            EstadoProducto.ACTIVO -> "#DDF2E6" to "#3F7D5A"
            EstadoProducto.STOCK_BAJO -> "#FCEBD0" to "#B7791F"
            EstadoProducto.AGOTADO -> "#FBE0E0" to "#B23A3A"
            EstadoProducto.DESACTIVADO -> "#EFEAF5" to "#8A8494"
        }
        holder.tvEstado.backgroundTintList = ColorStateList.valueOf(Color.parseColor(fondo))
        holder.tvEstado.setTextColor(Color.parseColor(texto))
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(nuevos: List<Producto>) {
        items = nuevos
        notifyDataSetChanged()
    }
}