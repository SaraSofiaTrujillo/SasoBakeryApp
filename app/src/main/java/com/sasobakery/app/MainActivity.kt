package com.sasobakery.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration

// pantalla principal del admin, desde aqui va a usuarios, productos y pedidos
class MainActivity : AppCompatActivity() {
    // escucha de los datos del negocio, se activa en onStart y se quita en onStop
    private var registroNegocio: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.navUsuarios).setOnClickListener {
            startActivity(Intent(this, UsuariosRolesActivity::class.java))
        }
        findViewById<View>(R.id.navProductos).setOnClickListener {
            startActivity(Intent(this, ProductosCategoriasActivity::class.java))
        }
        findViewById<View>(R.id.navPedidos).setOnClickListener {
            startActivity(Intent(this, PedidosActivity::class.java))
        }
        findViewById<View>(R.id.btnNegocio).setOnClickListener { mostrarDatosNegocio() }
        findViewById<View>(R.id.btnCerrarSesion).setOnClickListener { confirmarCerrarSesion() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // deja cargados los datos del negocio para el resto de pantallas
    override fun onStart() {
        super.onStart()
        registroNegocio = NegocioRepositorio.escuchar(
            alCambiar = { },
            alFallar = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        )
    }

    override fun onStop() {
        super.onStop()
        registroNegocio?.remove()
        registroNegocio = null
    }

    // hoja para editar los datos de la tienda, solo el admin
    private fun mostrarDatosNegocio() {
        val dialogo = BottomSheetDialog(this)
        val vista = layoutInflater.inflate(R.layout.dialog_datos_negocio, null)

        val etNombre = vista.findViewById<EditText>(R.id.etNegocioNombre)
        val etTelefono = vista.findViewById<EditText>(R.id.etNegocioTelefono)
        val etDireccion = vista.findViewById<EditText>(R.id.etNegocioDireccion)
        val etHorario = vista.findViewById<EditText>(R.id.etNegocioHorario)
        val btnGuardar = vista.findViewById<TextView>(R.id.btnGuardarNegocio)

        val actual = NegocioRepositorio.obtener()
        etNombre.setText(actual.nombre)
        etTelefono.setText(actual.telefono)
        etDireccion.setText(actual.direccion)
        etHorario.setText(actual.horario)

        vista.findViewById<View>(R.id.btnCancelarNegocio).setOnClickListener { dialogo.dismiss() }

        btnGuardar.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val telefono = etTelefono.text.toString().trim()
            val direccion = etDireccion.text.toString().trim()
            val horario = etHorario.text.toString().trim()

            // valida todo antes de guardar, cada error se muestra en su campo
            if (nombre.length < 2) {
                etNombre.error = "Escribe el nombre del negocio"
                etNombre.requestFocus()
                return@setOnClickListener
            }
            if (telefono.filter { it.isDigit() }.length < 7) {
                etTelefono.error = "Escribe un teléfono válido"
                etTelefono.requestFocus()
                return@setOnClickListener
            }
            if (direccion.length < 5) {
                etDireccion.error = "Escribe la dirección del negocio"
                etDireccion.requestFocus()
                return@setOnClickListener
            }
            if (horario.isEmpty()) {
                etHorario.error = "Escribe el horario de atención"
                etHorario.requestFocus()
                return@setOnClickListener
            }

            // bloquea el boton mientras guarda
            btnGuardar.isEnabled = false
            btnGuardar.text = "Guardando…"
            NegocioRepositorio.guardar(DatosNegocio(nombre, telefono, direccion, horario)) { error ->
                if (isFinishing || isDestroyed) return@guardar
                if (error != null) {
                    btnGuardar.isEnabled = true
                    btnGuardar.text = "Guardar"
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                } else {
                    dialogo.dismiss()
                    Toast.makeText(this, "Datos del negocio actualizados", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialogo.setContentView(vista)
        dialogo.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialogo.behavior.skipCollapsed = true
        dialogo.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialogo.show()
    }

    private fun confirmarCerrarSesion() {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Cerrar sesión?")
            .setMessage("Volverás a la pantalla de inicio de sesión.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar sesión") { _, _ -> cerrarSesion() }
            .show()
    }

    // borra la sesion y deja el login sin pantallas atras
    private fun cerrarSesion() {
        AuthRepositorio.cerrarSesion()
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }
}