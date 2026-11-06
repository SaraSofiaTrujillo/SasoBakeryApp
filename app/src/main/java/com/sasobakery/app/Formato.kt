package com.sasobakery.app

import java.text.Normalizer
import java.util.Locale

fun formatearCop(valor: Int): String =
    "\$ " + String.format(Locale.US, "%,d", valor).replace(",", ".")

fun normalizarTexto(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase()
        .trim()