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

// Pantalla de inicio de sesión (acceso principal de la app).
// Por ahora es SIMULADA: no hay autenticación real. Según el correo de prueba
// abre la pantalla principal de cada rol. La pantalla de selección de rol
// (RolSeleccionActivity) sigue disponible con el enlace "Entrar eligiendo un rol".
class LoginActivity : AppCompatActivity() {

    // ===== CUENTAS DE PRUEBA (correo -> pantalla principal del rol) =====
    private val cuentasDePrueba get() = CuentasRepositorio.cuentasDePrueba
    private val largoMinimoClave = CuentasRepositorio.LARGO_MINIMO_CLAVE
    // ====================================================================

    private lateinit var etCorreo: EditText
    private lateinit var etClave: EditText
    private lateinit var btnMostrar: TextView

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

        // Correo precargado como en el mockup (borra esta línea si no lo quieres)
        etCorreo.setText("valentina@email.com")

        // Mostrar / Ocultar contraseña
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

        findViewById<View>(R.id.btnIniciar).setOnClickListener { iniciarSesion() }

        // Acceso con huella
        findViewById<View>(R.id.btnBiometria).setOnClickListener { entrarConBiometria() }
        findViewById<View>(R.id.btnQuitarBiometria).setOnClickListener { quitarBiometria() }

        // Todavía sin pantalla
        findViewById<View>(R.id.btnOlvide).setOnClickListener {
            val intent = Intent(this, RecuperarClaveActivity::class.java)
            intent.putExtra(RecuperarClaveActivity.EXTRA_CORREO, etCorreo.text.toString().trim())
            startActivity(intent)
        }
        findViewById<View>(R.id.btnGoogle).setOnClickListener { mensaje("Próximamente") }
        findViewById<View>(R.id.btnRegistro).setOnClickListener { mensaje("Próximamente") }

        // Alternativa para pruebas: la pantalla de selección de rol de siempre.
        // Sin finish(): con Atrás se vuelve a este login.
        findViewById<View>(R.id.btnElegirRol).setOnClickListener {
            startActivity(Intent(this, RolSeleccionActivity::class.java))
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

    // ---------- Iniciar sesión (simulado) ----------

    private fun iniciarSesion() {
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
        if (clave.length < largoMinimoClave) {
            etClave.error = "Mínimo $largoMinimoClave caracteres"
            etClave.requestFocus()
            return
        }

        val destino = cuentasDePrueba[correo]
        if (destino == null) {
            etCorreo.error = "Correo no registrado. Usa uno de prueba o elige un rol."
            etCorreo.requestFocus()
            return
        }

        // Contraseña correcta: se ofrece la huella (si el celular puede) y se entra
        terminarInicioDeSesion(correo, destino)
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

    // Después de iniciar sesión con contraseña: ofrece activar la huella y entra a la app
    private fun terminarInicioDeSesion(correo: String, destino: Class<*>) {
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
                BiometriaHelper.activar(this, correo) { activada ->
                    if (activada) mensaje("Acceso con huella activado")
                    abrirInicio(destino)
                }
            }
            .show()
    }

    private fun entrarConBiometria() {
        BiometriaHelper.entrar(
            this,
            alEntrar = { correo ->
                val destino = cuentasDePrueba[correo]
                if (destino == null) {
                    BiometriaRepositorio.desactivar(this)
                    actualizarBotonBiometria()
                    mensaje("Esa cuenta ya no existe. Inicia sesión con tu contraseña.")
                } else {
                    abrirInicio(destino)
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