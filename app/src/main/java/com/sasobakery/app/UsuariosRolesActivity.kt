package com.sasobakery.app

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class UsuariosRolesActivity : AppCompatActivity() {

    // Los usuarios viven en UsuariosRepositorio (datos de ejemplo en memoria, luego
    // vendrán de la base de datos). Se vuelven a leer en onResume, así el usuario
    // recién invitado o editado aparece al volver del formulario.
    private var usuarios: List<Usuario> = UsuariosRepositorio.obtenerTodos()

    private val adapter = UsuariosAdapter(
        alTocarMas = { usuario, ancla -> mostrarMenuUsuario(usuario, ancla) }
    )
    private var rolSeleccionado: Rol? = null
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
        etBuscar.doAfterTextChanged { aplicarFiltros() }

        chips = listOf<Pair<TextView, Rol?>>(
            findViewById<TextView>(R.id.chipTodos) to null,
            findViewById<TextView>(R.id.chipComprador) to Rol.COMPRADOR,
            findViewById<TextView>(R.id.chipVendedor) to Rol.VENDEDOR,
            findViewById<TextView>(R.id.chipAdmin) to Rol.ADMINISTRADOR
        )
        chips.forEach { (chip, rol) ->
            chip.setOnClickListener {
                rolSeleccionado = rol
                actualizarChips()
                aplicarFiltros()
            }
        }

        findViewById<TextView>(R.id.btnInvitar).setOnClickListener {
            startActivity(Intent(this, UsuarioFormularioActivity::class.java))
        }
        findViewById<View>(R.id.btnFiltro).setOnClickListener { proximamente() }

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

        actualizarChips()
        aplicarFiltros()
    }

    override fun onResume() {
        super.onResume()
        refrescarLista()
    }

    /** Vuelve a leer los usuarios y respeta la búsqueda y el chip activos. */
    private fun refrescarLista() {
        usuarios = UsuariosRepositorio.obtenerTodos()
        aplicarFiltros()
    }

    private fun aplicarFiltros() {
        val texto = etBuscar.text.toString().trim().lowercase()
        val lista = usuarios.filter { u ->
            (rolSeleccionado == null || u.rol == rolSeleccionado) &&
                    (texto.isEmpty() ||
                            u.nombre.lowercase().contains(texto) ||
                            u.correo.lowercase().contains(texto))
        }
        adapter.submit(lista)
    }

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

    // ---------- Menú de los tres puntos ----------

    private fun mostrarMenuUsuario(usuario: Usuario, ancla: View) {
        val menu = PopupMenu(this, ancla)
        menu.menu.add(0, OPCION_EDITAR, 0, "Editar")
        menu.menu.add(0, OPCION_ESTADO, 1, if (usuario.activo) "Desactivar" else "Activar")

        // "Eliminar" en el rojo de la app para que se note que es destructivo
        val eliminar = SpannableString("Eliminar")
        eliminar.setSpan(ForegroundColorSpan(0xFFC2485F.toInt()), 0, eliminar.length, 0)
        menu.menu.add(0, OPCION_ELIMINAR, 2, eliminar)

        menu.setOnMenuItemClickListener { opcion ->
            when (opcion.itemId) {
                OPCION_EDITAR -> editarUsuario(usuario)
                OPCION_ESTADO -> cambiarEstado(usuario)
                OPCION_ELIMINAR -> confirmarEliminar(usuario)
            }
            true
        }
        menu.show()
    }

    private fun editarUsuario(usuario: Usuario) {
        val intent = Intent(this, UsuarioFormularioActivity::class.java)
        intent.putExtra(UsuarioFormularioActivity.EXTRA_USUARIO_ID, usuario.id)
        startActivity(intent)
    }

    // ---------- Desactivar / activar ----------

    private fun cambiarEstado(usuario: Usuario) {
        if (usuario.activo) {
            if (UsuariosRepositorio.esUltimoAdminActivo(usuario)) {
                avisarUltimoAdmin("desactivar")
                return
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("¿Desactivar a ${usuario.nombre}?")
                .setMessage("No podrá iniciar sesión hasta que vuelvas a activar su cuenta. Sus datos se conservan.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Desactivar") { _, _ -> aplicarEstado(usuario, false) }
                .show()
        } else {
            aplicarEstado(usuario, true)   // activar no necesita confirmación
        }
    }

    private fun aplicarEstado(usuario: Usuario, activo: Boolean) {
        if (UsuariosRepositorio.cambiarActivo(usuario.id, activo) == null) {
            Toast.makeText(this, "Ese usuario ya no existe", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                if (activo) "Usuario activado" else "Usuario desactivado",
                Toast.LENGTH_SHORT
            ).show()
        }
        refrescarLista()
    }

    // ---------- Eliminar ----------

    private fun confirmarEliminar(usuario: Usuario) {
        if (UsuariosRepositorio.esUltimoAdminActivo(usuario)) {
            avisarUltimoAdmin("eliminar")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Eliminar a ${usuario.nombre}?")
            .setMessage("Se borrará su cuenta de forma permanente. Esta acción no se puede deshacer.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                if (UsuariosRepositorio.eliminar(usuario.id)) {
                    Toast.makeText(this, "Usuario eliminado", Toast.LENGTH_SHORT).show()
                }
                refrescarLista()
            }
            .show()
    }

    private fun avisarUltimoAdmin(accion: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("No se puede $accion")
            .setMessage("Es el único administrador activo. Debe quedar al menos uno para gestionar la app.")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun proximamente() {
        Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val OPCION_EDITAR = 1
        const val OPCION_ESTADO = 2
        const val OPCION_ELIMINAR = 3
    }
}