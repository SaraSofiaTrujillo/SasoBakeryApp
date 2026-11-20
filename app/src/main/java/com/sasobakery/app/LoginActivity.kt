package com.sasobakery.app

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// inicio de sesion con correo y clave o con huella
class LoginActivity : AppCompatActivity() {
    private lateinit var etCorreo: EditText
    private lateinit var etClave: EditText
    private lateinit var btnMostrar: TextView
    private lateinit var btnIniciar: TextView
    // evita enviar dos veces mientras se inicia sesion
    private var enCurso = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        // ajusta los margenes para barras del sistema y teclado
        val cabecera = findViewById<View>(R.id.headerLogin)
        val panel = findViewById<View>(R.id.panelLogin)
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

        etCorreo = findViewById(R.id.etCorreo)
        etClave = findViewById(R.id.etClave)
        btnMostrar = findViewById(R.id.btnMostrar)
        btnIniciar = findViewById(R.id.btnIniciar)

        btnMostrar.setOnClickListener { alternarClave() }

        // el boton listo del teclado inicia sesion
        etClave.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) {
                iniciarSesion()
                true
            } else {
                false
            }
        }

        btnIniciar.setOnClickListener { iniciarSesion() }

        findViewById<View>(R.id.btnBiometria).setOnClickListener { entrarConBiometria() }
        findViewById<View>(R.id.btnQuitarBiometria).setOnClickListener { quitarBiometria() }

        // pasa el correo escrito a recuperar clave
        findViewById<View>(R.id.btnOlvide).setOnClickListener {
            val intent = Intent(this, RecuperarClaveActivity::class.java)
            intent.putExtra(RecuperarClaveActivity.EXTRA_CORREO, etCorreo.text.toString().trim())
            startActivity(intent)
        }
        // todavia no esta hecho
        findViewById<View>(R.id.btnGoogle).setOnClickListener { mensaje("Próximamente") }
        findViewById<View>(R.id.btnRegistro).setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }
    }

    // muestra u oculta la clave
    private fun alternarClave() {
        val estabaOculta = etClave.transformationMethod is PasswordTransformationMethod
        etClave.transformationMethod =
            if (estabaOculta) HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
        etClave.setSelection(etClave.text.length)
        btnMostrar.text = if (estabaOculta) "Ocultar" else "Mostrar"
    }

    // valida los campos y entra, el rol decide a que pantalla va
    private fun iniciarSesion() {
        if (enCurso) return

        val correo = etCorreo.text.toString().trim().lowercase()
        val clave = etClave.text.toString()

        if (correo.isEmpty()) {
            etCorreo.error = "Escribe tu correo"
            etCorreo.requestFocus()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreo.error = "Escribe un correo válido"
            etCorreo.requestFocus()
            return
        }
        if (clave.length < AuthRepositorio.LARGO_MINIMO_CLAVE) {
            etClave.error = "Mínimo ${AuthRepositorio.LARGO_MINIMO_CLAVE} caracteres"
            etClave.requestFocus()
            return
        }

        cambiarEstadoCarga(true)
        AuthRepositorio.iniciarSesion(correo, clave) { resultado ->
            if (isFinishing || isDestroyed) return@iniciarSesion
            cambiarEstadoCarga(false)

            when (resultado) {
                is ResultadoAuth.Exito ->
                    terminarInicioDeSesion(correo, clave, AuthRepositorio.destinoPara(resultado.rol))
                is ResultadoAuth.Error ->
                    Toast.makeText(this, resultado.mensaje, Toast.LENGTH_LONG).show()
            }
        }
    }

    // bloquea el boton mientras entra
    private fun cambiarEstadoCarga(cargando: Boolean) {
        enCurso = cargando
        btnIniciar.isEnabled = !cargando
        btnIniciar.text = if (cargando) "Ingresando…" else "Iniciar sesión"
    }

    // al volver revisa si se puede mostrar la huella
    override fun onResume() {
        super.onResume()
        actualizarBotonBiometria()
    }

    // el boton de huella solo sale si esta activada y el celular puede usarla
    private fun actualizarBotonBiometria() {
        val bloque = findViewById<View>(R.id.bloqueBiometria)
        val correoActivado = BiometriaRepositorio.correoActivado(this)
        val disponible = BiometriaHelper.estado(this) == EstadoBiometria.DISPONIBLE

        if (correoActivado != null && disponible) {
            findViewById<TextView>(R.id.tvBiometriaCuenta).text =
                "Cuenta: ${enmascararCorreo(correoActivado)}"
            bloque.visibility = View.VISIBLE
        } else {
            bloque.visibility = View.GONE
        }
    }

    // si puede, ofrece activar la huella antes de entrar
    private fun terminarInicioDeSesion(correo: String, clave: String, destino: Class<*>) {
        val puedeOfrecer = BiometriaHelper.estado(this) == EstadoBiometria.DISPONIBLE &&
                !BiometriaRepositorio.estaActivada(this, correo)
        if (!puedeOfrecer) {
            abrirInicio(destino)
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("¿Entrar con tu huella?")
            .setMessage("La próxima vez podrás entrar a Saso Bakery tocando el sensor, sin escribir tu contraseña.")
            .setCancelable(false)
            .setNegativeButton("Ahora no") { _, _ -> abrirInicio(destino) }
            .setPositiveButton("Activar") { _, _ ->
                BiometriaHelper.activar(this, correo, clave) { activada ->
                    if (activada) mensaje("Acceso con huella activado")
                    abrirInicio(destino)
                }
            }
            .show()
    }

    // la huella abre la clave guardada y con ella se inicia sesion normal
    private fun entrarConBiometria() {
        if (enCurso) return

        BiometriaHelper.entrar(
            this,
            alEntrar = { correo, clave ->
                cambiarEstadoCarga(true)
                AuthRepositorio.iniciarSesion(correo, clave) { resultado ->
                    if (isFinishing || isDestroyed) return@iniciarSesion
                    cambiarEstadoCarga(false)

                    when (resultado) {
                        is ResultadoAuth.Exito ->
                            abrirInicio(AuthRepositorio.destinoPara(resultado.rol))
                        is ResultadoAuth.Error -> {
                            // si la clave ya no sirve se quita la huella guardada
                            if (resultado.credencialesInvalidas) {
                                BiometriaRepositorio.desactivar(this)
                                actualizarBotonBiometria()
                                mensaje("Tu contraseña cambió o la cuenta ya no existe. Inicia sesión con tu contraseña.")
                            } else {
                                mensaje(resultado.mensaje)
                            }
                        }
                    }
                }
            },
            alFallar = { actualizarBotonBiometria() }
        )
    }

    // olvida la huella guardada en este celular
    private fun quitarBiometria() {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Dejar de usar la huella?")
            .setMessage("En este celular tendrás que iniciar sesión con tu contraseña.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Dejar de usar") { _, _ ->
                BiometriaRepositorio.desactivar(this)
                actualizarBotonBiometria()
                mensaje("Acceso con huella desactivado")
            }
            .show()
    }

    // abre la pantalla del rol y cierra el login
    private fun abrirInicio(destino: Class<*>) {
        startActivity(Intent(this, destino))
        finish()
    }

    private fun mensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }
}