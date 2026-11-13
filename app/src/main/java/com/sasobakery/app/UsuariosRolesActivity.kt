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

/**
 * Lista de usuarios del administrador. Los datos vienen de la colección "usuarios"
 * de Firestore y se actualizan solos cuando alguien crea, edita o elimina un usuario.
 * Desde aquí se abre el formulario (invitar / editar) y se activa, desactiva o elimina.
 */
class UsuariosRolesActivity : AppCompatActivity() {

    private val adapter = UsuariosAdapter { usuario, vista -> mostrarMenu(usuario, vista) }

    private var todos: List<Usuario> = emptyList() // lista completa tal como viene de Firestore
    private var rolFiltro: Rol? = null // null = mostrar todos los roles
    private var registro: ListenerRegistration? = null // la "escucha" de Firestore

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

        // El botón de filtro limpia el buscador y vuelve a mostrar todos los roles
        findViewById<View>(R.id.btnFiltro).setOnClickListener {
            etBuscar.setText("")
            rolFiltro = null
            aplicarFiltros()
        }

        findViewById<View>(R.id.btnInvitar).setOnClickListener {
            startActivity(Intent(this, UsuarioFormularioActivity::class.java))
        }

        // Menú inferior
        findViewById<View>(R.id.navResumen).setOnClickListener { finish() }
        findViewById<View>(R.id.navPedidos).setOnClickListener {
            startActivity(Intent(this, PedidosActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navProductos).setOnClickListener {
            startActivity(Intent(this, ProductosCategoriasActivity::class.java))
            finish()
        }

        aplicarFiltros() // dibuja los chips desde el inicio
    }

    // Empieza a escuchar Firestore cuando la pantalla se ve...
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

    // ...y deja de escuchar cuando ya no se ve, para no gastar datos ni batería
    override fun onStop() {
        super.onStop()
        registro?.remove()
        registro = null
    }

    /** Muestra solo los usuarios que cumplen el rol elegido y el texto del buscador. */
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

    // Mismo estilo de chips que el formulario
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

    // Los tres números de la tarjeta: total, compradores y equipo (vendedores + admins)
    private fun actualizarEstadisticas() {
        findViewById<TextView>(R.id.tvStatTotal).text = todos.size.toString()
        findViewById<TextView>(R.id.tvStatCompradores).text =
            todos.count { it.rol == Rol.COMPRADOR }.toString()
        findViewById<TextView>(R.id.tvStatEquipo).text =
            todos.count { it.rol != Rol.COMPRADOR }.toString()
    }

    // ---------- Menú de los tres puntitos de cada usuario ----------

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

    /**
     * Revisa que sea seguro desactivar o eliminar a este usuario. Devuelve el motivo
     * si NO se puede, o null si está permitido.
     */
    private fun motivoDeBloqueo(usuario: Usuario): String? = when {
        usuario.id == AuthRepositorio.uidActual() ->
            "No puedes hacerlo con tu propia cuenta."
        UsuariosRepositorio.esUltimoAdminActivo(todos, usuario) ->
            "Es el único administrador activo: debe seguir siéndolo."
        else -> null
    }

    private fun confirmarCambioActivo(usuario: Usuario) {
        // Activar siempre se puede; desactivar tiene restricciones
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
                    // Si salió bien, la lista se actualiza sola gracias a la escucha
                }
            }
            .show()
    }

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