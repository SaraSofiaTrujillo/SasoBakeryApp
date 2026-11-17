package com.sasobakery.app

/**
 * Ayudas para teléfonos personales. Se guardan "limpios" (solo dígitos y un + opcional
 * al inicio) y se muestran con espacios.
 */
object Telefono {

    /**
     * Limpia lo que escribió la persona ("+57 300 123-4567" -> "+573001234567").
     * Devuelve null si no es un teléfono válido (de 10 a 13 dígitos).
     */
    fun normalizar(texto: String): String? {
        val digitos = texto.filter { it.isDigit() }
        if (digitos.length !in 10..13) return null
        return if (texto.trim().startsWith("+")) "+$digitos" else digitos
    }

    /** "3001234567" -> "300 123 4567"; "+573001234567" -> "+57 300 123 4567". */
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