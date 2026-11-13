package com.sasobakery.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Guarda y protege el "acceso con huella" en ESTE celular.
//
// Cómo funciona (esta parte la hace Android y no necesita servidor):
//  1. Se crea una llave secreta dentro del Android Keystore (chip seguro del celular).
//     Esa llave SOLO se puede usar después de que el usuario pone su huella.
//  2. Con esa llave se cifra la contraseña de la cuenta y se guarda ya cifrada.
//  3. Para entrar con huella, Android pide la huella; si es correcta, libera la llave,
//     se descifra la contraseña y la app la usa para iniciar sesión en Firebase.
//  Si el usuario registra una huella nueva en el celular, Android invalida la llave
//  (así nadie puede agregar su huella y entrar a tu cuenta). Si la contraseña de la
//  cuenta cambia, la huella se desactiva sola y hay que activarla de nuevo.
object BiometriaRepositorio {

    private const val ARCHIVO = "saso_biometria"
    private const val CLAVE_CORREO = "correo"
    private const val CLAVE_TOKEN = "token_cifrado"
    private const val CLAVE_IV = "iv"

    private const val ALMACEN = "AndroidKeyStore"
    private const val ALIAS_LLAVE = "saso_llave_biometrica"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"
    private const val BITS_ETIQUETA = 128

    private fun prefs(contexto: Context) =
        contexto.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    fun correoActivado(contexto: Context): String? {
        val p = prefs(contexto)
        return if (p.contains(CLAVE_TOKEN)) p.getString(CLAVE_CORREO, null) else null
    }
    fun estaActivada(contexto: Context, correo: String): Boolean =
        correoActivado(contexto) == correo.trim().lowercase()

    fun cifradoParaActivar(): Cipher? = try {
        borrarLlave()
        val llave = crearLlave()
        Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, llave) }
    } catch (e: Exception) {
        null
    }
    /**
     * Con el Cipher ya desbloqueado por la huella, cifra la contraseña de la cuenta
     * y la guarda. Solo se puede descifrar de nuevo con la huella del dueño del celular.
     */
    fun guardar(contexto: Context, correo: String, clave: String, cipher: Cipher): Boolean = try {
        val cifrado = cipher.doFinal(clave.toByteArray(Charsets.UTF_8))
        prefs(contexto).edit()
            .putString(CLAVE_CORREO, correo.trim().lowercase())
            .putString(CLAVE_TOKEN, aTexto(cifrado))
            .putString(CLAVE_IV, aTexto(cipher.iv))
            .apply()
        true
    } catch (e: Exception) {
        false
    }

    fun cifradoParaEntrar(contexto: Context): Cipher? {
        return try {
            val p = prefs(contexto)
            val iv = p.getString(CLAVE_IV, null)
            if (iv == null || !p.contains(CLAVE_TOKEN)) return null
            val llave = obtenerLlave()
            if (llave == null) {
                desactivar(contexto)
                return null
            }
            Cipher.getInstance(TRANSFORMACION).apply {
                init(Cipher.DECRYPT_MODE, llave, GCMParameterSpec(BITS_ETIQUETA, deTexto(iv)))
            }
        } catch (e: Exception) {
            desactivar(contexto)
            null
        }
    }

    /** Con el Cipher ya desbloqueado por la huella, devuelve la contraseña guardada. */
    fun leerClave(contexto: Context, cipher: Cipher): String? = try {
        val guardado = prefs(contexto).getString(CLAVE_TOKEN, null)
        if (guardado == null) null
        else String(cipher.doFinal(deTexto(guardado)), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    fun desactivar(contexto: Context) {
        prefs(contexto).edit().clear().apply()
        borrarLlave()
    }

    private fun crearLlave(): SecretKey {
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ALMACEN)
        generador.init(
            KeyGenParameterSpec.Builder(
                ALIAS_LLAVE,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return generador.generateKey()
    }

    private fun obtenerLlave(): SecretKey? {
        val almacen = KeyStore.getInstance(ALMACEN).apply { load(null) }
        return almacen.getKey(ALIAS_LLAVE, null) as? SecretKey
    }

    private fun borrarLlave() {
        try {
            val almacen = KeyStore.getInstance(ALMACEN).apply { load(null) }
            if (almacen.containsAlias(ALIAS_LLAVE)) almacen.deleteEntry(ALIAS_LLAVE)
        } catch (e: Exception) {
        }
    }

    private fun aTexto(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun deTexto(texto: String): ByteArray = Base64.decode(texto, Base64.NO_WRAP)
}
