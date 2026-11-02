package com.sasobakery.app

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class UsuariosRolesActivity : AppCompatActivity() {

    // Datos de ejemplo (luego se reemplazarán por la base de datos)
    private val usuarios = listOf(
        Usuario("VM", "Valentina Mendoza", "valentina.mendoza@gmail.com", Rol.COMPRADOR),
        Usuario("CR", "Camila Ríos", "camila.rios@sasobakery.pe", Rol.VENDEDOR),
        Usuario("AS", "Andrea Salas", "andrea.salas@sasobakery.pe", Rol.ADMINISTRADOR),
        Usuario("MF", "María Flores", "maria.flores@gmail.com", Rol.COMPRADOR),
        Usuario("DR", "Diego Rojas", "diego.rojas@gmail.com", Rol.COMPRADOR)
    )

    private val adapter = UsuariosAdapter()
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

        findViewById<TextView>(R.id.btnInvitar).setOnClickListener { proximamente() }
        findViewById<android.view.View>(R.id.btnFiltro).setOnClickListener { proximamente() }

        // Menú inferior
        findViewById<android.view.View>(R.id.navResumen).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.navPedidos).setOnClickListener {
            startActivity(android.content.Intent(this, PedidosActivity::class.java))
            finish()
        }
        findViewById<android.view.View>(R.id.navProductos).setOnClickListener {
            startActivity(android.content.Intent(this, ProductosCategoriasActivity::class.java))
            finish()
        }

        actualizarChips()
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

    private fun proximamente() {
        Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
    }
}