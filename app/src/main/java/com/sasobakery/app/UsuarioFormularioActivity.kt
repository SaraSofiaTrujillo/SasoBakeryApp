package com.sasobakery.app

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Pantalla "Invitar usuario" / "Editar usuario" del administrador.
 * - Sin EXTRA_USUARIO_ID: invita a una persona nueva. Se crea su cuenta real en
 *   Firebase y recibe un correo para elegir su contraseña.
 * - Con EXTRA_USUARIO_ID: edita el nombre y el rol de un usuario existente en Firestore.
 *   El correo no se puede cambiar: es el acceso de la cuenta en Firebase Authentication.
 */
class UsuarioFormularioActivity : AppCompatActivity() {

    companion object {
        // Datos que la lista envía para abrir el formulario en modo edición
        const val EXTRA_USUARIO_ID = "usuario_id"
        const val EXTRA_NOMBRE = "usuario_nombre"
        const val EXTRA_CORREO = "usuario_correo"
        const val EXTRA_ROL = "usuario_rol"
        const val EXTRA_ULTIMO_ADMIN = "usuario_ultimo_admin"
    }

    private var rolSeleccionado = Rol.COMPRADOR // el rol con menos permisos va por defecto
    private var idEditando: String? = null // null = se está invitando a alguien nuevo
    private var esUltimoAdmin = false // true si no se le puede quitar el rol de administrador
    private var enCurso = false // evita enviar el formulario dos veces seguidas
    private var textoBoton = "" // texto original del botón, para restaurarlo

    private lateinit var etNombre: EditText
    private lateinit var etCorreo: EditText
    private lateinit var btnRegistrar: TextView
    private lateinit var chips: List<Pair<TextView, Rol>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_usuario_formulario)

        // Deja espacio para las barras del sistema y para el teclado
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        etNombre = findViewById(R.id.etNombre)
        etCorreo = findViewById(R.id.etCorreo)
        btnRegistrar = findViewById(R.id.btnRegistrar)

        chips = listOf(
            findViewById<TextView>(R.id.chipRolComprador) to Rol.COMPRADOR,
            findViewById<TextView>(R.id.chipRolVendedor) to Rol.VENDEDOR,
            findViewById<TextView>(R.id.chipRolAdmin) to Rol.ADMINISTRADOR
        )
        chips.forEach { (chip, rol) ->
            chip.setOnClickListener {
                rolSeleccionado = rol
                actualizarChips()
            }
        }

        // Modo edición: se cargan los datos recibidos y el correo queda bloqueado
        idEditando = intent.getStringExtra(EXTRA_USUARIO_ID)
        if (idEditando != null) {
            findViewById<TextView>(R.id.tvTitulo).text = "Editar usuario"
            btnRegistrar.text = "Guardar cambios"
            etNombre.setText(intent.getStringExtra(EXTRA_NOMBRE).orEmpty())
            etCorreo.setText(intent.getStringExtra(EXTRA_CORREO).orEmpty())
            etCorreo.isEnabled = false
            etCorreo.alpha = 0.6f
            rolSeleccionado = Rol.values().firstOrNull {
                it.name == intent.getStringExtra(EXTRA_ROL)
            } ?: Rol.COMPRADOR
            esUltimoAdmin = intent.getBooleanExtra(EXTRA_ULTIMO_ADMIN, false)
        }
        textoBoton = btnRegistrar.text.toString()
        actualizarChips()

        // El botón "Listo" del teclado también guarda
        etCorreo.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) {
                guardar()
                true
            } else {
                false
            }
        }

        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }
        btnRegistrar.setOnClickListener { guardar() }
    }

    // Mismo estilo de chips que el filtro de la lista de usuarios
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

    /** Valida los campos y, según el modo, invita a un usuario nuevo o guarda los cambios. */
    private fun guardar() {
        if (enCurso) return

        val nombre = etNombre.text.toString().trim()
        if (nombre.length < 2) {
            etNombre.error = "Escribe el nombre del usuario"
            etNombre.requestFocus()
            return
        }

        val editando = idEditando
        if (editando != null) {
            guardarCambios(editando, nombre)
            return
        }

        val correo = etCorreo.text.toString().trim().lowercase()
        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreo.error = "Escribe un correo válido"
            etCorreo.requestFocus()
            return
        }
        invitarUsuario(nombre, correo)
    }

    /**
     * Crea la cuenta real en Firebase y avisa cuando termina. La persona invitada
     * recibe un correo para crear su contraseña; el administrador mantiene su sesión.
     */
    private fun invitarUsuario(nombre: String, correo: String) {
        cambiarEstadoCarga(true, "Creando cuenta…")
        AuthRepositorio.crearUsuarioComoAdmin(nombre, correo, rolSeleccionado) { creado, aviso ->
            // Si la pantalla ya se cerró mientras esperaba, no se hace nada
            if (isFinishing || isDestroyed) return@crearUsuarioComoAdmin
            cambiarEstadoCarga(false)

            if (!creado) {
                Toast.makeText(this, aviso ?: "No se pudo crear el usuario", Toast.LENGTH_LONG).show()
                return@crearUsuarioComoAdmin
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("Usuario creado")
                .setMessage(aviso ?: "Enviamos un correo a $correo para que cree su contraseña.")
                .setCancelable(false)
                .setPositiveButton("Listo") { _, _ -> finish() }
                .show()
        }
    }

    /** Guarda en Firestore el nuevo nombre y rol del usuario que se está editando. */
    private fun guardarCambios(id: String, nombre: String) {
        // No se puede quitar el rol de administrador al último administrador activo
        if (rolSeleccionado != Rol.ADMINISTRADOR && esUltimoAdmin) {
            Toast.makeText(
                this,
                "Es el único administrador activo: debe seguir siéndolo",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        cambiarEstadoCarga(true, "Guardando…")
        UsuariosRepositorio.actualizar(id, nombre, rolSeleccionado) { error ->
            if (isFinishing || isDestroyed) return@actualizar
            cambiarEstadoCarga(false)

            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Cambios guardados", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // Mientras Firebase responde, el botón cambia de texto y no se puede pulsar
    private fun cambiarEstadoCarga(cargando: Boolean, textoCargando: String = "") {
        enCurso = cargando
        btnRegistrar.isEnabled = !cargando
        btnRegistrar.text = if (cargando) textoCargando else textoBoton
    }
}