package com.sasobakery.app

object UsuariosRepositorio {

    private val usuarios = mutableListOf(
        Usuario("VM", "Valentina Mendoza", "valentina.mendoza@gmail.com", Rol.COMPRADOR, id = 1),
        Usuario("CR", "Camila Ríos", "camila.rios@sasobakery.pe", Rol.VENDEDOR, id = 2),
        Usuario("AS", "Andrea Salas", "andrea.salas@sasobakery.pe", Rol.ADMINISTRADOR, id = 3),
        Usuario("MF", "María Flores", "maria.flores@gmail.com", Rol.COMPRADOR, id = 4),
        Usuario("DR", "Diego Rojas", "diego.rojas@gmail.com", Rol.COMPRADOR, id = 5)
    )

    private val correosEliminados = mutableSetOf<String>()

    private var siguienteId = 6

    fun obtenerTodos(): List<Usuario> = usuarios.toList()

    fun obtenerPorId(id: Int): Usuario? = usuarios.firstOrNull { it.id == id }

    fun existeCorreo(correo: String, excluirId: Int? = null): Boolean =
        usuarios.any {
            it.id != excluirId && it.correo.equals(correo.trim(), ignoreCase = true)
        }

    fun agregar(nombre: String, correo: String, rol: Rol): Usuario {
        val limpio = nombre.trim()
        val nuevo = Usuario(
            iniciales = calcularIniciales(limpio),
            nombre = limpio,
            correo = correo.trim().lowercase(),
            rol = rol,
            id = siguienteId++
        )
        correosEliminados.remove(nuevo.correo)
        usuarios.add(0, nuevo)
        return nuevo
    }

    fun actualizar(id: Int, nombre: String, correo: String, rol: Rol): Usuario? {
        val indice = usuarios.indexOfFirst { it.id == id }
        if (indice < 0) return null

        val anterior = usuarios[indice]
        val limpio = nombre.trim()
        val correoNuevo = correo.trim().lowercase()
        val editado = anterior.copy(
            iniciales = calcularIniciales(limpio),
            nombre = limpio,
            correo = correoNuevo,
            rol = rol
        )
        usuarios[indice] = editado

        if (!anterior.correo.equals(correoNuevo, ignoreCase = true)) {
            correosEliminados.add(anterior.correo.lowercase())
            correosEliminados.remove(correoNuevo)
        }
        return editado
    }

    fun cambiarActivo(id: Int, activo: Boolean): Usuario? {
        val indice = usuarios.indexOfFirst { it.id == id }
        if (indice < 0) return null
        val cambiado = usuarios[indice].copy(activo = activo)
        usuarios[indice] = cambiado
        return cambiado
    }

    fun eliminar(id: Int): Boolean {
        val usuario = obtenerPorId(id) ?: return false
        usuarios.remove(usuario)
        correosEliminados.add(usuario.correo.lowercase())
        return true
    }

    fun esUltimoAdminActivo(usuario: Usuario): Boolean =
        usuario.rol == Rol.ADMINISTRADOR && usuario.activo &&
                usuarios.count { it.rol == Rol.ADMINISTRADOR && it.activo } <= 1

    fun estaBloqueado(correo: String): Boolean {
        val limpio = correo.trim().lowercase()
        val actual = usuarios.firstOrNull { it.correo.equals(limpio, ignoreCase = true) }
        return if (actual != null) !actual.activo else limpio in correosEliminados
    }

    fun calcularIniciales(nombre: String): String {
        val palabras = nombre.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            palabras.isEmpty() -> "?"
            palabras.size == 1 -> palabras[0].take(2).uppercase()
            else -> "${palabras.first().first()}${palabras.last().first()}".uppercase()
        }
    }
}