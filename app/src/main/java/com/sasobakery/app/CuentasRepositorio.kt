package com.sasobakery.app

object CuentasRepositorio {

    const val LARGO_MINIMO_CLAVE = 6

    private val cuentasBase: Map<String, Class<*>> = mapOf(
        "valentina@email.com" to CatalogoCompradorActivity::class.java,
        "valentina.mendoza@gmail.com" to CatalogoCompradorActivity::class.java,
        "camila.rios@sasobakery.pe" to VendedorPrincipalActivity::class.java,
        "andrea.salas@sasobakery.pe" to MainActivity::class.java
    )

    val cuentasDePrueba: Map<String, Class<*>>
        get() = cuentasBase.filterKeys { !UsuariosRepositorio.estaBloqueado(it) }

    fun existe(correo: String): Boolean =
        cuentasDePrueba.containsKey(correo.trim().lowercase())
}