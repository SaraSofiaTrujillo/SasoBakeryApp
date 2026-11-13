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
 * - Con EXTRA_USUARIO_ID: edita un usuario existente (por ahora de la lista de ejemplo).
 */
class UsuarioFormularioActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USUARIO_ID = "usuario_id"
    }

    private var rolSeleccionado = Rol.COMPRADOR // el rol con menos permisos va por defecto
    private var usuarioEditando: Usuario? = null
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

        // ¿Modo edición? Si el usuario ya no existe, se cierra la pantalla.
        val idEdicion = intent.getIntExtra(EXTRA_USUARIO_ID, -1)
        if (idEdicion >= 0) {
            usuarioEditando = UsuariosRepositorio.obtenerPorId(idEdicion)
            if (usuarioEditando == null) {
                Toast.makeText(this, "Ese usuario ya no existe", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

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

        // Modo edición: textos propios y datos cargados
        usuarioEditando?.let { u ->
            findViewById<TextView>(R.id.tvTitulo).text = "Editar usuario"
            btnRegistrar.text = "Guardar cambios"
            etNombre.setText(u.nombre)
            etCorreo.setText(u.correo)
            rolSeleccionado = u.rol
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

        val correo = etCorreo.text.toString().trim().lowercase()
        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreo.error = "Escribe un correo válido"
            etCorreo.requestFocus()
            return
        }

        val editando = usuarioEditando

        // Al editar, el propio correo del usuario no cuenta como repetido
        if (UsuariosRepositorio.existeCorreo(correo, excluirId = editando?.id)) {
            etCorreo.error = "Ya existe un usuario con este correo"
            etCorreo.requestFocus()
            return
        }

        if (editando == null) {
            invitarUsuario(nombre, correo)
            return
        }

        // No se puede quitar el rol de administrador al último administrador activo
        if (rolSeleccionado != Rol.ADMINISTRADOR &&
            UsuariosRepositorio.esUltimoAdminActivo(editando)
        ) {
            Toast.makeText(
                this,
                "Es el único administrador activo: debe seguir siéndolo",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (UsuariosRepositorio.actualizar(editando.id, nombre, correo, rolSeleccionado) == null) {
            Toast.makeText(this, "Ese usuario ya no existe", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Cambios guardados", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    /**
     * Crea la cuenta real en Firebase y avisa cuando termina. La persona invitada
     * recibe un correo para crear su contraseña; el administrador mantiene su sesión.
     */
    private fun invitarUsuario(nombre: String, correo: String) {
        cambiarEstadoCarga(true)
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

    // Mientras Firebase responde, el botón cambia de texto y no se puede pulsar
    private fun cambiarEstadoCarga(cargando: Boolean) {
        enCurso = cargando
        btnRegistrar.isEnabled = !cargando
        btnRegistrar.text = if (cargando) "Creando cuenta…" else textoBoton
    }
}