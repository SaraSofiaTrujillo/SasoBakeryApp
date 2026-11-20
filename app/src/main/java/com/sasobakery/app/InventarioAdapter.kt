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

// nivel de stock con el color que se muestra
enum class NivelStock(val color: String) {
    DISPONIBLE("#3F7D5A"),
    BAJO("#B7791F"),
    AGOTADO("#C2485F")
}

// producto como se guarda en firestore, lo usan todos los roles
data class ProductoInventario(
    val nombre: String,
    val categoria: String,
    val precio: Int,
    val stock: Int,
    @DrawableRes val imagen: Int? = null,
    val id: String = "",
    val descripcion: String = "",
    val disponible: Boolean = true,
    val porciones: Int = 1,
    val imagenUri: String? = null,
    val destacado: Boolean = false,
    val imagenBase64: String? = null
) {
    // 5 o menos es stock bajo
    val nivel: NivelStock
        get() = when {
            stock <= 0 -> NivelStock.AGOTADO
            stock <= 5 -> NivelStock.BAJO
            else -> NivelStock.DISPONIBLE
        }

    // texto de cuantas unidades quedan
    val textoStock: String
        get() = when {
            stock <= 0 -> "Agotado"
            stock == 1 -> "1 disponible"
            else -> "$stock disponibles"
        }
}

// numero con puntos de miles
private fun formatoPrecio(valor: Int): String =
    String.format(Locale.US, "%,d", valor).replace(",", ".")

// filas del inventario del vendedor
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
        // un producto oculto muestra desactivado en vez del stock
        if (p.disponible) {
            holder.tvStock.text = p.textoStock
            holder.tvStock.setTextColor(Color.parseColor(p.nivel.color))
        } else {
            holder.tvStock.text = "Desactivado"
            holder.tvStock.setTextColor(Color.parseColor("#8A8394"))
        }

        holder.ivFoto.mostrarFotoProducto(p, 12)

        holder.btnMas.setOnClickListener { onMasClick(p) }
    }

    @SuppressLint("NotifyDataSetChanged")
    // cambia la lista y la vuelve a pintar
    fun submit(nuevos: List<ProductoInventario>) {
        items = nuevos
        notifyDataSetChanged()
    }
}