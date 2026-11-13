package com.sasobakery.app

import android.app.Application

/**
 * Clase que Android crea una sola vez al arrancar la app, antes que cualquier pantalla.
 * Aquí se registra la guardia de acceso para que vigile todas las pantallas.
 */
class SasoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(GuardiaAcceso())
    }
}