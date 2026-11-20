package com.sasobakery.app

import android.app.Application

// clase de la aplicacion, se crea antes que cualquier pantalla
class SasoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // activa el guardia que revisa el acceso en cada pantalla
        registerActivityLifecycleCallbacks(GuardiaAcceso())
    }
}