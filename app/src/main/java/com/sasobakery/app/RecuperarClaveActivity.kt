package com.sasobakery.app

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

// recuperar la clave, firebase envia un enlace al correo
class RecuperarClaveActivity : AppCompatActivity() {
    companion object {
        // correo que viene escrito desde el login
        const val EXTRA_CORREO = "correo"
        // espera antes de poder pedir otro enlace
        private const val SEGUNDOS_PARA_REENVIAR = 30
    }

    // dos pasos, escribir el correo y aviso de enviado
    private enum class Paso { CORREO, ENVIADO }

    private var paso = Paso.CORREO
    private var correo = ""
    private var enCurso = false
    // cuenta regresiva del reenvio
    private var temporizador: CountDownTimer? = null

    private lateinit var btnAtras: View
    private lateinit var pasos: Map<Paso, View>
    private lateinit var etCorreo: EditText
    private lateinit var btnEnviar: TextView
    private lateinit var tvCorreoEnviado: TextView
    private lateinit var tvReenviar: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_recuperar_clave)

        // ajusta los margenes para barras del sistema y teclado
        val cabecera = findViewById<View>(R.id.headerRecuperar)
        val panel = findViewById<View>(R.id.panelRecuperar)
        val baseArriba = cabecera.paddingTop
        val baseAbajo = panel.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            cabecera.updatePadding(top = baseArriba + barras.top)
            panel.updatePadding(bottom = baseAbajo + barras.bottom)
            insets
        }

        btnAtras = findViewById(R.id.btnAtras)
        pasos = mapOf(
            Paso.CORREO to findViewById<View>(R.id.paso1),
            Paso.ENVIADO to findViewById<View>(R.id.paso2)
        )
        etCorreo = findViewById(R.id.etCorreo)
        btnEnviar = findViewById(R.id.btnEnviarCodigo)
        tvCorreoEnviado = findViewById(R.id.tvCorreoEnviado)
        tvReenviar = findViewById(R.id.tvReenviar)

        // el layout trae campos de codigo que ya no se usan, se ocultan
        findViewById<View>(R.id.tvEtiquetaCodigo).visibility = View.GONE
        findViewById<View>(R.id.etCodigo).visibility = View.GONE
        findViewById<View>(R.id.tvNotaPrueba).visibility = View.GONE

        val btnVolverDesdeEnviado = findViewById<TextView>(R.id.btnVerificarCodigo)
        btnVolverDesdeEnviado.text = "Volver a iniciar sesión"
        btnVolverDesdeEnviado.setOnClickListener { finish() }

        // si viene un correo del login se deja escrito
        etCorreo.setText(intent.getStringExtra(EXTRA_CORREO).orEmpty())

        btnEnviar.setOnClickListener { enviarEnlace(esReenvio = false) }
        etCorreo.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) {
                enviarEnlace(esReenvio = false)
                true
            } else {
                false
            }
        }
        findViewById<View>(R.id.btnVolverLogin).setOnClickListener { finish() }

        tvReenviar.setOnClickListener {
            if (tvReenviar.isEnabled) enviarEnlace(esReenvio = true)
        }
        findViewById<View>(R.id.btnCambiarCorreo).setOnClickListener { mostrarPaso(Paso.CORREO) }

        btnAtras.setOnClickListener { atras() }
        // atras en el segundo paso vuelve al primero
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = atras()
        })

        mostrarPaso(Paso.CORREO)
    }

    // muestra solo el paso actual
    private fun mostrarPaso(nuevo: Paso) {
        paso = nuevo
        pasos.forEach { (clave, vista) ->
            vista.visibility = if (clave == nuevo) View.VISIBLE else View.GONE
        }
        if (nuevo != Paso.ENVIADO) temporizador?.cancel()
        ocultarTeclado()
    }

    // del paso enviado vuelve al correo, del primero cierra
    private fun atras() {
        if (paso == Paso.ENVIADO) mostrarPaso(Paso.CORREO) else finish()
    }

    // valida el correo y pide el envio, el reenvio usa el correo ya guardado
    private fun enviarEnlace(esReenvio: Boolean) {
        if (enCurso) return

        val destino = if (esReenvio) correo else etCorreo.text.toString().trim().lowercase()
        if (!esReenvio) {
            if (destino.isEmpty()) {
                etCorreo.error = "Escribe tu correo"
                etCorreo.requestFocus()
                return
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(destino).matches()) {
                etCorreo.error = "Escribe un correo válido"
                etCorreo.requestFocus()
                return
            }
        }

        cambiarEstadoCarga(true)
        AuthRepositorio.enviarCorreoRecuperacion(destino) { error ->
            if (isFinishing || isDestroyed) return@enviarCorreoRecuperacion
            cambiarEstadoCarga(false)

            if (error != null) {
                mensaje(error)
                return@enviarCorreoRecuperacion
            }

            correo = destino
            if (esReenvio) {
                mensaje("Te enviamos un nuevo enlace")
            } else {
                // el mensaje no confirma si la cuenta existe
                tvCorreoEnviado.text =
                    "Si ${enmascararCorreo(correo)} tiene una cuenta, le enviamos un enlace para " +
                            "crear una nueva contraseña. Revisa también la carpeta de spam."
                mostrarPaso(Paso.ENVIADO)
            }
            iniciarConteoReenvio()
        }
    }

    // bloquea el boton mientras envia
    private fun cambiarEstadoCarga(cargando: Boolean) {
        enCurso = cargando
        btnEnviar.isEnabled = !cargando
        btnEnviar.text = if (cargando) "Enviando…" else "Enviar enlace"
    }

    // desactiva el reenvio hasta que termine la cuenta regresiva
    private fun iniciarConteoReenvio() {
        temporizador?.cancel()
        tvReenviar.isEnabled = false
        tvReenviar.setTextColor(Color.parseColor("#8A8494"))
        temporizador = object : CountDownTimer(SEGUNDOS_PARA_REENVIAR * 1000L, 1000L) {
            override fun onTick(milisRestantes: Long) {
                tvReenviar.text = "Reenviar enlace en ${(milisRestantes + 999) / 1000} s"
            }

            override fun onFinish() {
                tvReenviar.isEnabled = true
                tvReenviar.text = "¿No llegó? Reenviar enlace"
                tvReenviar.setTextColor(Color.parseColor("#7B5EA7"))
            }
        }.start()
    }

    // esconde el teclado al cambiar de paso
    private fun ocultarTeclado() {
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.ime())
    }

    private fun mensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }

    // cancela el temporizador
    override fun onDestroy() {
        temporizador?.cancel()
        super.onDestroy()
    }
}