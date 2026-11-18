package com.sasobakery.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Pantalla "Pago seguro": muestra el checkout de PayU dentro de la app. La tarjeta se
 * escribe en la página de PayU, nunca en la app. Cuando PayU devuelve al comprador a la
 * dirección de respuesta, esta pantalla la intercepta, interpreta el resultado y se cierra
 * devolviéndolo a quien la abrió.
 *
 * Resultado: RESULT_OK con [EXTRA_RESPUESTA], o RESULT_CANCELED si el comprador salió.
 */
class PayuCheckoutActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REFERENCIA = "payu_referencia"
        const val EXTRA_MONTO = "payu_monto"
        const val EXTRA_DESCRIPCION = "payu_descripcion"
        const val EXTRA_CORREO = "payu_correo"
        const val EXTRA_RESPUESTA = "payu_respuesta"
    }

    private lateinit var web: WebView
    private lateinit var progreso: View
    private var referencia = ""
    private var monto = 0
    private var terminado = false // evita entregar la respuesta dos veces

    @SuppressLint("SetJavaScriptEnabled") // el checkout de PayU necesita JavaScript
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_payu_checkout)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        referencia = intent.getStringExtra(EXTRA_REFERENCIA).orEmpty()
        monto = intent.getIntExtra(EXTRA_MONTO, 0)
        val descripcion = intent.getStringExtra(EXTRA_DESCRIPCION).orEmpty()
        val correo = intent.getStringExtra(EXTRA_CORREO).orEmpty()
        if (referencia.isEmpty() || monto <= 0) {
            finish()
            return
        }

        web = findViewById(R.id.webCheckout)
        progreso = findViewById(R.id.progresoCheckout)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true

        web.webViewClient = object : WebViewClient() {
            // Si PayU manda al comprador a nuestra dirección de respuesta, no se carga:
            // se lee el resultado y se cierra esta pantalla.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.toString().startsWith(PayuConfig.URL_RESPUESTA)) {
                    entregarRespuesta(request)
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                progreso.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progreso.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    progreso.visibility = View.GONE
                    Toast.makeText(
                        this@PayuCheckoutActivity,
                        "No se pudo cargar PayU. Revisa tu conexión.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        findViewById<View>(R.id.btnAtras).setOnClickListener { confirmarSalir() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmarSalir()
        })

        // El formulario viaja por POST hacia el checkout de pruebas de PayU
        web.postUrl(
            PayuConfig.URL_CHECKOUT,
            PayuConfig.cuerpoSolicitud(referencia, monto, descripcion, correo)
        )
    }

    /** Interpreta lo que devolvió PayU y cierra la pantalla enviándolo a quien la abrió. */
    private fun entregarRespuesta(request: WebResourceRequest) {
        if (terminado) return
        terminado = true
        val respuesta = PayuConfig.interpretarRespuesta(request.url, referencia, monto)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESPUESTA, respuesta))
        finish()
    }

    private fun confirmarSalir() {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Cancelar el pago?")
            .setMessage("No se hará ningún cobro y tu carrito seguirá igual.")
            .setNegativeButton("Seguir pagando", null)
            .setPositiveButton("Cancelar pago") { _, _ ->
                terminado = true
                setResult(RESULT_CANCELED)
                finish()
            }
            .show()
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}