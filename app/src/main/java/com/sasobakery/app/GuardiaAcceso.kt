package com.sasobakery.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

// revisa cada pantalla que se abre, sin sesion va al login y de otro rol va a la suya
class GuardiaAcceso : Application.ActivityLifecycleCallbacks {
    // pantallas de cada rol, las que no estan aqui son publicas (login, registro)
    private val pantallasAdmin = setOf<Class<*>>(
        MainActivity::class.java,
        PedidosActivity::class.java,
        CategoriasActivity::class.java,
        ProductosCategoriasActivity::class.java,
        UsuariosRolesActivity::class.java,
        UsuarioFormularioActivity::class.java
    )

    private val pantallasVendedor = setOf<Class<*>>(
        VendedorPrincipalActivity::class.java,
        ProductosInventarioActivity::class.java,
        PedidosVendedorActivity::class.java,
        PerfilVendedorActivity::class.java
    )

    // las que pueden abrir vendedor y admin
    private val pantallasEquipo = setOf<Class<*>>(
        ProductoFormularioActivity::class.java
    )

    private val pantallasComprador = setOf<Class<*>>(
        CatalogoCompradorActivity::class.java,
        ProductoDetalleActivity::class.java,
        CarritoCompradorActivity::class.java,
        EntregaCompradorActivity::class.java,
        PagoCompradorActivity::class.java,
        PedidoConfirmadoActivity::class.java,
        PerfilCompradorActivity::class.java,
        MisPedidosCompradorActivity::class.java,
        FavoritosCompradorActivity::class.java
    )

    // que roles pueden abrir cada pantalla, null si es publica
    private fun rolesPermitidos(pantalla: Class<*>): Set<Rol>? = when (pantalla) {
        in pantallasEquipo -> setOf(Rol.ADMINISTRADOR, Rol.VENDEDOR)
        in pantallasAdmin -> setOf(Rol.ADMINISTRADOR)
        in pantallasVendedor -> setOf(Rol.VENDEDOR)
        in pantallasComprador -> setOf(Rol.COMPRADOR)
        else -> null
    }

    // se ejecuta cada vez que se abre una pantalla
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        val permitidos = rolesPermitidos(activity.javaClass) ?: return

        // sin sesion va al login
        if (!AuthRepositorio.haySesion()) {
            irA(activity, LoginActivity::class.java)
            return
        }

        // si ya se sabe el rol solo se compara
        val rolConocido = AuthRepositorio.rolEnCache()
        if (rolConocido != null) {
            if (rolConocido !in permitidos) irA(activity, AuthRepositorio.destinoPara(rolConocido))
            return
        }

        // si la app se reinicio hay que consultar el rol en firestore
        AuthRepositorio.rolDeSesionActual { resultado ->
            if (activity.isFinishing || activity.isDestroyed) return@rolDeSesionActual

            when (resultado) {
                is ResultadoAuth.Exito ->
                    if (resultado.rol !in permitidos) {
                        irA(activity, AuthRepositorio.destinoPara(resultado.rol))
                    }
                // cuenta rechazada vuelve al login, si fue solo la red se queda donde esta
                is ResultadoAuth.Error ->
                    if (!AuthRepositorio.haySesion()) {
                        Toast.makeText(activity, resultado.mensaje, Toast.LENGTH_LONG).show()
                        irA(activity, LoginActivity::class.java)
                    }
            }
        }
    }

    // abre la pantalla y borra el historial para que atras no vuelva
    private fun irA(activity: Activity, destino: Class<*>) {
        val intent = Intent(activity, destino)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finish()
    }

    // estos no se usan pero android obliga a declararlos
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}