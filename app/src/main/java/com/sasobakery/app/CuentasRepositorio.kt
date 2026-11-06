package com.sasobakery.app

// Cuentas de prueba y reglas de contraseña compartidas por el inicio de sesión
// y la recuperación de contraseña (así no se repiten en dos pantallas).
// BACKEND: cuando exista el servicio de autenticación, esta clase se reemplaza
// por las consultas reales de cuentas.
object CuentasRepositorio {

    const val LARGO_MINIMO_CLAVE = 6

    // Correo -> pantalla principal del rol (mismas cuentas de prueba del login)
    private val cuentasBase: Map<String, Class<*>> = mapOf(
        "valentina@email.com" to CatalogoCompradorActivity::class.java,
        "valentina.mendoza@gmail.com" to CatalogoCompradorActivity::class.java,
        "camila.rios@sasobakery.pe" to VendedorPrincipalActivity::class.java,
        "andrea.salas@sasobakery.pe" to MainActivity::class.java
    )

    // Solo las cuentas que el administrador no desactivó ni eliminó
    val cuentasDePrueba: Map<String, Class<*>>
        get() = cuentasBase.filterKeys { !UsuariosRepositorio.estaBloqueado(it) }

    /** true si el correo pertenece a una cuenta de prueba vigente. */
    fun existe(correo: String): Boolean =
        cuentasDePrueba.containsKey(correo.trim().lowercase())
}