package com.sasobakery.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Guardia de acceso: la app la registra una sola vez (ver SasoApp) y se ejecuta cada
 * vez que se abre una pantalla. Hace dos comprobaciones:
 *  1) Si la pantalla es privada y no hay sesión iniciada, manda al login.
 *  2) Si la pantalla es de otro rol, manda a la pantalla principal del rol del usuario.
 * Así ningún usuario puede ver pantallas que no le corresponden, aunque Android
 * restaure la app en una pantalla interna.
 */
class GuardiaAcceso : Application.ActivityLifecycleCallbacks {

    // Pantallas de cada rol. Las que no están en estas listas (splash, login, registro
    // y recuperación de contraseña) son públicas y no se vigilan.
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
        ProductoFormularioActivity::class.java,
        PedidosVendedorActivity::class.java,
        PerfilVendedorActivity::class.java
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

    /** Rol que se necesita para abrir esta pantalla (null si es pública). */
    private fun rolRequerido(pantalla: Class<*>): Rol? = when (pantalla) {
        in pantallasAdmin -> Rol.ADMINISTRADOR
        in pantallasVendedor -> Rol.VENDEDOR
        in pantallasComprador -> Rol.COMPRADOR
        else -> null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        val requerido = rolRequerido(activity.javaClass) ?: return

        // 1) Pantalla privada sin sesión: al login
        if (!AuthRepositorio.haySesion()) {
            irA(activity, LoginActivity::class.java)
            return
        }

        // 2) Si el rol ya está en memoria (caso normal), se compara directamente
        val rolConocido = AuthRepositorio.rolEnCache()
        if (rolConocido != null) {
            if (rolConocido != requerido) irA(activity, AuthRepositorio.destinoPara(rolConocido))
            return
        }

        // 3) La app se reinició y Android abrió esta pantalla directamente: se consulta
        //    el rol (y si la cuenta sigue activa) en Firestore antes de dejar al usuario aquí
        AuthRepositorio.rolDeSesionActual { resultado ->
            if (activity.isFinishing || activity.isDestroyed) return@rolDeSesionActual

            when (resultado) {
                is ResultadoAuth.Exito ->
                    if (resultado.rol != requerido) {
                        irA(activity, AuthRepositorio.destinoPara(resultado.rol))
                    }
                is ResultadoAuth.Error ->
                    // Si la cuenta fue rechazada (desactivada o sin perfil) la sesión ya
                    // se cerró. Si fue solo un problema de red, se deja al usuario donde está.
                    if (!AuthRepositorio.haySesion()) {
                        Toast.makeText(activity, resultado.mensaje, Toast.LENGTH_LONG).show()
                        irA(activity, LoginActivity::class.java)
                    }
            }
        }
    }

    // Abre la pantalla indicada y borra el historial, para que Atrás no vuelva a la anterior
    private fun irA(activity: Activity, destino: Class<*>) {
        val intent = Intent(activity, destino)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finish()
    }

    // Los demás eventos del ciclo de vida no se usan, pero Android obliga a declararlos
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}