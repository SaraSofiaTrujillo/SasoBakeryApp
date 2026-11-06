package com.sasobakery.app

// Lista de usuarios compartida entre pantallas (solo en memoria).
// Cuando exista la base de datos, esta clase es la que se reemplaza.
object UsuariosRepositorio {

    // Usuarios de prueba (los mismos que ya se veían en la pantalla)
    private val usuarios = mutableListOf(
        Usuario("VM", "Valentina Mendoza", "valentina.mendoza@gmail.com", Rol.COMPRADOR, id = 1),
        Usuario("CR", "Camila Ríos", "camila.rios@sasobakery.pe", Rol.VENDEDOR, id = 2),
        Usuario("AS", "Andrea Salas", "andrea.salas@sasobakery.pe", Rol.ADMINISTRADOR, id = 3),
        Usuario("MF", "María Flores", "maria.flores@gmail.com", Rol.COMPRADOR, id = 4),
        Usuario("DR", "Diego Rojas", "diego.rojas@gmail.com", Rol.COMPRADOR, id = 5)
    )

    // Correos de cuentas eliminadas (o cuyo correo cambió): ya no pueden iniciar sesión
    private val correosEliminados = mutableSetOf<String>()

    // El próximo usuario nuevo recibe el id 6, luego 7, etc.
    private var siguienteId = 6

    /** Copia de la lista actual (así nadie la modifica por accidente). */
    fun obtenerTodos(): List<Usuario> = usuarios.toList()

    fun obtenerPorId(id: Int): Usuario? = usuarios.firstOrNull { it.id == id }

    /**
     * true si ya hay un usuario con ese correo (sin importar mayúsculas).
     * Al editar, pasa el id del usuario para que su propio correo no cuente como repetido.
     */
    fun existeCorreo(correo: String, excluirId: Int? = null): Boolean =
        usuarios.any {
            it.id != excluirId && it.correo.equals(correo.trim(), ignoreCase = true)
        }

    /** Registra un usuario nuevo; aparece primero en la lista. */
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

    /** Guarda los cambios de un usuario existente. Devuelve null si ya no existe. */
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

    /** Activa o desactiva la cuenta. Devuelve null si el usuario ya no existe. */
    fun cambiarActivo(id: Int, activo: Boolean): Usuario? {
        val indice = usuarios.indexOfFirst { it.id == id }
        if (indice < 0) return null
        val cambiado = usuarios[indice].copy(activo = activo)
        usuarios[indice] = cambiado
        return cambiado
    }

    /** Elimina al usuario. Devuelve true si existía. */
    fun eliminar(id: Int): Boolean {
        val usuario = obtenerPorId(id) ?: return false
        usuarios.remove(usuario)
        correosEliminados.add(usuario.correo.lowercase())
        return true
    }

    /** true si este usuario es el único administrador activo (no se puede quitar). */
    fun esUltimoAdminActivo(usuario: Usuario): Boolean =
        usuario.rol == Rol.ADMINISTRADOR && usuario.activo &&
                usuarios.count { it.rol == Rol.ADMINISTRADOR && it.activo } <= 1

    /** true si el correo no debe poder iniciar sesión (desactivado o eliminado). */
    fun estaBloqueado(correo: String): Boolean {
        val limpio = correo.trim().lowercase()
        val actual = usuarios.firstOrNull { it.correo.equals(limpio, ignoreCase = true) }
        return if (actual != null) !actual.activo else limpio in correosEliminados
    }

    /** "Ana María Gómez" -> "AG"; "Ana" -> "AN". */
    fun calcularIniciales(nombre: String): String {
        val palabras = nombre.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            palabras.isEmpty() -> "?"
            palabras.size == 1 -> palabras[0].take(2).uppercase()
            else -> "${palabras.first().first()}${palabras.last().first()}".uppercase()
        }
    }
}