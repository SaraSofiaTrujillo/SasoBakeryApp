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

// pantalla con el webview de payu, devuelve la respuesta a quien la abrio
class PayuCheckoutActivity : AppCompatActivity() {
    companion object {
        // datos que llegan en el intent y la respuesta que sale
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
    // evita entregar la respuesta dos veces
    private var terminado = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_payu_checkout)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        // sin referencia o monto no se puede pagar, se cierra
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
            // cuando payu redirige a nuestra url de respuesta se corta la carga y se lee el resultado
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

        // payu pide los datos por post, no por url
        web.postUrl(
            PayuConfig.URL_CHECKOUT,
            PayuConfig.cuerpoSolicitud(referencia, monto, descripcion, correo)
        )
    }

    // valida la respuesta con PayuConfig y la devuelve a la pantalla de pago
    private fun entregarRespuesta(request: WebResourceRequest) {
        if (terminado) return
        terminado = true
        val respuesta = PayuConfig.interpretarRespuesta(request.url, referencia, monto)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESPUESTA, respuesta))
        finish()
    }

    // antes de salir pregunta, cancelar no cobra nada
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

    // libera el webview
    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}