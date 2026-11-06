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

// Pantalla "Invitar usuario" / "Editar usuario".
// Si llega EXTRA_USUARIO_ID se edita ese usuario; si no, se registra uno nuevo.
class UsuarioFormularioActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USUARIO_ID = "usuario_id"
    }

    private var rolSeleccionado = Rol.COMPRADOR // el rol con menos permisos va por defecto
    private var usuarioEditando: Usuario? = null

    private lateinit var etNombre: EditText
    private lateinit var etCorreo: EditText
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
            findViewById<TextView>(R.id.btnRegistrar).text = "Guardar cambios"
            etNombre.setText(u.nombre)
            etCorreo.setText(u.correo)
            rolSeleccionado = u.rol
        }
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
        findViewById<View>(R.id.btnRegistrar).setOnClickListener { guardar() }
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

    private fun guardar() {
        val nombre = etNombre.text.toString().trim()
        if (nombre.length < 2) {
            etNombre.error = "Escribe el nombre del usuario"
            etNombre.requestFocus()
            return
        }

        val correo = etCorreo.text.toString().trim()
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
            UsuariosRepositorio.agregar(nombre, correo, rolSeleccionado)
            Toast.makeText(this, "Usuario registrado", Toast.LENGTH_SHORT).show()
        } else {
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
        }
        finish()
    }
}