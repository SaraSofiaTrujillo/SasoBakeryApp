package com.sasobakery.app

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

enum class Rol(val etiqueta: String) {
    COMPRADOR("Comprador"),
    VENDEDOR("Vendedor"),
    ADMINISTRADOR("Administrador")
}

data class Usuario(
    val iniciales: String,
    val nombre: String,
    val correo: String,
    val rol: Rol,
    val id: Int = 0,
    val activo: Boolean = true   // false = cuenta desactivada
)

class UsuariosAdapter(
    private var items: List<Usuario> = emptyList(),
    // Se llama al tocar los tres puntos: recibe el usuario y la vista donde anclar el menú
    private val alTocarMas: (Usuario, View) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<UsuariosAdapter.VH>() {

    class VH(vista: View) : RecyclerView.ViewHolder(vista) {
        val tvIniciales: TextView = vista.findViewById(R.id.tvIniciales)
        val tvNombre: TextView = vista.findViewById(R.id.tvNombre)
        val tvCorreo: TextView = vista.findViewById(R.id.tvCorreo)
        val tvEstado: TextView = vista.findViewById(R.id.tvEstado)
        val tvRol: TextView = vista.findViewById(R.id.tvRol)
        val btnMas: View = vista.findViewById(R.id.btnMas)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_usuario, parent, false)
        return VH(vista)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val u = items[position]
        holder.tvIniciales.text = u.iniciales
        holder.tvNombre.text = u.nombre
        holder.tvCorreo.text = u.correo
        holder.tvRol.text = u.rol.etiqueta

        // Colores de la etiqueta según el rol: fondo a texto
        val (fondo, texto) = when (u.rol) {
            Rol.COMPRADOR -> "#EFEAF5" to "#6B5B84"
            Rol.VENDEDOR -> "#FBE0E5" to "#C2485F"
            Rol.ADMINISTRADOR -> "#E6DDF5" to "#5D477F"
        }
        holder.tvRol.backgroundTintList = ColorStateList.valueOf(Color.parseColor(fondo))
        holder.tvRol.setTextColor(Color.parseColor(texto))

        // Usuario desactivado: se ve atenuado y con aviso (los tres puntos siguen activos)
        val alfa = if (u.activo) 1f else 0.5f
        holder.tvIniciales.alpha = alfa
        holder.tvNombre.alpha = alfa
        holder.tvCorreo.alpha = alfa
        holder.tvRol.alpha = alfa
        holder.tvEstado.visibility = if (u.activo) View.GONE else View.VISIBLE

        holder.btnMas.setOnClickListener { alTocarMas(u, holder.btnMas) }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(nuevos: List<Usuario>) {
        items = nuevos
        notifyDataSetChanged()
    }
}