package com.sasobakery.app

import com.google.firebase.FirebaseApp
import java.security.SecureRandom
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

// resultado de login y registro, trae el rol o el mensaje de error
sealed class ResultadoAuth {
    data class Exito(val rol: Rol) : ResultadoAuth()
    // credencialesInvalidas sirve para que la huella se apague sola
    data class Error(
        val mensaje: String,
        val credencialesInvalidas: Boolean = false
    ) : ResultadoAuth()
}

// maneja login y registro con firebase, el perfil con el rol queda en firestore
object AuthRepositorio {
    // minimo que pide firebase
    const val LARGO_MINIMO_CLAVE = 6
    private const val COLECCION_USUARIOS = "usuarios"
    // nombre de la segunda app de firebase para invitar usuarios
    private const val NOMBRE_APP_SECUNDARIA = "creacion_usuarios"
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    // rol guardado en memoria para no consultar firestore a cada rato
    private var rolActual: Rol? = null

    fun rolEnCache(): Rol? = rolActual

    fun haySesion(): Boolean = auth.currentUser != null

    fun correoActual(): String? = auth.currentUser?.email

    fun uidActual(): String? = auth.currentUser?.uid

    // pantalla principal segun el rol
    fun destinoPara(rol: Rol): Class<*> = when (rol) {
        Rol.ADMINISTRADOR -> MainActivity::class.java
        Rol.VENDEDOR -> VendedorPrincipalActivity::class.java
        Rol.COMPRADOR -> CatalogoCompradorActivity::class.java
    }

    // crea la cuenta y despues el perfil, el admin no se puede crear desde aqui
    fun registrar(
        nombre: String,
        correo: String,
        clave: String,
        rol: Rol,
        telefono: String,
        alTerminar: (ResultadoAuth) -> Unit
    ) {
        if (rol == Rol.ADMINISTRADOR) {
            alTerminar(ResultadoAuth.Error("El registro no permite crear administradores"))
            return
        }

        auth.createUserWithEmailAndPassword(correo, clave)
            .addOnSuccessListener { resultado ->
                val usuario = resultado.user
                if (usuario == null) {
                    alTerminar(ResultadoAuth.Error("No se pudo crear la cuenta. Intenta de nuevo."))
                    return@addOnSuccessListener
                }

                val perfil = hashMapOf(
                    "nombre" to nombre,
                    "correo" to correo,
                    "rol" to rol.name.lowercase(),
                    "activo" to true,
                    "creado" to FieldValue.serverTimestamp()
                )
                perfil["telefono"] = telefono
                db.collection(COLECCION_USUARIOS).document(usuario.uid).set(perfil)
                    .addOnSuccessListener {
                        rolActual = rol
                        alTerminar(ResultadoAuth.Exito(rol))
                    }
                    .addOnFailureListener { error ->
                        // si falla el perfil se borra la cuenta para que no quede a medias
                        usuario.delete()
                        alTerminar(ResultadoAuth.Error(traducirError(error)))
                    }
            }
            .addOnFailureListener { alTerminar(ResultadoAuth.Error(traducirError(it))) }
    }

    // inicia sesion y luego busca el rol en firestore
    fun iniciarSesion(correo: String, clave: String, alTerminar: (ResultadoAuth) -> Unit) {
        auth.signInWithEmailAndPassword(correo, clave)
            .addOnSuccessListener { leerRolDeSesion(alTerminar) }
            .addOnFailureListener { error ->
                alTerminar(ResultadoAuth.Error(traducirError(error), esErrorDeCredenciales(error)))
            }
    }

    // para cuando la app abre con sesion guardada, revisa rol y que siga activa
    fun rolDeSesionActual(alTerminar: (ResultadoAuth) -> Unit) {
        if (auth.currentUser == null) {
            alTerminar(ResultadoAuth.Error("No hay sesión iniciada"))
            return
        }
        leerRolDeSesion(alTerminar)
    }

    // cierra sesion y limpia el carrito de la memoria
    fun cerrarSesion() {
        CarritoRepositorio.limpiarMemoria()
        auth.signOut()
    }

    // el admin invita usuarios, usa una segunda app de firebase para no perder su sesion
    fun crearUsuarioComoAdmin(
        nombre: String,
        correo: String,
        rol: Rol,
        alTerminar: (creado: Boolean, mensaje: String?) -> Unit
    ) {
        if (rolActual != Rol.ADMINISTRADOR) {
            alTerminar(false, "Solo un administrador puede invitar usuarios.")
            return
        }

        val authSecundaria = authSecundaria()

        authSecundaria.createUserWithEmailAndPassword(correo, generarClaveTemporal())
            .addOnSuccessListener { resultado ->
                val nuevo = resultado.user
                if (nuevo == null) {
                    authSecundaria.signOut()
                    alTerminar(false, "No se pudo crear la cuenta. Intenta de nuevo.")
                    return@addOnSuccessListener
                }

                val perfil = hashMapOf(
                    "nombre" to nombre,
                    "correo" to correo,
                    "rol" to rol.name.lowercase(),
                    "activo" to true,
                    "creado" to FieldValue.serverTimestamp()
                )
                db.collection(COLECCION_USUARIOS).document(nuevo.uid).set(perfil)
                    .addOnSuccessListener {
                        authSecundaria.signOut()
                        enviarEnlaceDeInvitacion(correo, alTerminar)
                    }
                    .addOnFailureListener { error ->
                        nuevo.delete().addOnCompleteListener {
                            authSecundaria.signOut()
                            alTerminar(false, traducirError(error))
                        }
                    }
            }
            .addOnFailureListener { alTerminar(false, traducirError(it)) }
    }

    // manda el correo para que la persona cree su clave
    private fun enviarEnlaceDeInvitacion(
        correo: String,
        alTerminar: (creado: Boolean, mensaje: String?) -> Unit
    ) {
        auth.setLanguageCode("es")
        auth.sendPasswordResetEmail(correo)
            .addOnSuccessListener { alTerminar(true, null) }
            .addOnFailureListener {
                alTerminar(
                    true,
                    "La cuenta se creó, pero no se pudo enviar el correo. La persona puede " +
                            "usar «¿Olvidé mi contraseña?» en el inicio de sesión."
                )
            }
    }

    // devuelve el auth de la segunda app, la crea si no existe
    private fun authSecundaria(): FirebaseAuth {
        val appPrincipal = FirebaseApp.getInstance()
        val appSecundaria = try {
            FirebaseApp.getInstance(NOMBRE_APP_SECUNDARIA)
        } catch (e: IllegalStateException) {
            FirebaseApp.initializeApp(
                appPrincipal.applicationContext,
                appPrincipal.options,
                NOMBRE_APP_SECUNDARIA
            )
        }
        return FirebaseAuth.getInstance(appSecundaria)
    }

    // clave al azar de 24 caracteres, nadie la conoce
    private fun generarClaveTemporal(): String {
        val caracteres = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val azar = SecureRandom()
        return (1..24).map { caracteres[azar.nextInt(caracteres.length)] }.joinToString("")
    }

    // si el correo no existe igual dice que salio bien, para no mostrar quien tiene cuenta
    fun enviarCorreoRecuperacion(correo: String, alTerminar: (String?) -> Unit) {
        auth.setLanguageCode("es")
        auth.sendPasswordResetEmail(correo)
            .addOnSuccessListener { alTerminar(null) }
            .addOnFailureListener { error ->
                if (error is FirebaseAuthInvalidUserException) alTerminar(null)
                else alTerminar(traducirError(error))
            }
    }

    // revisa que la clave sea de la cuenta actual
    fun verificarClave(clave: String, alTerminar: (String?) -> Unit) {
        val usuario = auth.currentUser
        val correo = usuario?.email
        if (usuario == null || correo == null) {
            alTerminar("No hay sesión iniciada")
            return
        }
        usuario.reauthenticate(EmailAuthProvider.getCredential(correo, clave))
            .addOnSuccessListener { alTerminar(null) }
            .addOnFailureListener { error ->
                alTerminar(
                    if (error is FirebaseAuthInvalidCredentialsException) "Contraseña incorrecta."
                    else traducirError(error)
                )
            }
    }

    // busca el perfil, si no existe o esta desactivado cierra la sesion
    private fun leerRolDeSesion(alTerminar: (ResultadoAuth) -> Unit) {
        val usuario = auth.currentUser
        if (usuario == null) {
            alTerminar(ResultadoAuth.Error("No hay sesión iniciada"))
            return
        }

        db.collection(COLECCION_USUARIOS).document(usuario.uid).get()
            .addOnSuccessListener { perfil ->
                val rol = Rol.values().firstOrNull {
                    it.name.equals(perfil.getString("rol"), ignoreCase = true)
                }
                when {
                    !perfil.exists() || rol == null -> {
                        auth.signOut()
                        alTerminar(ResultadoAuth.Error("Tu cuenta no tiene un perfil configurado. Contacta al administrador."))
                    }
                    perfil.getBoolean("activo") == false -> {
                        auth.signOut()
                        alTerminar(ResultadoAuth.Error("Tu cuenta está desactivada. Contacta al administrador."))
                    }
                    else -> {
                        rolActual = rol
                        alTerminar(ResultadoAuth.Exito(rol))
                    }
                }
            }
            .addOnFailureListener { alTerminar(ResultadoAuth.Error(traducirError(it))) }
    }

    // true si fue por correo o clave mala
    private fun esErrorDeCredenciales(error: Exception): Boolean =
        error is FirebaseAuthInvalidCredentialsException || error is FirebaseAuthInvalidUserException

    // pasa los errores de firebase a mensajes claros
    private fun traducirError(error: Exception): String = when (error) {
        is FirebaseAuthWeakPasswordException ->
            "La contraseña es muy débil. Usa al menos $LARGO_MINIMO_CLAVE caracteres."
        is FirebaseAuthUserCollisionException ->
            "Ya existe una cuenta con ese correo."
        is FirebaseAuthInvalidCredentialsException,
        is FirebaseAuthInvalidUserException ->
            "Correo o contraseña incorrectos."
        is FirebaseTooManyRequestsException ->
            "Demasiados intentos. Espera unos minutos e intenta de nuevo."
        is FirebaseNetworkException ->
            "Sin conexión a internet. Revisa tu red e intenta de nuevo."
        is FirebaseFirestoreException ->
            if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                "No tienes permiso para esta acción."
            else "No se pudo consultar tu cuenta. Intenta de nuevo."
        else -> "Ocurrió un error. Intenta de nuevo."
    }
}