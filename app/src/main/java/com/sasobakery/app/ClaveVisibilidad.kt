package com.sasobakery.app

import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.TextView

/**
 * Muestra u oculta la contraseña de este campo y cambia el texto del botón
 * ("Mostrar" / "Ocultar"). Se usa en la recuperación de contraseña.
 */
fun EditText.alternarVisibilidadClave(boton: TextView) {
    val estabaOculta = transformationMethod is PasswordTransformationMethod
    transformationMethod =
        if (estabaOculta) HideReturnsTransformationMethod.getInstance()
        else PasswordTransformationMethod.getInstance()
    setSelection(text.length)
    boton.text = if (estabaOculta) "Ocultar" else "Mostrar"
}
