package com.sasobakery.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.widget.ImageView

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