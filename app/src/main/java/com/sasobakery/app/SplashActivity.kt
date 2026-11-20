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

// pantalla de inicio con animacion, despues decide a donde ir segun la sesion
class SplashActivity : AppCompatActivity() {
    // milisegundos que se queda antes de seguir
    private val duracionSplash = 2000L

    private val manejador = Handler(Looper.getMainLooper())
    // tarea programada, se cancela en onDestroy
    private val irAlLogin = Runnable { abrirLogin() }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_splash)

        animar()
        manejador.postDelayed(irAlLogin, duracionSplash)
    }

    // los textos suben y aparecen uno tras otro, los circulos crecen
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

        textos.forEach {
            it.alpha = 0f
            it.translationY = 14 * dp
        }
        circulos.forEach {
            it.alpha = 0f
            it.scaleX = 0.7f
            it.scaleY = 0.7f
        }

        circulos.forEach {
            it.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).start()
        }

        logo.animate()
            .scaleX(1.08f).scaleY(1.08f)
            .setDuration(350).setStartDelay(150)
            .withEndAction {
                logo.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
            }
            .start()

        textos.forEachIndexed { indice, vista ->
            vista.animate()
                .alpha(1f).translationY(0f)
                .setDuration(500).setStartDelay(350L + indice * 150L)
                .start()
        }
    }

    @Suppress("DEPRECATION")
    // sin sesion va al login, con sesion consulta el rol para abrir su pantalla
    private fun abrirLogin() {
        if (!AuthRepositorio.haySesion()) {
            irA(LoginActivity::class.java)
            return
        }
        AuthRepositorio.rolDeSesionActual { resultado ->
            if (isFinishing || isDestroyed) return@rolDeSesionActual
            val destino = if (resultado is ResultadoAuth.Exito) {
                AuthRepositorio.destinoPara(resultado.rol)
            } else {
                LoginActivity::class.java
            }
            irA(destino)
        }
    }

    @Suppress("DEPRECATION")
    // abre la pantalla con fundido y cierra el splash
    private fun irA(destino: Class<*>) {
        startActivity(Intent(this, destino))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    // evita que la tarea programada corra si ya se cerro
    override fun onDestroy() {
        manejador.removeCallbacks(irAlLogin)
        super.onDestroy()
    }
}