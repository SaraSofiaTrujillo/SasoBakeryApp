package com.sasobakery.app

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

// Nivel de stock de un producto. "color" es el color del texto de stock.
enum class NivelStock(val color: String) {
    DISPONIBLE("#3F7D5A"),
    BAJO("#B7791F"),
    AGOTADO("#C2485F")
}

// Producto tal como se ve en el inventario del vendedor (incluye stock).
// Los campos nuevos van al final y tienen valor por defecto, así el código
// anterior que crea productos con 4 datos sigue funcionando.
data class ProductoInventario(
    val nombre: String,
    val categoria: String,
    val precio: Int, // pesos colombianos (COP)
    val stock: Int,
    @DrawableRes val imagen: Int? = null,
    val id: Int = 0, // 0 = todavía no guardado
    val descripcion: String = "",
    val disponible: Boolean = true, // visible en el catálogo
    val porciones: Int = 1,
    val imagenUri: String? = null // foto elegida de la galería
) {
    // Con 5 unidades o menos se considera stock bajo; con 0 está agotado
    val nivel: NivelStock
        get() = when {
            stock <= 0 -> NivelStock.AGOTADO
            stock <= 5 -> NivelStock.BAJO
            else -> NivelStock.DISPONIBLE
        }

    val textoStock: String
        get() = when {
            stock <= 0 -> "Agotado"
            stock == 1 -> "1 disponible"
            else -> "$stock disponibles"
        }
}

// 68000 -> "68.000"
private fun formatoPrecio(valor: Int): String =
    String.format(Locale.US, "%,d", valor).replace(",", ".")

class InventarioAdapter(
    private val onMasClick: (ProductoInventario) -> Unit
) : RecyclerView.Adapter<InventarioAdapter.VH>() {

    private var items: List<ProductoInventario> = emptyList()

    class VH(vista: View) : RecyclerView.ViewHolder(vista) {
        val ivFoto: ImageView = vista.findViewById(R.id.ivFoto)
        val tvNombre: TextView = vista.findViewById(R.id.tvNombre)
        val tvDetalle: TextView = vista.findViewById(R.id.tvDetalle)
        val tvStock: TextView = vista.findViewById(R.id.tvStock)
        val btnMas: View = vista.findViewById(R.id.btnMas)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_producto_inventario, parent, false)
        return VH(vista)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.tvNombre.text = p.nombre
        holder.tvDetalle.text = "${p.categoria} · \$ ${formatoPrecio(p.precio)}"
        holder.tvStock.text = p.textoStock
        holder.tvStock.setTextColor(Color.parseColor(p.nivel.color))

        // Foto: primero la de la galería, luego la del proyecto,
        // y si no hay ninguna se muestra un icono provisional
        if (p.imagenUri != null) {
            holder.ivFoto.setPadding(0, 0, 0, 0)
            holder.ivFoto.imageTintList = null
            holder.ivFoto.setImageURI(Uri.parse(p.imagenUri))
        } else if (p.imagen != null) {
            holder.ivFoto.setPadding(0, 0, 0, 0)
            holder.ivFoto.imageTintList = null
            holder.ivFoto.setImageResource(p.imagen)
        } else {
            val pad = (12 * holder.itemView.resources.displayMetrics.density).toInt()
            holder.ivFoto.setPadding(pad, pad, pad, pad)
            holder.ivFoto.setImageResource(R.drawable.ic_box)
            holder.ivFoto.imageTintList = ColorStateList.valueOf(Color.parseColor("#7B5EA7"))
        }

        holder.btnMas.setOnClickListener { onMasClick(p) }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(nuevos: List<ProductoInventario>) {
        items = nuevos
        notifyDataSetChanged()
    }
}