package com.sasobakery.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

enum class EstadoBiometria {
    DISPONIBLE,
    SIN_SENSOR,
    SIN_HUELLA_REGISTRADA,
    NO_DISPONIBLE
}

fun enmascararCorreo(correo: String): String {
    val arroba = correo.indexOf('@')
    if (arroba <= 0) return correo
    return correo.take(minOf(2, arroba)) + "***" + correo.substring(arroba)
}
object BiometriaHelper {

    private const val AUTENTICADORES = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun estado(contexto: Context): EstadoBiometria =
        when (BiometricManager.from(contexto).canAuthenticate(AUTENTICADORES)) {
            BiometricManager.BIOMETRIC_SUCCESS -> EstadoBiometria.DISPONIBLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> EstadoBiometria.SIN_SENSOR
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> EstadoBiometria.SIN_HUELLA_REGISTRADA
            else -> EstadoBiometria.NO_DISPONIBLE
        }

    fun abrirAjustesDeRegistro(actividad: Activity) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, AUTENTICADORES
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        try {
            actividad.startActivity(intent)
        } catch (e: Exception) {
            avisar(actividad, "Abre los Ajustes del celular y registra tu huella en Seguridad.")
        }
    }

    fun activar(actividad: FragmentActivity, correo: String, alTerminar: (Boolean) -> Unit) {
        val cipher = BiometriaRepositorio.cifradoParaActivar()
        if (cipher == null) {
            avisar(actividad, "No pudimos preparar la huella en este celular.")
            alTerminar(false)
            return
        }

        pedirHuella(
            actividad = actividad,
            titulo = "Activar acceso con huella",
            subtitulo = "Toca el sensor para confirmar que eres tú",
            textoCancelar = "Cancelar",
            cipher = cipher,
            alAutenticar = { cipherListo ->
                if (BiometriaRepositorio.guardar(actividad, correo, cipherListo)) {
                    alTerminar(true)
                } else {
                    avisar(actividad, "No pudimos guardar el acceso con huella.")
                    alTerminar(false)
                }
            },
            alTerminarSinExito = { mensaje ->
                if (mensaje != null) avisar(actividad, mensaje)
                alTerminar(false)
            }
        )
    }

    fun entrar(actividad: FragmentActivity, alEntrar: (String) -> Unit, alFallar: () -> Unit) {
        val cipher = BiometriaRepositorio.cifradoParaEntrar(actividad)
        if (cipher == null) {
            avisar(
                actividad,
                "Tu huella cambio o ya no esta disponible. Inicia sesión con tu contraseña y vuelve a activarla."
            )
            alFallar()
            return
        }

        pedirHuella(
            actividad = actividad,
            titulo = "Entrar a Saso Bakery",
            subtitulo = "Toca el sensor para continuar",
            textoCancelar = "Usar contraseña",
            cipher = cipher,
            alAutenticar = { cipherListo ->
                val token = BiometriaRepositorio.leerToken(actividad, cipherListo)
                val correo = BiometriaRepositorio.correoActivado(actividad)
                if (token == null || correo == null) {
                    BiometriaRepositorio.desactivar(actividad)
                    avisar(actividad, "No pudimos verificar tu acceso. Inicia sesión con tu contraseña.")
                    alFallar()
                } else {
                    alEntrar(correo)
                }
            },
            alTerminarSinExito = { mensaje ->
                if (mensaje != null) avisar(actividad, mensaje)
                alFallar()
            }
        )
    }

    private fun pedirHuella(
        actividad: FragmentActivity,
        titulo: String,
        subtitulo: String,
        textoCancelar: String,
        cipher: Cipher,
        alAutenticar: (Cipher) -> Unit,
        alTerminarSinExito: (String?) -> Unit
    ) {
        val respuesta = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val cipherListo = result.cryptoObject?.cipher
                if (cipherListo == null) {
                    alTerminarSinExito("No pudimos verificar tu huella. Intenta de nuevo.")
                } else {
                    alAutenticar(cipherListo)
                }
            }

            override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                val aviso = when (codigo) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> null
                    BiometricPrompt.ERROR_LOCKOUT ->
                        "Demasiados intentos. Espera unos segundos o usa tu contraseña."
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                        "La huella quedo bloqueada. Desbloquea el celular con tu PIN o usa tu contraseña."
                    else -> mensaje.toString()
                }
                alTerminarSinExito(aviso)
            }
        }

        val prompt = BiometricPrompt(actividad, ContextCompat.getMainExecutor(actividad), respuesta)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle(subtitulo)
            .setNegativeButtonText(textoCancelar)
            .setAllowedAuthenticators(AUTENTICADORES)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun avisar(contexto: Context, texto: String) {
        Toast.makeText(contexto, texto, Toast.LENGTH_LONG).show()
    }
}
