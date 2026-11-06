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
    fun guardar(contexto: Context, correo: String, cipher: Cipher): Boolean = try {
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

    fun leerToken(contexto: Context, cipher: Cipher): String? = try {
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
