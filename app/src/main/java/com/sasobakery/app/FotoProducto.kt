package com.sasobakery.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import android.widget.ImageView
import java.io.ByteArrayOutputStream

// pasa fotos a texto base64 para firestore y de texto a bitmap para mostrarlas
object ImagenBase64 {
    // tamanos para que la foto pese poco en firestore
    private const val LADO_MAXIMO = 640
    private const val BYTES_OBJETIVO = 120_000
    private const val BYTES_LIMITE = 400_000

    // guarda los bitmaps ya decodificados para no repetir el trabajo
    private val cache = object : LruCache<Int, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(clave: Int, bitmap: Bitmap) = bitmap.byteCount / 1024
    }

    // foto de la galeria a base64, la achica, la endereza y la comprime
    fun desdeUri(contexto: Context, uri: Uri): String? {
        return try {
            val resolver = contexto.contentResolver

            // primero solo lee las medidas, asi no carga la foto completa
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, medidas) }
            var reduccion = 1
            while (medidas.outWidth / (reduccion * 2) >= LADO_MAXIMO &&
                medidas.outHeight / (reduccion * 2) >= LADO_MAXIMO
            ) {
                reduccion *= 2
            }

            val opciones = BitmapFactory.Options().apply { inSampleSize = reduccion }
            var bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opciones)
            } ?: return null

            // algunas fotos vienen giradas, se corrige con los datos exif
            val orientacion = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
            val grados = when (orientacion) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (grados != 0f) {
                val matriz = Matrix().apply { postRotate(grados) }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
            }

            val mayor = maxOf(bitmap.width, bitmap.height)
            if (mayor > LADO_MAXIMO) {
                val escala = LADO_MAXIMO.toFloat() / mayor
                bitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * escala).toInt().coerceAtLeast(1),
                    (bitmap.height * escala).toInt().coerceAtLeast(1),
                    true
                )
            }

            // baja la calidad hasta que pese lo esperado, si sigue muy grande se rechaza
            var calidad = 80
            var bytes = comprimir(bitmap, calidad)
            while (bytes.size > BYTES_OBJETIVO && calidad > 40) {
                calidad -= 10
                bytes = comprimir(bitmap, calidad)
            }
            if (bytes.size > BYTES_LIMITE) return null

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    // base64 a bitmap, usa cache
    fun aBitmap(base64: String?): Bitmap? {
        if (base64.isNullOrEmpty()) return null
        val clave = base64.hashCode()
        cache.get(clave)?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { cache.put(clave, it) }
        } catch (e: Exception) {
            null
        }
    }

    // bitmap a jpeg
    private fun comprimir(bitmap: Bitmap, calidad: Int): ByteArray {
        val salida = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, calidad, salida)
        return salida.toByteArray()
    }
}

// pone la foto del producto en un ImageView
fun ImageView.mostrarFotoProducto(producto: ProductoInventario, relleno: Int = 12) {
    mostrarFotoDesde(producto.imagenBase64, producto.imagen, relleno)
}

// orden de prioridad, foto base64, imagen de la app o icono por defecto
fun ImageView.mostrarFotoDesde(base64: String?, recurso: Int?, relleno: Int = 12) {
    val bitmap = ImagenBase64.aBitmap(base64)
    when {
        bitmap != null -> {
            setPadding(0, 0, 0, 0)
            imageTintList = null
            setImageBitmap(bitmap)
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