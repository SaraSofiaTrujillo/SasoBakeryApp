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

// perfil del vendedor, datos personales y de la tienda
class PerfilVendedorActivity : AppCompatActivity() {
    // escucha de los datos del negocio, se activa en onStart y se quita en onStop
    private var registroNegocio: ListenerRegistration? = null

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

    // al volver se recarga el perfil de la cuenta
    override fun onResume() {
        super.onResume()
        mostrarPerfil()
        PerfilVendedorRepositorio.cargarDeSesion {
            if (!isFinishing && !isDestroyed) mostrarPerfil()
        }
    }

    // si el admin cambia los datos del negocio se actualizan aqui
    override fun onStart() {
        super.onStart()
        registroNegocio = NegocioRepositorio.escuchar(
            alCambiar = { mostrarPerfil() },
            alFallar = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        )
    }

    override fun onStop() {
        super.onStop()
        registroNegocio?.remove()
        registroNegocio = null
    }

    // pinta cabecera y datos
    private fun mostrarPerfil() {
        val p = PerfilVendedorRepositorio.obtener()

        findViewById<TextView>(R.id.tvIniciales).text = p.iniciales
        findViewById<TextView>(R.id.tvNombreCabecera).text = p.nombre
        findViewById<TextView>(R.id.tvCorreoCabecera).text = p.correo

        findViewById<TextView>(R.id.tvDatoNombre).text = p.nombre
        findViewById<TextView>(R.id.tvDatoCorreo).text = p.correo
        findViewById<TextView>(R.id.tvDatoTelefono).text =
            if (p.telefono.isBlank()) "Sin registrar" else Telefono.mostrar(p.telefono)

        findViewById<TextView>(R.id.tvDatoNegocio).text = p.negocio
        findViewById<TextView>(R.id.tvDatoDireccion).text = p.direccion
        findViewById<TextView>(R.id.tvDatoHorario).text = p.horario
    }

    // hoja para cambiar nombre y telefono
    private fun mostrarEditar() {
        val actual = PerfilVendedorRepositorio.obtener()
        val dialogo = BottomSheetDialog(this)
        val vista = layoutInflater.inflate(R.layout.dialog_editar_perfil, null)

        val etNombre = vista.findViewById<EditText>(R.id.etPerfilNombre)
        val etTelefono = vista.findViewById<EditText>(R.id.etPerfilTelefono)
        val etNegocio = vista.findViewById<EditText>(R.id.etPerfilNegocio)
        val etDireccion = vista.findViewById<EditText>(R.id.etPerfilDireccion)
        val etHorario = vista.findViewById<EditText>(R.id.etPerfilHorario)
        val btnGuardar = vista.findViewById<TextView>(R.id.btnGuardarEdicion)

        etNombre.setText(actual.nombre)
        vista.findViewById<TextView>(R.id.tvPerfilCorreo).text = actual.correo
        etTelefono.setText(Telefono.mostrar(actual.telefono))
        etNegocio.setText(actual.negocio)
        etDireccion.setText(actual.direccion)
        etHorario.setText(actual.horario)

        // los datos del negocio solo los edita el admin
        listOf(etNegocio, etDireccion, etHorario).forEach {
            it.isEnabled = false
            it.alpha = 0.6f
        }

        vista.findViewById<View>(R.id.btnCancelarEdicion).setOnClickListener {
            dialogo.dismiss()
        }

        // valida y guarda, el boton se bloquea mientras tanto
        btnGuardar.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val telefono = Telefono.normalizar(etTelefono.text.toString())

            if (nombre.length < 2) {
                etNombre.error = "Escribe tu nombre"
                etNombre.requestFocus()
                return@setOnClickListener
            }
            if (telefono == null) {
                etTelefono.error = "Escribe un teléfono válido (10 dígitos)"
                etTelefono.requestFocus()
                return@setOnClickListener
            }

            btnGuardar.isEnabled = false
            btnGuardar.text = "Guardando…"
            PerfilVendedorRepositorio.guardar(
                actual.copy(nombre = nombre, telefono = telefono)
            ) { error ->
                if (isFinishing || isDestroyed) return@guardar
                if (error != null) {
                    btnGuardar.isEnabled = true
                    btnGuardar.text = "Guardar"
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                } else {
                    mostrarPerfil()
                    dialogo.dismiss()
                    Toast.makeText(this, "Información actualizada", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialogo.setContentView(vista)
        dialogo.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialogo.behavior.skipCollapsed = true
        dialogo.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialogo.show()
    }

    // vuelve al resumen que ya estaba abierto
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

    // borra la sesion y deja el login sin pantallas atras
    private fun cerrarSesion() {
        AuthRepositorio.cerrarSesion()
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }
}