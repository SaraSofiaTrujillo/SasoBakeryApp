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

// formulario del admin, invita un usuario nuevo o edita nombre y rol de uno existente
class UsuarioFormularioActivity : AppCompatActivity() {
    companion object {
        // datos que llegan cuando se edita, sin id es una invitacion
        const val EXTRA_USUARIO_ID = "usuario_id"
        const val EXTRA_NOMBRE = "usuario_nombre"
        const val EXTRA_CORREO = "usuario_correo"
        const val EXTRA_ROL = "usuario_rol"
        const val EXTRA_ULTIMO_ADMIN = "usuario_ultimo_admin"
    }

    private var rolSeleccionado = Rol.COMPRADOR
    private var idEditando: String? = null
    // true si es el unico admin activo, no se le puede quitar el rol
    private var esUltimoAdmin = false
    private var enCurso = false
    // texto original del boton para restaurarlo despues de cargar
    private var textoBoton = ""

    private lateinit var etNombre: EditText
    private lateinit var etCorreo: EditText
    private lateinit var btnRegistrar: TextView
    private lateinit var chips: List<Pair<TextView, Rol>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_usuario_formulario)

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

        // al editar el correo no se puede cambiar
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

        // el boton listo del teclado guarda
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

    // valida y decide si es edicion o invitacion
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

    // crea la cuenta y envia el correo para que ponga su clave
    private fun invitarUsuario(nombre: String, correo: String) {
        cambiarEstadoCarga(true, "Creando cuenta…")
        AuthRepositorio.crearUsuarioComoAdmin(nombre, correo, rolSeleccionado) { creado, aviso ->
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

    // no deja quitar el rol al ultimo admin
    private fun guardarCambios(id: String, nombre: String) {
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

    // bloquea el boton mientras guarda
    private fun cambiarEstadoCarga(cargando: Boolean, textoCargando: String = "") {
        enCurso = cargando
        btnRegistrar.isEnabled = !cargando
        btnRegistrar.text = if (cargando) textoCargando else textoBoton
    }
}