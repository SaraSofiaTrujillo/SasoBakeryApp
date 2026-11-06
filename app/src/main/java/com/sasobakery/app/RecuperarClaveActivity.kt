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

class RecuperarClaveActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CORREO = "correo"
    }

    private enum class Paso { CORREO, CODIGO, NUEVA_CLAVE, LISTO }

    private var paso = Paso.CORREO
    private var correo = ""
    private var temporizador: CountDownTimer? = null

    private lateinit var btnAtras: View
    private lateinit var pasos: Map<Paso, View>
    private lateinit var etCorreo: EditText
    private lateinit var etCodigo: EditText
    private lateinit var etClaveNueva: EditText
    private lateinit var etClaveConfirmar: EditText
    private lateinit var tvCorreoEnviado: TextView
    private lateinit var tvReenviar: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_recuperar_clave)

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
            Paso.CODIGO to findViewById<View>(R.id.paso2),
            Paso.NUEVA_CLAVE to findViewById<View>(R.id.paso3),
            Paso.LISTO to findViewById<View>(R.id.paso4)
        )
        etCorreo = findViewById(R.id.etCorreo)
        etCodigo = findViewById(R.id.etCodigo)
        etClaveNueva = findViewById(R.id.etClaveNueva)
        etClaveConfirmar = findViewById(R.id.etClaveConfirmar)
        tvCorreoEnviado = findViewById(R.id.tvCorreoEnviado)
        tvReenviar = findViewById(R.id.tvReenviar)

        etCorreo.setText(intent.getStringExtra(EXTRA_CORREO).orEmpty())

        findViewById<View>(R.id.btnEnviarCodigo).setOnClickListener { enviarCodigo() }
        etCorreo.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) { enviarCodigo(); true } else false
        }
        findViewById<View>(R.id.btnVolverLogin).setOnClickListener { finish() }

        findViewById<View>(R.id.btnVerificarCodigo).setOnClickListener { verificarCodigo() }
        etCodigo.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) { verificarCodigo(); true } else false
        }
        tvReenviar.setOnClickListener { reenviarCodigo() }
        findViewById<View>(R.id.btnCambiarCorreo).setOnClickListener { mostrarPaso(Paso.CORREO) }

        val btnMostrarNueva = findViewById<TextView>(R.id.btnMostrarNueva)
        val btnMostrarConfirmar = findViewById<TextView>(R.id.btnMostrarConfirmar)
        btnMostrarNueva.setOnClickListener { etClaveNueva.alternarVisibilidadClave(btnMostrarNueva) }
        btnMostrarConfirmar.setOnClickListener {
            etClaveConfirmar.alternarVisibilidadClave(btnMostrarConfirmar)
        }
        findViewById<View>(R.id.btnGuardarClave).setOnClickListener { guardarClave() }
        etClaveConfirmar.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) { guardarClave(); true } else false
        }

        findViewById<View>(R.id.btnIrLogin).setOnClickListener { finish() }

        btnAtras.setOnClickListener { atras() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = atras()
        })

        mostrarPaso(Paso.CORREO)
    }


    private fun mostrarPaso(nuevo: Paso) {
        paso = nuevo
        pasos.forEach { (clave, vista) ->
            vista.visibility = if (clave == nuevo) View.VISIBLE else View.GONE
        }
        btnAtras.visibility = if (nuevo == Paso.LISTO) View.INVISIBLE else View.VISIBLE
        if (nuevo != Paso.CODIGO) temporizador?.cancel()
        ocultarTeclado()
    }

    private fun atras() {
        if (paso == Paso.CODIGO) mostrarPaso(Paso.CORREO) else finish()
    }

    private fun enviarCodigo() {
        val escrito = etCorreo.text.toString().trim().lowercase()
        if (escrito.isEmpty()) {
            etCorreo.error = "Escribe tu correo"
            etCorreo.requestFocus()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(escrito).matches()) {
            etCorreo.error = "Escribe un correo válido"
            etCorreo.requestFocus()
            return
        }

        correo = escrito
        RecuperacionClaveRepositorio.solicitarCodigo(correo)

        // Mensaje neutro: no dice si el correo existe o no (así se protege a los usuarios)
        tvCorreoEnviado.text =
            "Si ${enmascarar(correo)} tiene una cuenta, le enviamos un código de " +
                    "${RecuperacionClaveRepositorio.LARGO_CODIGO} dígitos."
        etCodigo.text.clear()
        etCodigo.error = null
        mostrarPaso(Paso.CODIGO)
        iniciarConteoReenvio()
    }

    private fun verificarCodigo() {
        val codigo = etCodigo.text.toString().trim()
        if (codigo.length != RecuperacionClaveRepositorio.LARGO_CODIGO) {
            etCodigo.error = "Escribe los ${RecuperacionClaveRepositorio.LARGO_CODIGO} dígitos"
            etCodigo.requestFocus()
            return
        }

        when (RecuperacionClaveRepositorio.verificarCodigo(correo, codigo)) {
            ResultadoCodigo.CORRECTO -> {
                etClaveNueva.text.clear()
                etClaveConfirmar.text.clear()
                mostrarPaso(Paso.NUEVA_CLAVE)
            }
            ResultadoCodigo.INCORRECTO -> {
                etCodigo.error = "Código incorrecto"
                etCodigo.requestFocus()
            }
            ResultadoCodigo.EXPIRADO -> {
                etCodigo.error = "El código venció. Pide uno nuevo."
                etCodigo.requestFocus()
            }
        }
    }

    private fun reenviarCodigo() {
        if (!tvReenviar.isEnabled) return
        RecuperacionClaveRepositorio.solicitarCodigo(correo)
        etCodigo.text.clear()
        etCodigo.error = null
        mensaje("Te enviamos un nuevo código")
        iniciarConteoReenvio()
    }

    private fun iniciarConteoReenvio() {
        temporizador?.cancel()
        tvReenviar.isEnabled = false
        tvReenviar.setTextColor(Color.parseColor("#8A8494"))
        temporizador = object : CountDownTimer(
            RecuperacionClaveRepositorio.SEGUNDOS_PARA_REENVIAR * 1000L, 1000L
        ) {
            override fun onTick(milisRestantes: Long) {
                tvReenviar.text = "Reenviar código en ${(milisRestantes + 999) / 1000} s"
            }

            override fun onFinish() {
                tvReenviar.isEnabled = true
                tvReenviar.text = "¿No llegó? Reenviar código"
                tvReenviar.setTextColor(Color.parseColor("#7B5EA7"))
            }
        }.start()
    }

    private fun guardarClave() {
        val nueva = etClaveNueva.text.toString()
        val confirmar = etClaveConfirmar.text.toString()

        if (nueva.length < CuentasRepositorio.LARGO_MINIMO_CLAVE) {
            etClaveNueva.error = "Mínimo ${CuentasRepositorio.LARGO_MINIMO_CLAVE} caracteres"
            etClaveNueva.requestFocus()
            return
        }
        if (nueva != confirmar) {
            etClaveConfirmar.error = "Las contraseñas no coinciden"
            etClaveConfirmar.requestFocus()
            return
        }

        if (RecuperacionClaveRepositorio.cambiarClave(correo, nueva)) {
            mostrarPaso(Paso.LISTO)
        } else {
            mensaje("La recuperación venció. Empieza de nuevo.")
            mostrarPaso(Paso.CORREO)
        }
    }

    private fun enmascarar(correo: String): String {
        val arroba = correo.indexOf('@')
        if (arroba <= 0) return correo
        return correo.take(minOf(2, arroba)) + "***" + correo.substring(arroba)
    }

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
