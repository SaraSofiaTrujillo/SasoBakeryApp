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

/**
 * Recuperación de contraseña en 2 pasos dentro de la misma pantalla:
 * 1) el usuario escribe su correo  2) "Revisa tu correo".
 * Firebase envía un enlace al correo; ahí el usuario crea su nueva contraseña
 * en una página segura y vuelve a iniciar sesión.
 */
class RecuperarClaveActivity : AppCompatActivity() {

    companion object {
        // El login envía el correo que ya escribió el usuario
        const val EXTRA_CORREO = "correo"
        private const val SEGUNDOS_PARA_REENVIAR = 30
    }

    private enum class Paso { CORREO, ENVIADO }

    private var paso = Paso.CORREO
    private var correo = "" // correo al que se envió el enlace
    private var enCurso = false // evita pedir dos envíos a la vez
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

        // Misma lógica de márgenes del login: lila hasta arriba, panel blanco hasta
        // abajo, y el teclado empuja el contenido para no tapar los campos.
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

        // El paso 2 ya no pide código: se ocultan esos elementos
        findViewById<View>(R.id.tvEtiquetaCodigo).visibility = View.GONE
        findViewById<View>(R.id.etCodigo).visibility = View.GONE
        findViewById<View>(R.id.tvNotaPrueba).visibility = View.GONE

        // El botón grande del paso 2 pasa a ser "Volver a iniciar sesión"
        val btnVolverDesdeEnviado = findViewById<TextView>(R.id.btnVerificarCodigo)
        btnVolverDesdeEnviado.text = "Volver a iniciar sesión"
        btnVolverDesdeEnviado.setOnClickListener { finish() }

        // Si el usuario ya había escrito su correo en el login, se reutiliza
        etCorreo.setText(intent.getStringExtra(EXTRA_CORREO).orEmpty())

        // ----- Paso 1: correo -----
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

        // ----- Paso 2: revisa tu correo -----
        tvReenviar.setOnClickListener {
            if (tvReenviar.isEnabled) enviarEnlace(esReenvio = true)
        }
        findViewById<View>(R.id.btnCambiarCorreo).setOnClickListener { mostrarPaso(Paso.CORREO) }

        // Volver (flecha de la pantalla y botón Atrás del celular)
        btnAtras.setOnClickListener { atras() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = atras()
        })

        mostrarPaso(Paso.CORREO)
    }

    // ---------- Pasos ----------

    private fun mostrarPaso(nuevo: Paso) {
        paso = nuevo
        pasos.forEach { (clave, vista) ->
            vista.visibility = if (clave == nuevo) View.VISIBLE else View.GONE
        }
        if (nuevo != Paso.ENVIADO) temporizador?.cancel()
        ocultarTeclado()
    }

    // Del paso 2 se vuelve al correo; del paso 1 se sale a la pantalla anterior
    private fun atras() {
        if (paso == Paso.ENVIADO) mostrarPaso(Paso.CORREO) else finish()
    }

    // ---------- Enviar el enlace ----------

    /**
     * Pide a Firebase que envíe el enlace de recuperación. Si es un reenvío se usa el
     * correo ya validado; si no, se valida primero lo que escribió el usuario.
     */
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
            // Si la pantalla ya se cerró mientras esperaba, no se hace nada
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
                // Mensaje neutro: no dice si el correo existe o no (así se protege a los usuarios)
                tvCorreoEnviado.text =
                    "Si ${enmascararCorreo(correo)} tiene una cuenta, le enviamos un enlace para " +
                            "crear una nueva contraseña. Revisa también la carpeta de spam."
                mostrarPaso(Paso.ENVIADO)
            }
            iniciarConteoReenvio()
        }
    }

    // Mientras Firebase responde, el botón cambia de texto y no se puede pulsar
    private fun cambiarEstadoCarga(cargando: Boolean) {
        enCurso = cargando
        btnEnviar.isEnabled = !cargando
        btnEnviar.text = if (cargando) "Enviando…" else "Enviar enlace"
    }

    // "Reenviar" se activa después de unos segundos para evitar envíos seguidos
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

    // ---------- Utilidades ----------

    private fun ocultarTeclado() {
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.ime())
    }

    private fun mensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        temporizador?.cancel()
        super.onDestroy()
    }
}