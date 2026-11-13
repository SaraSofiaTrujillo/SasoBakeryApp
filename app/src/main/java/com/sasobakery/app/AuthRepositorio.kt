package com.sasobakery.app

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

/**
 * Resultado de una operación de autenticación: o salió bien y trae el rol del
 * usuario, o salió mal y trae un mensaje listo para mostrar en pantalla.
 * [credencialesInvalidas] es true cuando falló porque el correo o la contraseña
 * ya no son válidos (la huella usa esto para desactivarse sola).
 */
sealed class ResultadoAuth {
    data class Exito(val rol: Rol) : ResultadoAuth()
    data class Error(
        val mensaje: String,
        val credencialesInvalidas: Boolean = false
    ) : ResultadoAuth()
}

/**
 * Único punto de la app que habla con Firebase Authentication y con la
 * colección "usuarios" de Firestore. Las pantallas solo llaman a estas funciones.
 *
 * Cada cuenta tiene dos partes:
 *  - Authentication: correo y contraseña (Firebase las guarda cifradas).
 *  - Firestore usuarios/{uid}: nombre, correo, rol y si la cuenta está activa.
 */
object AuthRepositorio {

    const val LARGO_MINIMO_CLAVE = 6 // mínimo que exige Firebase
    private const val COLECCION_USUARIOS = "usuarios"

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    /** true si ya hay una sesión guardada en este celular. */
    fun haySesion(): Boolean = auth.currentUser != null

    /** Correo de la cuenta con la sesión iniciada (null si no hay sesión). */
    fun correoActual(): String? = auth.currentUser?.email

    /** Pantalla principal que le corresponde a cada rol. */
    fun destinoPara(rol: Rol): Class<*> = when (rol) {
        Rol.ADMINISTRADOR -> MainActivity::class.java
        Rol.VENDEDOR -> VendedorPrincipalActivity::class.java
        Rol.COMPRADOR -> CatalogoCompradorActivity::class.java
    }

    // ---------- Registro ----------

    /**
     * Crea la cuenta en Authentication y su perfil en Firestore.
     * Al terminar bien, el usuario queda con la sesión iniciada.
     */
    fun registrar(
        nombre: String,
        correo: String,
        clave: String,
        rol: Rol,
        alTerminar: (ResultadoAuth) -> Unit
    ) {
        // Seguridad: el registro público nunca crea administradores
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
                db.collection(COLECCION_USUARIOS).document(usuario.uid).set(perfil)
                    .addOnSuccessListener { alTerminar(ResultadoAuth.Exito(rol)) }
                    .addOnFailureListener { error ->
                        // Una cuenta sin perfil no sirve: se borra para poder intentar otra vez
                        usuario.delete()
                        alTerminar(ResultadoAuth.Error(traducirError(error)))
                    }
            }
            .addOnFailureListener { alTerminar(ResultadoAuth.Error(traducirError(it))) }
    }

    // ---------- Inicio y cierre de sesión ----------

    /** Inicia sesión con correo y contraseña y devuelve el rol de la cuenta. */
    fun iniciarSesion(correo: String, clave: String, alTerminar: (ResultadoAuth) -> Unit) {
        auth.signInWithEmailAndPassword(correo, clave)
            .addOnSuccessListener { leerRolDeSesion(alTerminar) }
            .addOnFailureListener { error ->
                alTerminar(ResultadoAuth.Error(traducirError(error), esErrorDeCredenciales(error)))
            }
    }

    /** Con una sesión ya guardada (la app se abrió de nuevo), consulta el rol vigente. */
    fun rolDeSesionActual(alTerminar: (ResultadoAuth) -> Unit) {
        if (auth.currentUser == null) {
            alTerminar(ResultadoAuth.Error("No hay sesión iniciada"))
            return
        }
        leerRolDeSesion(alTerminar)
    }

    /** Cierra la sesión en este celular. */
    fun cerrarSesion() {
        auth.signOut()
    }

    // ---------- Contraseña ----------

    /**
     * Envía a [correo] un enlace para crear una nueva contraseña.
     * [alTerminar] recibe null si salió bien o un mensaje de error.
     * Si el correo no tiene cuenta también responde "bien": así nadie puede
     * averiguar qué correos están registrados.
     */
    fun enviarCorreoRecuperacion(correo: String, alTerminar: (String?) -> Unit) {
        auth.setLanguageCode("es") // el correo llega en español
        auth.sendPasswordResetEmail(correo)
            .addOnSuccessListener { alTerminar(null) }
            .addOnFailureListener { error ->
                if (error is FirebaseAuthInvalidUserException) alTerminar(null)
                else alTerminar(traducirError(error))
            }
    }

    /**
     * Comprueba que [clave] sea la contraseña de la cuenta con sesión iniciada.
     * [alTerminar] recibe null si es correcta o un mensaje de error.
     */
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

    // ---------- Internas ----------

    /**
     * Lee el perfil de la sesión actual en Firestore. Si la cuenta no tiene perfil
     * o está desactivada, cierra la sesión y avisa: así un usuario desactivado
     * por el administrador no puede entrar.
     */
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
                    else -> alTerminar(ResultadoAuth.Exito(rol))
                }
            }
            // Si falla la lectura (por ejemplo sin internet) no se cierra la sesión
            .addOnFailureListener { alTerminar(ResultadoAuth.Error(traducirError(it))) }
    }

    private fun esErrorDeCredenciales(error: Exception): Boolean =
        error is FirebaseAuthInvalidCredentialsException || error is FirebaseAuthInvalidUserException

    /** Convierte los errores de Firebase en mensajes claros en español. */
    private fun traducirError(error: Exception): String = when (error) {
        // Esta va primero: "contraseña débil" es un tipo de credencial inválida
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