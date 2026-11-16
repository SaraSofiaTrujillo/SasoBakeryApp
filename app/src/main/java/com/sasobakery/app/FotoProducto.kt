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

/**
 * Convierte fotos en texto Base64 (para guardarlas en Firestore) y de texto a imagen.
 * Es el único lugar que sabe cómo se guarda la foto: si algún día se usa Firebase
 * Storage, solo habría que cambiar esta clase.
 */
object ImagenBase64 {

    private const val LADO_MAXIMO = 640        // píxeles del lado más largo
    private const val BYTES_OBJETIVO = 120_000 // tamaño al que se intenta llegar
    private const val BYTES_LIMITE = 400_000   // más que esto no se acepta (Firestore: 1 MB por documento)

    // Guarda las imágenes ya decodificadas para no repetir el trabajo al desplazar la lista
    private val cache = object : LruCache<Int, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(clave: Int, bitmap: Bitmap) = bitmap.byteCount / 1024
    }

    /**
     * Lee la foto elegida de la galería, la endereza, la reduce y la comprime.
     * Devuelve el texto Base64, o null si no se pudo. Conviene llamarla fuera del hilo principal.
     */
    fun desdeUri(contexto: Context, uri: Uri): String? {
        return try {
            val resolver = contexto.contentResolver

            // 1) Mide la foto sin cargarla, para decidir cuánto reducirla al abrirla
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, medidas) }
            var reduccion = 1
            while (medidas.outWidth / (reduccion * 2) >= LADO_MAXIMO &&
                medidas.outHeight / (reduccion * 2) >= LADO_MAXIMO
            ) {
                reduccion *= 2
            }

            // 2) Abre la foto ya reducida
            val opciones = BitmapFactory.Options().apply { inSampleSize = reduccion }
            var bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opciones)
            } ?: return null

            // 3) Las fotos de cámara pueden venir giradas: se enderezan según sus datos EXIF
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

            // 4) Ajusta el lado más largo a LADO_MAXIMO
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

            // 5) Comprime a JPEG bajando la calidad hasta que pese poco
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

    /** Convierte el texto Base64 en imagen (null si no hay foto o el texto no es válido). */
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

    private fun comprimir(bitmap: Bitmap, calidad: Int): ByteArray {
        val salida = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, calidad, salida)
        return salida.toByteArray()
    }
}

/**
 * Muestra la foto de un producto en este ImageView:
 * 1) la foto guardada en Firestore, 2) la foto del proyecto,
 * 3) si no hay ninguna, el ícono de caja lila (marcador).
 * "relleno" es el espacio (en dp) alrededor del ícono de caja.
 */
fun ImageView.mostrarFotoProducto(producto: ProductoInventario, relleno: Int = 12) {
    mostrarFotoDesde(producto.imagenBase64, producto.imagen, relleno)
}

/** Foto en Base64 y el recurso por separado. */
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