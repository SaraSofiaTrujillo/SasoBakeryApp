package com.sasobakery.app

import java.text.Normalizer
import java.util.Locale

/** 68000 -> "$ 68.000" (pesos colombianos, sin decimales). */
fun formatearCop(valor: Int): String =
    "\$ " + String.format(Locale.US, "%,d", valor).replace(",", ".")

/** "Tiramisú" -> "tiramisu": sirve para buscar sin importar mayúsculas ni tildes. */
fun normalizarTexto(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase()
        .trim()