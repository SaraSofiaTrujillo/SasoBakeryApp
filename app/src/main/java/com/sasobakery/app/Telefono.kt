package com.sasobakery.app

// ayudas para validar y mostrar telefonos
object Telefono {
    // deja solo digitos, acepta de 10 a 13 y conserva el mas, null si no es valido
    fun normalizar(texto: String): String? {
        val digitos = texto.filter { it.isDigit() }
        if (digitos.length !in 10..13) return null
        return if (texto.trim().startsWith("+")) "+$digitos" else digitos
    }

    // pone espacios para leerlo mejor, 3 3 4
    fun mostrar(valor: String): String {
        val digitos = valor.filter { it.isDigit() }
        return when {
            digitos.length == 10 ->
                "${digitos.substring(0, 3)} ${digitos.substring(3, 6)} ${digitos.substring(6)}"
            valor.startsWith("+") && digitos.length == 12 && digitos.startsWith("57") ->
                "+57 ${digitos.substring(2, 5)} ${digitos.substring(5, 8)} ${digitos.substring(8)}"
            else -> valor
        }
    }
}