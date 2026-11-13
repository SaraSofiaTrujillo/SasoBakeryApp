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

class SplashActivity : AppCompatActivity() {

    private val duracionSplash = 2000L

    private val manejador = Handler(Looper.getMainLooper())
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
    /**
     * Se ejecuta al terminar el splash. Si hay una sesión guardada, consulta el rol
     * y abre su pantalla principal; si no hay sesión (o la cuenta ya no es válida),
     * abre el login.
     */
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
    private fun irA(destino: Class<*>) {
        startActivity(Intent(this, destino))
        // Fundido suave hacia la siguiente pantalla
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        // Se cierra para que "Atrás" salga de la app y no vuelva al splash
        finish()
    }

    override fun onDestroy() {
        manejador.removeCallbacks(irAlLogin)
        super.onDestroy()
    }
}