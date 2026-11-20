package com.sasobakery.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration

// lista de usuarios para el admin, con busqueda, filtro por rol y menu de acciones
class UsuariosRolesActivity : AppCompatActivity() {
    private val adapter = UsuariosAdapter { usuario, vista -> mostrarMenu(usuario, vista) }

    private var todos: List<Usuario> = emptyList()
    // rol elegido en los chips, null es todos
    private var rolFiltro: Rol? = null
    // escucha de firestore, se activa en onStart y se quita en onStop
    private var registro: ListenerRegistration? = null

    private lateinit var etBuscar: EditText
    private lateinit var chips: List<Pair<TextView, Rol?>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_usuarios_roles)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        val rv = findViewById<RecyclerView>(R.id.rvUsuarios)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        etBuscar = findViewById(R.id.etBuscar)
        // filtra mientras se escribe
        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                aplicarFiltros()
            }
        })

        chips = listOf(
            findViewById<TextView>(R.id.chipTodos) to null,
            findViewById<TextView>(R.id.chipComprador) to Rol.COMPRADOR,
            findViewById<TextView>(R.id.chipVendedor) to Rol.VENDEDOR,
            findViewById<TextView>(R.id.chipAdmin) to Rol.ADMINISTRADOR
        )
        chips.forEach { (chip, rol) ->
            chip.setOnClickListener {
                rolFiltro = rol
                aplicarFiltros()
            }
        }

        // limpia busqueda y filtro
        findViewById<View>(R.id.btnFiltro).setOnClickListener {
            etBuscar.setText("")
            rolFiltro = null
            aplicarFiltros()
        }

        findViewById<View>(R.id.btnInvitar).setOnClickListener {
            startActivity(Intent(this, UsuarioFormularioActivity::class.java))
        }

        findViewById<View>(R.id.navResumen).setOnClickListener { finish() }
        findViewById<View>(R.id.navPedidos).setOnClickListener {
            startActivity(Intent(this, PedidosActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navProductos).setOnClickListener {
            startActivity(Intent(this, ProductosCategoriasActivity::class.java))
            finish()
        }

        aplicarFiltros()
    }

    // usuarios en tiempo real
    override fun onStart() {
        super.onStart()
        registro = UsuariosRepositorio.escuchar(
            alCambiar = { lista ->
                todos = lista
                actualizarEstadisticas()
                aplicarFiltros()
            },
            alFallar = { mensaje ->
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onStop() {
        super.onStop()
        registro?.remove()
        registro = null
    }

    // combina el rol y el texto, busca por nombre o correo
    private fun aplicarFiltros() {
        val texto = etBuscar.text.toString().trim().lowercase()
        val visibles = todos.filter { u ->
            (rolFiltro == null || u.rol == rolFiltro) &&
                    (texto.isEmpty() ||
                            u.nombre.lowercase().contains(texto) ||
                            u.correo.lowercase().contains(texto))
        }
        adapter.submit(visibles)
        actualizarChips()
    }

    // resalta el chip elegido
    private fun actualizarChips() {
        chips.forEach { (chip, rol) ->
            if (rol == rolFiltro) {
                chip.setBackgroundResource(R.drawable.bg_users_chip_selected)
                chip.setTextColor(0xFFFFFFFF.toInt())
            } else {
                chip.setBackgroundResource(R.drawable.bg_users_chip)
                chip.setTextColor(0xFF5D477F.toInt())
            }
        }
    }

    // contadores de arriba, equipo es todo el que no es comprador
    private fun actualizarEstadisticas() {
        findViewById<TextView>(R.id.tvStatTotal).text = todos.size.toString()
        findViewById<TextView>(R.id.tvStatCompradores).text =
            todos.count { it.rol == Rol.COMPRADOR }.toString()
        findViewById<TextView>(R.id.tvStatEquipo).text =
            todos.count { it.rol != Rol.COMPRADOR }.toString()
    }

    // menu de los tres puntos de cada usuario
    private fun mostrarMenu(usuario: Usuario, vista: View) {
        val menu = PopupMenu(this, vista)
        menu.menu.add(0, 1, 0, "Editar")
        menu.menu.add(0, 2, 1, if (usuario.activo) "Desactivar" else "Activar")
        menu.menu.add(0, 3, 2, "Eliminar")
        menu.setOnMenuItemClickListener { opcion ->
            when (opcion.itemId) {
                1 -> editar(usuario)
                2 -> confirmarCambioActivo(usuario)
                3 -> confirmarEliminar(usuario)
            }
            true
        }
        menu.show()
    }

    // abre el formulario con los datos del usuario
    private fun editar(usuario: Usuario) {
        val intent = Intent(this, UsuarioFormularioActivity::class.java)
        intent.putExtra(UsuarioFormularioActivity.EXTRA_USUARIO_ID, usuario.id)
        intent.putExtra(UsuarioFormularioActivity.EXTRA_NOMBRE, usuario.nombre)
        intent.putExtra(UsuarioFormularioActivity.EXTRA_CORREO, usuario.correo)
        intent.putExtra(UsuarioFormularioActivity.EXTRA_ROL, usuario.rol.name)
        intent.putExtra(
            UsuarioFormularioActivity.EXTRA_ULTIMO_ADMIN,
            UsuariosRepositorio.esUltimoAdminActivo(todos, usuario)
        )
        startActivity(intent)
    }

    // razon por la que no se puede desactivar o eliminar, null si si se puede
    private fun motivoDeBloqueo(usuario: Usuario): String? = when {
        usuario.id == AuthRepositorio.uidActual() ->
            "No puedes hacerlo con tu propia cuenta."
        UsuariosRepositorio.esUltimoAdminActivo(todos, usuario) ->
            "Es el único administrador activo: debe seguir siéndolo."
        else -> null
    }

    // activar siempre se puede, desactivar pasa por las reglas de bloqueo
    private fun confirmarCambioActivo(usuario: Usuario) {
        if (usuario.activo) {
            val motivo = motivoDeBloqueo(usuario)
            if (motivo != null) {
                Toast.makeText(this, motivo, Toast.LENGTH_LONG).show()
                return
            }
        }

        val accion = if (usuario.activo) "Desactivar" else "Activar"
        val detalle = if (usuario.activo) {
            "${usuario.nombre} ya no podrá iniciar sesión hasta que lo actives de nuevo."
        } else {
            "${usuario.nombre} podrá iniciar sesión otra vez."
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("$accion usuario")
            .setMessage(detalle)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton(accion) { _, _ ->
                UsuariosRepositorio.cambiarActivo(usuario.id, !usuario.activo) { error ->
                    if (isFinishing || isDestroyed) return@cambiarActivo
                    if (error != null) {
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    // pide confirmacion antes de borrar el perfil
    private fun confirmarEliminar(usuario: Usuario) {
        val motivo = motivoDeBloqueo(usuario)
        if (motivo != null) {
            Toast.makeText(this, motivo, Toast.LENGTH_LONG).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar usuario")
            .setMessage("Se eliminará el perfil de ${usuario.nombre} y ya no podrá entrar a la app. Esta acción no se puede deshacer.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                UsuariosRepositorio.eliminar(usuario.id) { error ->
                    if (isFinishing || isDestroyed) return@eliminar
                    if (error != null) {
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Usuario eliminado", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }
}