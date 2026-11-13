package com.sasobakery.app

import android.text.InputType
import android.widget.FrameLayout
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PerfilCompradorActivity : AppCompatActivity() {

    private lateinit var tvIniciales: TextView
    private lateinit var tvNombre: TextView
    private lateinit var tvCorreo: TextView
    private lateinit var tvPedidos: TextView
    private lateinit var tvPuntos: TextView
    private lateinit var tvBadgeNav: TextView
    private lateinit var tvBadgePedidos: TextView
    private lateinit var tvSubDirecciones: TextView
    private lateinit var tvSubHuella: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_perfil_comprador)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        tvIniciales = findViewById(R.id.tvIniciales)
        tvNombre = findViewById(R.id.tvNombre)
        tvCorreo = findViewById(R.id.tvCorreo)
        tvPedidos = findViewById(R.id.tvPedidos)
        tvPuntos = findViewById(R.id.tvPuntos)
        tvBadgeNav = findViewById(R.id.tvBadgeNav)

        crearFilas()

        findViewById<View>(R.id.btnEditarPerfil).setOnClickListener { mostrarEditar() }

        findViewById<View>(R.id.navInicio).setOnClickListener { irAlCatalogo() }
        findViewById<View>(R.id.navCategorias).setOnClickListener { irAlCatalogo() }
        findViewById<View>(R.id.navCarrito).setOnClickListener { irAlCarrito() }
    }

    override fun onResume() {
        super.onResume()
        mostrarPerfil()
    }

    private fun crearFilas() {
        val actividad = findViewById<LinearLayout>(R.id.layoutActividad)
        val opciones = findViewById<LinearLayout>(R.id.layoutOpciones)

        val filaPedidos = agregarFila(
            actividad, R.drawable.ic_bag, "Mis pedidos", "Revisa y sigue tus compras"
        ) { startActivity(Intent(this, MisPedidosCompradorActivity::class.java)) }
        tvBadgePedidos = filaPedidos.findViewById(R.id.tvBadge)

        agregarDivisor(actividad)
        agregarFila(
            actividad, R.drawable.ic_heart, "Mis favoritos", "Tus dulces guardados"
        ) { startActivity(Intent(this, FavoritosCompradorActivity::class.java)) }

        agregarDivisor(actividad)
        val filaDirecciones = agregarFila(
            actividad, R.drawable.ic_pin, "Direcciones", "Agrega tu dirección de entrega"
        ) { mostrarDireccion() }
        tvSubDirecciones = filaDirecciones.findViewById(R.id.tvSub)

        agregarFila(opciones, R.drawable.ic_card, "Métodos de pago") { mostrarMetodosPago() }
        agregarDivisor(opciones)
        agregarFila(opciones, R.drawable.ic_bell, "Notificaciones") { mostrarNotificaciones() }
        agregarDivisor(opciones)
        val filaHuella = agregarFila(
            opciones, R.drawable.ic_fingerprint, "Acceso con huella", "Entra sin escribir tu contraseña"
        ) { gestionarHuella() }
        tvSubHuella = filaHuella.findViewById(R.id.tvSub)
        agregarDivisor(opciones)
        agregarFila(
            opciones, R.drawable.ic_logout, "Cerrar sesión", peligro = true
        ) { confirmarCerrarSesion() }
    }

    private fun agregarFila(
        contenedor: LinearLayout,
        icono: Int,
        titulo: String,
        subtitulo: String = "",
        peligro: Boolean = false,
        alTocar: () -> Unit
    ): View {
        val fila = layoutInflater.inflate(R.layout.item_perfil_opcion, contenedor, false)

        val ivIcono = fila.findViewById<ImageView>(R.id.ivIcono)
        val tvTitulo = fila.findViewById<TextView>(R.id.tvTitulo)
        val tvSub = fila.findViewById<TextView>(R.id.tvSub)

        ivIcono.setImageResource(icono)
        tvTitulo.text = titulo
        if (subtitulo.isEmpty()) tvSub.visibility = View.GONE else tvSub.text = subtitulo

        if (peligro) {
            tvTitulo.setTextColor(0xFFC2485F.toInt())
            ivIcono.imageTintList = ColorStateList.valueOf(0xFFC2485F.toInt())
        }

        fila.setOnClickListener { alTocar() }
        contenedor.addView(fila)
        return fila
    }

    private fun agregarDivisor(contenedor: LinearLayout) {
        val linea = View(this)
        val alto = (1 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        linea.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, alto)
        linea.setBackgroundColor(0xFFEEE8F4.toInt())
        contenedor.addView(linea)
    }

    private fun mostrarPerfil() {
        val p = PerfilCompradorRepositorio.obtener()
        tvIniciales.text = p.iniciales
        tvNombre.text = p.nombre
        tvCorreo.text = p.correo

        tvPedidos.text = PerfilCompradorRepositorio.misPedidos().size.toString()
        tvPuntos.text = PerfilCompradorRepositorio.puntos().toString()

        val activos = PerfilCompradorRepositorio.pedidosActivos()
        tvBadgePedidos.visibility = if (activos > 0) View.VISIBLE else View.GONE
        tvBadgePedidos.text = if (activos == 1) "1 activo" else "$activos activos"

        tvSubDirecciones.text = if (EntregaRepositorio.tieneDireccion()) {
            "${EntregaRepositorio.etiquetaDireccion} · ${EntregaRepositorio.direccion}"
        } else {
            "Agrega tu dirección de entrega"
        }

        actualizarTextoHuella()

        val total = CarritoRepositorio.cantidadTotal()
        tvBadgeNav.visibility = if (total == 0) View.GONE else View.VISIBLE
        tvBadgeNav.text = if (total > 9) "9+" else total.toString()
    }

    private fun mostrarEditar() {
        val actual = PerfilCompradorRepositorio.obtener()
        val dialogo = BottomSheetDialog(this)
        val vista = layoutInflater.inflate(R.layout.dialog_editar_perfil_comprador, null)

        val etNombre = vista.findViewById<EditText>(R.id.etCompradorNombre)
        etNombre.setText(actual.nombre)
        vista.findViewById<TextView>(R.id.tvCompradorCorreo).text = actual.correo

        vista.findViewById<View>(R.id.btnCancelarPerfil).setOnClickListener { dialogo.dismiss() }

        vista.findViewById<View>(R.id.btnGuardarPerfil).setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            if (nombre.isEmpty()) {
                etNombre.error = "Escribe tu nombre"
                etNombre.requestFocus()
                return@setOnClickListener
            }
            PerfilCompradorRepositorio.guardar(actual.copy(nombre = nombre))
            mostrarPerfil()
            dialogo.dismiss()
            mensaje("Perfil actualizado")
        }

        abrirHoja(dialogo, vista)
    }

    private fun mostrarDireccion() {
        val dialogo = BottomSheetDialog(this)
        val vista = layoutInflater.inflate(R.layout.dialog_direccion_entrega, null)

        val etEtiqueta = vista.findViewById<EditText>(R.id.etDirEtiqueta)
        val etDireccion = vista.findViewById<EditText>(R.id.etDirDireccion)
        etEtiqueta.setText(EntregaRepositorio.etiquetaDireccion)
        etDireccion.setText(EntregaRepositorio.direccion)

        vista.findViewById<View>(R.id.btnCancelarDireccion).setOnClickListener { dialogo.dismiss() }

        vista.findViewById<View>(R.id.btnGuardarDireccion).setOnClickListener {
            val direccion = etDireccion.text.toString().trim()
            if (direccion.isEmpty()) {
                etDireccion.error = "Escribe tu dirección"
                etDireccion.requestFocus()
                return@setOnClickListener
            }
            EntregaRepositorio.etiquetaDireccion =
                etEtiqueta.text.toString().trim().ifEmpty { "Dirección" }
            EntregaRepositorio.direccion = direccion

            mostrarPerfil()
            dialogo.dismiss()
            mensaje("Dirección guardada")
        }

        abrirHoja(dialogo, vista)
    }

    private fun abrirHoja(dialogo: BottomSheetDialog, vista: View) {
        dialogo.setContentView(vista)
        dialogo.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialogo.behavior.skipCollapsed = true
        dialogo.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialogo.show()
    }

    private fun mostrarMetodosPago() {
        val texto = PagoRepositorio.metodos().joinToString("\n\n") { "${it.titulo}\n${it.detalle}" }
        MaterialAlertDialogBuilder(this)
            .setTitle("Métodos de pago")
            .setMessage("$texto\n\nPuedes agregar una tarjeta nueva al pagar tu pedido.")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun mostrarNotificaciones() {
        val marcado = booleanArrayOf(PerfilCompradorRepositorio.notificaciones)
        MaterialAlertDialogBuilder(this)
            .setTitle("Notificaciones")
            .setMultiChoiceItems(
                arrayOf("Avisarme cuando cambie el estado de mi pedido"), marcado
            ) { _, _, activo -> marcado[0] = activo }
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                PerfilCompradorRepositorio.notificaciones = marcado[0]
                mensaje(if (marcado[0]) "Notificaciones activadas" else "Notificaciones desactivadas")
            }
            .show()
    }

    private fun confirmarCerrarSesion() {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Cerrar sesión?")
            .setMessage("Volverás a la pantalla de inicio de sesión.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar sesión") { _, _ -> cerrarSesion() }
            .show()
    }

    private fun cerrarSesion() {
        AuthRepositorio.cerrarSesion() // cierra la sesión de Firebase
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    // ---------- Acceso con huella ----------

    // Muestra en el perfil si la huella está activada para la cuenta con sesión iniciada
    private fun actualizarTextoHuella() {
        val correo = AuthRepositorio.correoActual().orEmpty()
        tvSubHuella.text = when {
            BiometriaRepositorio.estaActivada(this, correo) -> "Activado en este celular"
            BiometriaHelper.estado(this) == EstadoBiometria.SIN_SENSOR ->
                "Este celular no tiene sensor biométrico"
            else -> "Desactivado"
        }
    }

    // Activa o desactiva la huella según el estado actual
    private fun gestionarHuella() {
        val correo = AuthRepositorio.correoActual().orEmpty()

        if (BiometriaRepositorio.estaActivada(this, correo)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("¿Desactivar el acceso con huella?")
                .setMessage("Tendrás que iniciar sesión con tu contraseña en este celular.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Desactivar") { _, _ ->
                    BiometriaRepositorio.desactivar(this)
                    actualizarTextoHuella()
                    mensaje("Acceso con huella desactivado")
                }
                .show()
            return
        }

        when (BiometriaHelper.estado(this)) {
            EstadoBiometria.DISPONIBLE -> pedirClaveParaHuella(correo)
            EstadoBiometria.SIN_HUELLA_REGISTRADA ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Registra tu huella primero")
                    .setMessage("Este celular todavía no tiene una huella registrada. Regístrala en los ajustes y vuelve aquí.")
                    .setNegativeButton("Ahora no", null)
                    .setPositiveButton("Abrir ajustes") { _, _ ->
                        BiometriaHelper.abrirAjustesDeRegistro(this)
                    }
                    .show()
            EstadoBiometria.SIN_SENSOR -> mensaje("Este celular no tiene sensor biométrico")
            EstadoBiometria.NO_DISPONIBLE ->
                mensaje("La biometría no está disponible ahora. Intenta más tarde.")
        }
    }

    /**
     * Para activar la huella hace falta la contraseña de la cuenta: se pide una vez,
     * se comprueba con Firebase y se guarda cifrada, protegida por la huella.
     */
    private fun pedirClaveParaHuella(correo: String) {
        val densidad = resources.displayMetrics.density
        val campo = EditText(this).apply {
            hint = "Tu contraseña"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val contenedor = FrameLayout(this).apply {
            val lado = (24 * densidad).toInt()
            setPadding(lado, (8 * densidad).toInt(), lado, 0)
            addView(
                campo,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirma tu contraseña")
            .setMessage("La necesitamos una vez para activar la huella. Se guarda cifrada y solo se abre con tu huella.")
            .setView(contenedor)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar") { _, _ ->
                verificarClaveYActivar(correo, campo.text.toString())
            }
            .show()
    }

    // Comprueba la contraseña en Firebase y, si es correcta, pide la huella para guardarla
    private fun verificarClaveYActivar(correo: String, clave: String) {
        if (clave.isEmpty()) {
            mensaje("Escribe tu contraseña")
            return
        }
        AuthRepositorio.verificarClave(clave) { error ->
            if (isFinishing || isDestroyed) return@verificarClave
            if (error != null) {
                mensaje(error)
                return@verificarClave
            }
            BiometriaHelper.activar(this, correo, clave) { activada ->
                if (activada) mensaje("Acceso con huella activado")
                actualizarTextoHuella()
            }
        }
    }

    private fun irAlCatalogo() {
        val intent = Intent(this, CatalogoCompradorActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun irAlCarrito() {
        val intent = Intent(this, CarritoCompradorActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun mensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }
}