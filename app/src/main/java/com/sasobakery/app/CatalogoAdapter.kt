package com.sasobakery.app

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Tarjetas de producto del catálogo del comprador (cuadrícula de 2 columnas)
class CatalogoAdapter(
    private val onProductoClick: (ProductoInventario) -> Unit,
    private val onAgregarClick: (ProductoInventario) -> Unit,
    private val onFavoritoClick: (ProductoInventario) -> Unit
) : RecyclerView.Adapter<CatalogoAdapter.VH>() {

    private var items: List<ProductoInventario> = emptyList()

    class VH(vista: View) : RecyclerView.ViewHolder(vista) {
        val card: View = vista.findViewById(R.id.cardProducto)
        val ivFoto: ImageView = vista.findViewById(R.id.ivFoto)
        val btnFavorito: View = vista.findViewById(R.id.btnFavorito)
        val ivCorazon: ImageView = vista.findViewById(R.id.ivCorazon)
        val tvAgotado: TextView = vista.findViewById(R.id.tvAgotado)
        val tvCategoria: TextView = vista.findViewById(R.id.tvCategoria)
        val tvNombre: TextView = vista.findViewById(R.id.tvNombre)
        val tvPrecio: TextView = vista.findViewById(R.id.tvPrecio)
        val btnAgregar: View = vista.findViewById(R.id.btnAgregar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_producto_catalogo, parent, false)
        return VH(vista)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]

        holder.ivFoto.mostrarFotoProducto(p, 32)
        holder.tvCategoria.text = p.categoria
        holder.tvNombre.text = p.nombre
        holder.tvPrecio.text = formatearCop(p.precio) // precio real del inventario

        // Agotado: etiqueta visible y botón "+" en gris
        val agotado = p.nivel == NivelStock.AGOTADO
        holder.tvAgotado.visibility = if (agotado) View.VISIBLE else View.GONE
        holder.btnAgregar.backgroundTintList =
            if (agotado) ColorStateList.valueOf(Color.parseColor("#C9C1D6")) else null

        // Corazón: relleno y rosa si el comprador lo marcó
        val esFavorito = FavoritosRepositorio.esFavorito(p.id)
        holder.ivCorazon.setImageResource(
            if (esFavorito) R.drawable.ic_heart_filled else R.drawable.ic_heart
        )
        holder.ivCorazon.imageTintList = ColorStateList.valueOf(
            Color.parseColor(if (esFavorito) "#C2485F" else "#5D477F")
        )

        holder.card.setOnClickListener { onProductoClick(p) }
        holder.btnAgregar.setOnClickListener { onAgregarClick(p) }
        holder.btnFavorito.setOnClickListener {
            onFavoritoClick(p)
            // Se vuelve a dibujar solo esta tarjeta para ver el corazón actualizado
            @Suppress("DEPRECATION")
            val posicion = holder.adapterPosition
            if (posicion != RecyclerView.NO_POSITION) notifyItemChanged(posicion)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(nuevos: List<ProductoInventario>) {
        items = nuevos
        notifyDataSetChanged()
    }
}