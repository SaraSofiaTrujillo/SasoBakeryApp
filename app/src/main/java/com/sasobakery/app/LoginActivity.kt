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

// Pantalla de inicio de sesión (acceso principal de la app).
// Por ahora es SIMULADA: no hay autenticación real. Según el correo de prueba
// abre la pantalla principal de cada rol. La pantalla de selección de rol
// (RolSeleccionActivity) sigue disponible con el enlace "Entrar eligiendo un rol".
class LoginActivity : AppCompatActivity() {

    // ===== CUENTAS DE PRUEBA (correo -> pantalla principal del rol) =====
    private val cuentasDePrueba: Map<String, Class<*>> = mapOf(
        "valentina@email.com" to CatalogoCompradorActivity::class.java,
        "valentina.mendoza@gmail.com" to CatalogoCompradorActivity::class.java,
        "camila.rios@sasobakery.pe" to VendedorPrincipalActivity::class.java,
        "andrea.salas@sasobakery.pe" to MainActivity::class.java
    )
    private val largoMinimoClave = 6
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

        // Todavía sin pantalla
        findViewById<View>(R.id.btnOlvide).setOnClickListener { mensaje("Próximamente") }
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

        // Se cierra el login para que Atrás no regrese a él desde dentro de la app
        startActivity(Intent(this, destino))
        finish()
    }

    private fun mensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }
}