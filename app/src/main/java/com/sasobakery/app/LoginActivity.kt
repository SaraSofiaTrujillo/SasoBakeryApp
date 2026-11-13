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

/**
 * Pantalla de inicio de sesión. Valida los campos, inicia sesión con Firebase
 * (AuthRepositorio) y abre la pantalla principal que corresponde al rol de la cuenta.
 * También permite entrar con huella si el usuario la activó en este celular.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var etCorreo: EditText
    private lateinit var etClave: EditText
    private lateinit var btnMostrar: TextView
    private lateinit var btnIniciar: TextView
    private var enCurso = false // evita enviar el formulario dos veces seguidas

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        // La zona lila llega hasta arriba y el panel blanco hasta abajo;
        // el teclado empuja el contenido para no tapar los campos.
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

        // Botón "Listo" del teclado
        etClave.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) {
                iniciarSesion()
                true
            } else {
                false
            }
        }

        btnIniciar.setOnClickListener { iniciarSesion() }

        // Acceso con huella
        findViewById<View>(R.id.btnBiometria).setOnClickListener { entrarConBiometria() }
        findViewById<View>(R.id.btnQuitarBiometria).setOnClickListener { quitarBiometria() }

        findViewById<View>(R.id.btnOlvide).setOnClickListener {
            val intent = Intent(this, RecuperarClaveActivity::class.java)
            intent.putExtra(RecuperarClaveActivity.EXTRA_CORREO, etCorreo.text.toString().trim())
            startActivity(intent)
        }
        findViewById<View>(R.id.btnGoogle).setOnClickListener { mensaje("Próximamente") }
        findViewById<View>(R.id.btnRegistro).setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }
    }

    // ---------- Contraseña ----------

    private fun alternarClave() {
        val estabaOculta = etClave.transformationMethod is PasswordTransformationMethod
        etClave.transformationMethod =
            if (estabaOculta) HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
        etClave.setSelection(etClave.text.length)
        btnMostrar.text = if (estabaOculta) "Ocultar" else "Mostrar"
    }

    // ---------- Iniciar sesión ----------

    /** Valida los campos y, si están bien, pide a Firebase iniciar sesión. */
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
            // Si la pantalla ya se cerró mientras esperaba, no se hace nada
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

    // Mientras Firebase responde, el botón cambia de texto y no se puede pulsar
    private fun cambiarEstadoCarga(cargando: Boolean) {
        enCurso = cargando
        btnIniciar.isEnabled = !cargando
        btnIniciar.text = if (cargando) "Ingresando…" else "Iniciar sesión"
    }

    // ---------- Acceso con huella ----------

    // Cada vez que se ve el login se revisa si debe aparecer el botón de huella
    override fun onResume() {
        super.onResume()
        actualizarBotonBiometria()
    }

    // El botón solo aparece si la huella está activada y el celular todavía la permite
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

    /**
     * Después de iniciar sesión con contraseña: ofrece activar la huella (si el celular
     * puede y esta cuenta aún no la tiene) y entra a la app. La contraseña se guarda
     * cifrada y solo se puede abrir con la huella.
     */
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

    /**
     * Entra con la huella: Android devuelve el correo y la contraseña guardados y con
     * ellos se inicia sesión en Firebase. Si la contraseña cambió o la cuenta ya no
     * existe, la huella se desactiva y se pide la contraseña.
     */
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

    // Se cierra el login para que Atrás no regrese a él desde dentro de la app
    private fun abrirInicio(destino: Class<*>) {
        startActivity(Intent(this, destino))
        finish()
    }

    private fun mensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }
}