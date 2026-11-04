package com.sasobakery.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.widget.ImageView

/**
 * Muestra la foto de un producto en este ImageView:
 * 1) la foto elegida de la galería, 2) la foto del proyecto,
 * 3) si no hay ninguna, el ícono de caja lila (marcador).
 * "relleno" es el espacio (en dp) alrededor del ícono de caja.
 */
fun ImageView.mostrarFotoProducto(producto: ProductoInventario, relleno: Int = 12) {
    val uri = producto.imagenUri
    val recurso = producto.imagen

    when {
        uri != null -> {
            setPadding(0, 0, 0, 0)
            imageTintList = null
            setImageURI(Uri.parse(uri))
        }
        recurso != null -> {
            setPadding(0, 0, 0, 0)
            imageTintList = null
            setImageResource(recurso)
        }
        else -> {
            val pad = (relleno * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            setImageResource(R.drawable.ic_box)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#7B5EA7"))
        }
    }
}