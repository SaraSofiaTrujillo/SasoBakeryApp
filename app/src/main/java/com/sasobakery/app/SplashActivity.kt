package com.sasobakery.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

// Pantalla de bienvenida: se ve unos segundos y pasa al inicio de sesión.
class SplashActivity : AppCompatActivity() {

    // Cuánto tiempo se ve (en milisegundos). Cámbialo si lo quieres más corto o más largo.
    private val duracionSplash = 2000L

    private val manejador = Handler(Looper.getMainLooper())
    private val irAlLogin = Runnable { abrirLogin() }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Debe ir ANTES de super.onCreate: pasa del tema de arranque al tema normal
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Íconos de la barra de estado oscuros, porque el fondo es lila claro
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_splash)

        animar()
        manejador.postDelayed(irAlLogin, duracionSplash)
    }

    private fun animar() {
        val logo = findViewById<View>(R.id.ivSplashLogo)
        val textos = listOf<View>(
            findViewById(R.id.tvSaso),
            findViewById(R.id.tvBakery),
            findViewById(R.id.tvLema)
        )
        val circulos = listOf<View>(
            findViewById(R.id.circuloRosa),
            findViewById(R.id.circuloLila),
            findViewById(R.id.circuloBlanco)
        )
        val dp = resources.displayMetrics.density

        // Punto de partida: textos invisibles y un poco más abajo; círculos pequeños
        textos.forEach {
            it.alpha = 0f
            it.translationY = 14 * dp
        }
        circulos.forEach {
            it.alpha = 0f
            it.scaleX = 0.7f
            it.scaleY = 0.7f
        }

        // Los círculos aparecen creciendo
        circulos.forEach {
            it.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).start()
        }

        // El logo da un pequeño salto
        logo.animate()
            .scaleX(1.08f).scaleY(1.08f)
            .setDuration(350).setStartDelay(150)
            .withEndAction {
                logo.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
            }
            .start()

        // Los textos suben y aparecen uno tras otro
        textos.forEachIndexed { indice, vista ->
            vista.animate()
                .alpha(1f).translationY(0f)
                .setDuration(500).setStartDelay(350L + indice * 150L)
                .start()
        }
    }

    @Suppress("DEPRECATION")
    private fun abrirLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        // Fundido suave hacia el login
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        // Se cierra para que "Atrás" desde el login salga de la app y no vuelva al splash
        finish()
    }

    override fun onDestroy() {
        manejador.removeCallbacks(irAlLogin)
        super.onDestroy()
    }
}