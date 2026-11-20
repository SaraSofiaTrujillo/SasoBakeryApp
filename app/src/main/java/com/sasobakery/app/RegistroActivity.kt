package com.sasobakery.app

import android.content.Intent
import android.os.Bundle
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

// registro de cuentas nuevas, solo comprador o vendedor
class RegistroActivity : AppCompatActivity() {
    // rol elegido en los chips
    private var rolSeleccionado = Rol.COMPRADOR
    private var enCurso = false

    private lateinit var etNombre: EditText
    private lateinit var etCorreo: EditText
    private lateinit var etClave: EditText
    private lateinit var etConfirmar: EditText
    private lateinit var etTelefono: EditText
    private lateinit var btnCrear: TextView
    private lateinit var chips: List<Pair<TextView, Rol>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_registro)

        // ajusta los margenes para barras del sistema y teclado
        val cabecera = findViewById<View>(R.id.headerRegistro)
        val panel = findViewById<View>(R.id.panelRegistro)
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

        etNombre = findViewById(R.id.etNombre)
        etCorreo = findViewById(R.id.etCorreo)
        etClave = findViewById(R.id.etClave)
        etConfirmar = findViewById(R.id.etConfirmar)
        btnCrear = findViewById(R.id.btnCrearCuenta)
        etTelefono = findViewById(R.id.etTelefono)

        val btnMostrarClave = findViewById<TextView>(R.id.btnMostrarClave)
        val btnMostrarConfirmar = findViewById<TextView>(R.id.btnMostrarConfirmar)
        // ojo para ver u ocultar cada clave
        btnMostrarClave.setOnClickListener { etClave.alternarVisibilidadClave(btnMostrarClave) }
        btnMostrarConfirmar.setOnClickListener {
            etConfirmar.alternarVisibilidadClave(btnMostrarConfirmar)
        }

        chips = listOf(
            findViewById<TextView>(R.id.chipRolComprador) to Rol.COMPRADOR,
            findViewById<TextView>(R.id.chipRolVendedor) to Rol.VENDEDOR
        )
        chips.forEach { (chip, rol) ->
            chip.setOnClickListener {
                rolSeleccionado = rol
                actualizarChips()
            }
        }
        actualizarChips()

        // el boton listo del teclado crea la cuenta
        etConfirmar.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) {
                crearCuenta()
                true
            } else {
                false
            }
        }

        btnCrear.setOnClickListener { crearCuenta() }
        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }
        findViewById<View>(R.id.btnYaTengoCuenta).setOnClickListener { finish() }
    }

    // resalta el rol elegido
    private fun actualizarChips() {
        chips.forEach { (chip, rol) ->
            if (rol == rolSeleccionado) {
                chip.setBackgroundResource(R.drawable.bg_users_chip_selected)
                chip.setTextColor(0xFFFFFFFF.toInt())
            } else {
                chip.setBackgroundResource(R.drawable.bg_users_chip)
                chip.setTextColor(0xFF5D477F.toInt())
            }
        }
    }

    // valida todos los campos en orden y despues crea la cuenta
    private fun crearCuenta() {
        if (enCurso) return

        val nombre = etNombre.text.toString().trim()
        val correo = etCorreo.text.toString().trim().lowercase()
        val clave = etClave.text.toString()
        val confirmar = etConfirmar.text.toString()

        if (nombre.length < 2) {
            etNombre.error = "Escribe tu nombre"
            etNombre.requestFocus()
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
        if (clave != confirmar) {
            etConfirmar.error = "Las contraseñas no coinciden"
            etConfirmar.requestFocus()
            return
        }

        val telefono = Telefono.normalizar(etTelefono.text.toString())
        if (telefono == null) {
            etTelefono.error = "Escribe un teléfono válido (10 dígitos)"
            etTelefono.requestFocus()
            return
        }

        cambiarEstadoCarga(true)
        AuthRepositorio.registrar(nombre, correo, clave, rolSeleccionado, telefono) { resultado ->
            if (isFinishing || isDestroyed) return@registrar
            cambiarEstadoCarga(false)

            when (resultado) {
                // entra directo a la pantalla de su rol sin pantallas atras
                is ResultadoAuth.Exito -> {
                    Toast.makeText(this, "Cuenta creada", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, AuthRepositorio.destinoPara(resultado.rol))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                }
                is ResultadoAuth.Error ->
                    Toast.makeText(this, resultado.mensaje, Toast.LENGTH_LONG).show()
            }
        }
    }

    // bloquea el boton mientras crea la cuenta
    private fun cambiarEstadoCarga(cargando: Boolean) {
        enCurso = cargando
        btnCrear.isEnabled = !cargando
        btnCrear.text = if (cargando) "Creando cuenta…" else "Crear cuenta"
    }
}