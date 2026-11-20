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

// como esta la huella en el celular
enum class EstadoBiometria {
    DISPONIBLE,
    SIN_SENSOR,
    SIN_HUELLA_REGISTRADA,
    NO_DISPONIBLE
}

// oculta parte del correo para mostrarlo
fun enmascararCorreo(correo: String): String {
    val arroba = correo.indexOf('@')
    if (arroba <= 0) return correo
    return correo.take(minOf(2, arroba)) + "***" + correo.substring(arroba)
}
// maneja el dialogo de huella, la parte de guardar claves esta en BiometriaRepositorio
object BiometriaHelper {
    // solo huella segura, sin patron ni pin
    private const val AUTENTICADORES = BiometricManager.Authenticators.BIOMETRIC_STRONG

    // revisa si el celular tiene sensor y huella registrada
    fun estado(contexto: Context): EstadoBiometria =
        when (BiometricManager.from(contexto).canAuthenticate(AUTENTICADORES)) {
            BiometricManager.BIOMETRIC_SUCCESS -> EstadoBiometria.DISPONIBLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> EstadoBiometria.SIN_SENSOR
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> EstadoBiometria.SIN_HUELLA_REGISTRADA
            else -> EstadoBiometria.NO_DISPONIBLE
        }

    // lleva a ajustes para registrar la huella, en android viejo abre seguridad
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

    // pide la huella y guarda el correo y la clave cifrados
    fun activar(
        actividad: FragmentActivity,
        correo: String,
        clave: String,
        alTerminar: (Boolean) -> Unit
    ) {
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
                if (BiometriaRepositorio.guardar(actividad, correo, clave, cipherListo)) {
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

    // pide la huella y devuelve correo y clave para iniciar sesion
    fun entrar(
        actividad: FragmentActivity,
        alEntrar: (String, String) -> Unit,
        alFallar: () -> Unit
    ) {
        val cipher = BiometriaRepositorio.cifradoParaEntrar(actividad)
        if (cipher == null) {
            avisar(
                actividad,
                "Tu huella cambió o ya no está disponible. Inicia sesión con tu contraseña y vuelve a activarla."
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
                val clave = BiometriaRepositorio.leerClave(actividad, cipherListo)
                val correo = BiometriaRepositorio.correoActivado(actividad)
                if (clave == null || correo == null) {
                    BiometriaRepositorio.desactivar(actividad)
                    avisar(actividad, "No pudimos verificar tu acceso. Inicia sesión con tu contraseña.")
                    alFallar()
                } else {
                    alEntrar(correo, clave)
                }
            },
            alTerminarSinExito = { mensaje ->
                if (mensaje != null) avisar(actividad, mensaje)
                alFallar()
            }
        )
    }

    // muestra el dialogo de huella, lo usan activar y entrar
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

            // si cancela no se avisa nada, los demas errores si
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

    // toast corto para los mensajes
    private fun avisar(contexto: Context, texto: String) {
        Toast.makeText(contexto, texto, Toast.LENGTH_LONG).show()
    }
}