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

/**
 * Pantalla de registro. Crea la cuenta con AuthRepositorio y, si sale bien,
 * entra directo a la pantalla principal del rol elegido (comprador o vendedor).
 * Los administradores no se registran aquí: los crea otro administrador.
 */
class RegistroActivity : AppCompatActivity() {

    private var rolSeleccionado = Rol.COMPRADOR // el rol con menos permisos va por defecto
    private var enCurso = false // evita enviar el formulario dos veces seguidas

    private lateinit var etNombre: EditText
    private lateinit var etCorreo: EditText
    private lateinit var etClave: EditText
    private lateinit var etConfirmar: EditText
    private lateinit var etTelefono: EditText
    private lateinit var etDireccion: EditText
    private lateinit var etHorario: EditText
    private lateinit var bloqueNegocio: View
    private lateinit var btnCrear: TextView
    private lateinit var chips: List<Pair<TextView, Rol>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_registro)

        // Mismos márgenes del login: el teclado empuja el contenido y no tapa los campos
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
        etDireccion = findViewById(R.id.etDireccion)
        etHorario = findViewById(R.id.etHorario)
        bloqueNegocio = findViewById(R.id.bloqueNegocio)

        // Botones "Mostrar / Ocultar" de las dos contraseñas
        val btnMostrarClave = findViewById<TextView>(R.id.btnMostrarClave)
        val btnMostrarConfirmar = findViewById<TextView>(R.id.btnMostrarConfirmar)
        btnMostrarClave.setOnClickListener { etClave.alternarVisibilidadClave(btnMostrarClave) }
        btnMostrarConfirmar.setOnClickListener {
            etConfirmar.alternarVisibilidadClave(btnMostrarConfirmar)
        }

        // Selector de rol (solo comprador o vendedor)
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

        // El botón "Listo" del teclado también crea la cuenta
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

    // Mismo estilo de chips que el resto de la app
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
        // Los datos del negocio solo se piden a los vendedores
        bloqueNegocio.visibility = if (rolSeleccionado == Rol.VENDEDOR) View.VISIBLE else View.GONE
    }

    /** Valida los campos y, si todo está bien, crea la cuenta en Firebase. */
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

        // Si se registra como vendedor, los datos del negocio son obligatorios
        val telefono = etTelefono.text.toString().trim()
        val direccion = etDireccion.text.toString().trim()
        val horario = etHorario.text.toString().trim()
        if (rolSeleccionado == Rol.VENDEDOR) {
            if (telefono.filter { it.isDigit() }.length < 7) {
                etTelefono.error = "Escribe un teléfono válido"
                etTelefono.requestFocus()
                return
            }
            if (direccion.length < 5) {
                etDireccion.error = "Escribe la dirección de tu negocio"
                etDireccion.requestFocus()
                return
            }
            if (horario.isEmpty()) {
                etHorario.error = "Escribe tu horario de atención"
                etHorario.requestFocus()
                return
            }
        }
        val datosNegocio = if (rolSeleccionado == Rol.VENDEDOR) {
            mapOf(
                "negocio" to "Saso Bakery",
                "telefono" to telefono,
                "direccion" to direccion,
                "horario" to horario
            )
        } else {
            emptyMap()
        }

        cambiarEstadoCarga(true)
        AuthRepositorio.registrar(nombre, correo, clave, rolSeleccionado, datosNegocio) { resultado ->
            // Si la pantalla ya se cerró mientras esperaba, no se hace nada
            if (isFinishing || isDestroyed) return@registrar
            cambiarEstadoCarga(false)

            when (resultado) {
                is ResultadoAuth.Exito -> {
                    Toast.makeText(this, "Cuenta creada", Toast.LENGTH_SHORT).show()
                    // Se borra el historial: "Atrás" no vuelve al registro ni al login
                    val intent = Intent(this, AuthRepositorio.destinoPara(resultado.rol))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                }
                is ResultadoAuth.Error ->
                    Toast.makeText(this, resultado.mensaje, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Mientras Firebase responde, el botón cambia de texto y no se puede pulsar
    private fun cambiarEstadoCarga(cargando: Boolean) {
        enCurso = cargando
        btnCrear.isEnabled = !cargando
        btnCrear.text = if (cargando) "Creando cuenta…" else "Crear cuenta"
    }
}