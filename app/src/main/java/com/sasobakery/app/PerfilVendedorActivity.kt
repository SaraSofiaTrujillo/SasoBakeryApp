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

class PerfilVendedorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_perfil_vendedor)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        findViewById<View>(R.id.btnEditar).setOnClickListener { mostrarEditar() }
        findViewById<View>(R.id.btnCerrarSesion).setOnClickListener { confirmarCerrarSesion() }

        findViewById<View>(R.id.navResumen).setOnClickListener { volverAlResumen() }
        findViewById<View>(R.id.navPedidos).setOnClickListener {
            startActivity(Intent(this, PedidosVendedorActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navProductos).setOnClickListener {
            startActivity(Intent(this, ProductosInventarioActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        mostrarPerfil()
    }

    private fun mostrarPerfil() {
        val p = PerfilVendedorRepositorio.obtener()

        findViewById<TextView>(R.id.tvIniciales).text = p.iniciales
        findViewById<TextView>(R.id.tvNombreCabecera).text = p.nombre
        findViewById<TextView>(R.id.tvCorreoCabecera).text = p.correo

        findViewById<TextView>(R.id.tvDatoNombre).text = p.nombre
        findViewById<TextView>(R.id.tvDatoCorreo).text = p.correo
        findViewById<TextView>(R.id.tvDatoTelefono).text = p.telefono

        findViewById<TextView>(R.id.tvDatoNegocio).text = p.negocio
        findViewById<TextView>(R.id.tvDatoDireccion).text = p.direccion
        findViewById<TextView>(R.id.tvDatoHorario).text = p.horario
    }


    private fun mostrarEditar() {
        val actual = PerfilVendedorRepositorio.obtener()
        val dialogo = BottomSheetDialog(this)
        val vista = layoutInflater.inflate(R.layout.dialog_editar_perfil, null)

        val etNombre = vista.findViewById<EditText>(R.id.etPerfilNombre)
        val etTelefono = vista.findViewById<EditText>(R.id.etPerfilTelefono)
        val etNegocio = vista.findViewById<EditText>(R.id.etPerfilNegocio)
        val etDireccion = vista.findViewById<EditText>(R.id.etPerfilDireccion)
        val etHorario = vista.findViewById<EditText>(R.id.etPerfilHorario)

        etNombre.setText(actual.nombre)
        vista.findViewById<TextView>(R.id.tvPerfilCorreo).text = actual.correo
        etTelefono.setText(actual.telefono)
        etNegocio.setText(actual.negocio)
        etDireccion.setText(actual.direccion)
        etHorario.setText(actual.horario)

        vista.findViewById<View>(R.id.btnCancelarEdicion).setOnClickListener {
            dialogo.dismiss()
        }

        vista.findViewById<View>(R.id.btnGuardarEdicion).setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val telefono = etTelefono.text.toString().trim()
            val negocio = etNegocio.text.toString().trim()

            if (nombre.isEmpty()) {
                etNombre.error = "Escribe tu nombre"
                etNombre.requestFocus()
                return@setOnClickListener
            }
            if (telefono.isEmpty()) {
                etTelefono.error = "Escribe tu teléfono"
                etTelefono.requestFocus()
                return@setOnClickListener
            }
            if (negocio.isEmpty()) {
                etNegocio.error = "Escribe el nombre del negocio"
                etNegocio.requestFocus()
                return@setOnClickListener
            }

            PerfilVendedorRepositorio.guardar(
                actual.copy(
                    nombre = nombre,
                    telefono = telefono,
                    negocio = negocio,
                    direccion = etDireccion.text.toString().trim(),
                    horario = etHorario.text.toString().trim()
                )
            )
            mostrarPerfil()
            dialogo.dismiss()
            Toast.makeText(this, "Información actualizada", Toast.LENGTH_SHORT).show()
        }

        dialogo.setContentView(vista)
        dialogo.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialogo.behavior.skipCollapsed = true
        dialogo.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialogo.show()
    }


    private fun volverAlResumen() {
        val intent = Intent(this, VendedorPrincipalActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun confirmarCerrarSesion() {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Cerrar sesión?")
            .setMessage("Volverás a la pantalla de inicio de sesión.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar sesión") { _, _ -> cerrarSesion() }
            .show()
    }

    private fun cerrarSesion() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }
}