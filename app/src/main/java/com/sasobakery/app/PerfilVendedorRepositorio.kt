package com.sasobakery.app

// Datos del vendedor y de su negocio
data class PerfilVendedor(
    val nombre: String,
    val correo: String,
    val telefono: String,
    val negocio: String,
    val direccion: String,
    val horario: String
) {
    // "Camila Ríos" -> "CR" (para el avatar)
    val iniciales: String
        get() = nombre.trim()
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
}

// Perfil compartido entre pantallas (solo en memoria).
// Cuando exista la base de datos, esta clase es la que se reemplaza.
object PerfilVendedorRepositorio {

    // Datos de ejemplo (el correo coincide con el de la lista de usuarios del administrador)
    private var perfil = PerfilVendedor(
        nombre = "Camila Ríos",
        correo = "camila.rios@sasobakery.pe",
        telefono = "+57 300 123 4567",
        negocio = "Saso Bakery",
        direccion = "Cra 7 # 45-20, Bogotá",
        horario = "Lun - Sáb · 8:00 a. m. - 6:00 p. m."
    )

    fun obtener(): PerfilVendedor = perfil

    fun guardar(nuevo: PerfilVendedor) {
        perfil = nuevo
    }
}