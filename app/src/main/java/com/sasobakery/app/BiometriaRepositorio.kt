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

// guarda la clave cifrada con una llave del keystore, solo se abre con la huella
object BiometriaRepositorio {
    // preferencias donde queda el correo y la clave cifrada
    private const val ARCHIVO = "saso_biometria"
    private const val CLAVE_CORREO = "correo"
    private const val CLAVE_TOKEN = "token_cifrado"
    private const val CLAVE_IV = "iv"

    // keystore de android, la llave nunca sale de ahi
    private const val ALMACEN = "AndroidKeyStore"
    private const val ALIAS_LLAVE = "saso_llave_biometrica"
    // cifrado AES GCM
    private const val TRANSFORMACION = "AES/GCM/NoPadding"
    private const val BITS_ETIQUETA = 128

    // acceso a las preferencias
    private fun prefs(contexto: Context) =
        contexto.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    // correo con huella activada, null si no hay
    fun correoActivado(contexto: Context): String? {
        val p = prefs(contexto)
        return if (p.contains(CLAVE_TOKEN)) p.getString(CLAVE_CORREO, null) else null
    }
    // true si ese correo tiene huella activada
    fun estaActivada(contexto: Context, correo: String): Boolean =
        correoActivado(contexto) == correo.trim().lowercase()

    // crea llave nueva y deja listo el cifrador, se usa despues de la huella
    fun cifradoParaActivar(): Cipher? = try {
        borrarLlave()
        val llave = crearLlave()
        Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, llave) }
    } catch (e: Exception) {
        null
    }
    // cifra la clave y guarda correo, clave cifrada y iv
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

    // prepara el descifrador, si la llave ya no existe desactiva la huella
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

    // descifra la clave guardada
    fun leerClave(contexto: Context, cipher: Cipher): String? = try {
        val guardado = prefs(contexto).getString(CLAVE_TOKEN, null)
        if (guardado == null) null
        else String(cipher.doFinal(deTexto(guardado)), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    // borra lo guardado y la llave
    fun desactivar(contexto: Context) {
        prefs(contexto).edit().clear().apply()
        borrarLlave()
    }

    // llave que exige huella y se invalida si se registra una nueva
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

    // busca la llave en el keystore
    private fun obtenerLlave(): SecretKey? {
        val almacen = KeyStore.getInstance(ALMACEN).apply { load(null) }
        return almacen.getKey(ALIAS_LLAVE, null) as? SecretKey
    }

    // borra la llave si existe
    private fun borrarLlave() {
        try {
            val almacen = KeyStore.getInstance(ALMACEN).apply { load(null) }
            if (almacen.containsAlias(ALIAS_LLAVE)) almacen.deleteEntry(ALIAS_LLAVE)
        } catch (e: Exception) {
        }
    }

    // bytes a texto para guardarlos en preferencias
    private fun aTexto(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun deTexto(texto: String): ByteArray = Base64.decode(texto, Base64.NO_WRAP)
}