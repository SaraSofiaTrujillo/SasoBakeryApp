package com.sasobakery.app

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        findViewById<android.view.View>(R.id.navMas).setOnClickListener {
            startActivity(android.content.Intent(this, UsuariosRolesActivity::class.java))
        }
        findViewById<android.view.View>(R.id.navProductos).setOnClickListener {
            startActivity(android.content.Intent(this, ProductosCategoriasActivity::class.java))
        }
        findViewById<android.view.View>(R.id.navPedidos).setOnClickListener {
            startActivity(android.content.Intent(this, PedidosActivity::class.java))
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}