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
// Cómo funciona (esta parte es real, la hace Android y no necesita backend):
//  1. Se crea una llave secreta dentro del Android Keystore (chip seguro del celular).
//     Esa llave SOLO se puede usar después de que el usuario pone su huella.
//  2. Con esa llave se cifra un "token de sesión" y se guarda ya cifrado.
//  3. Para entrar con huella, Android pide la huella; si es correcta, libera la llave,
//     se descifra el token y se abre la app.
//  Si el usuario registra una huella nueva en el celular, Android invalida la llave
//  (así nadie puede agregar su huella y entrar a tu cuenta).
//
// ┌─ BACKEND ─────────────────────────────────────────────────────────────┐
// │ Hoy el token es de mentira (TOKEN_SIMULADO). Con el servicio real, el │
// │ servidor entrega un token de sesión al iniciar sesión con contraseña, │
// │ aquí se guarda cifrado, y al entrar con huella se envía al servidor   │
// │ para que lo valide (y lo pueda revocar).                              │
// └───────────────────────────────────────────────────────────────────────┘
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

    // ---------- Consultas ----------

    /** Correo de la cuenta que tiene la huella activada (null si no hay ninguna). */
    fun correoActivado(contexto: Context): String? {
        val p = prefs(contexto)
        return if (p.contains(CLAVE_TOKEN)) p.getString(CLAVE_CORREO, null) else null
    }

    /** true si esta cuenta ya tiene la huella activada en este celular. */
    fun estaActivada(contexto: Context, correo: String): Boolean =
        correoActivado(contexto) == correo.trim().lowercase()

    // ---------- Activar ----------

    /**
     * Crea una llave nueva y devuelve el Cipher que Android debe "desbloquear" con la huella.
     * Devuelve null si el celular no puede crearla.
     */
    fun cifradoParaActivar(): Cipher? = try {
        borrarLlave()
        val llave = crearLlave()
        Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, llave) }
    } catch (e: Exception) {
        null
    }

    /** Con el Cipher ya desbloqueado por la huella, cifra el token y lo guarda. */
    fun guardar(contexto: Context, correo: String, cipher: Cipher): Boolean = try {
        // BACKEND: aquí va el token real que entrega el servidor al iniciar sesión
        val token = "TOKEN_SIMULADO|${correo.trim().lowercase()}|${System.currentTimeMillis()}"
        val cifrado = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs(contexto).edit()
            .putString(CLAVE_CORREO, correo.trim().lowercase())
            .putString(CLAVE_TOKEN, aTexto(cifrado))
            .putString(CLAVE_IV, aTexto(cipher.iv))
            .apply()
        true
    } catch (e: Exception) {
        false
    }

    // ---------- Entrar ----------

    /**
     * Prepara el Cipher para descifrar. Devuelve null si ya no se puede (por ejemplo,
     * porque se registró una huella nueva en el celular); en ese caso la huella
     * queda desactivada y el comprador debe entrar con su contraseña.
     */
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
            // Incluye KeyPermanentlyInvalidatedException (cambiaron las huellas del celular)
            desactivar(contexto)
            null
        }
    }

    /** Con el Cipher ya desbloqueado por la huella, devuelve el token guardado. */
    fun leerToken(contexto: Context, cipher: Cipher): String? = try {
        val guardado = prefs(contexto).getString(CLAVE_TOKEN, null)
        if (guardado == null) null
        else String(cipher.doFinal(deTexto(guardado)), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    // ---------- Desactivar ----------

    fun desactivar(contexto: Context) {
        prefs(contexto).edit().clear().apply()
        borrarLlave()
    }

    // ---------- Llave en el Android Keystore ----------

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
                .setUserAuthenticationRequired(true) // solo se usa con huella
                .setInvalidatedByBiometricEnrollment(true) // huella nueva = llave inválida
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
            // Si no se pudo borrar, no pasa nada: se reemplaza al activar de nuevo
        }
    }

    private fun aTexto(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun deTexto(texto: String): ByteArray = Base64.decode(texto, Base64.NO_WRAP)
}
