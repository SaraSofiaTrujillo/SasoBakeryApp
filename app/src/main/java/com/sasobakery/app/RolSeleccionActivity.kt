package com.sasobakery.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class RolSeleccionActivity : AppCompatActivity() {

    // Pantalla principal de cada rol.
    // "null" significa que esa pantalla todavía no existe.
    private val destinoAdministrador: Class<*>? = MainActivity::class.java
    private val destinoVendedor: Class<*>? = VendedorPrincipalActivity::class.java
    private val destinoComprador: Class<*>? = CatalogoCompradorActivity::class.java

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_rol_seleccion)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        findViewById<View>(R.id.cardAdmin).setOnClickListener {
            abrir(destinoAdministrador, "administrador")
        }
        findViewById<View>(R.id.cardVendedor).setOnClickListener {
            abrir(destinoVendedor, "vendedor")
        }
        findViewById<View>(R.id.cardComprador).setOnClickListener {
            abrir(destinoComprador, "comprador")
        }
    }

    private fun abrir(destino: Class<*>?, rol: String) {
        if (destino == null) {
            Toast.makeText(
                this,
                "La pantalla principal del $rol está en construcción",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            // Sin finish(): el botón Atrás vuelve aquí para probar otro rol
            startActivity(Intent(this, destino))
        }
    }
}