package com.sasobakery.app

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

// Perfil e historial del comprador.
// No guarda datos propios: lee de los repositorios que ya existen.
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

        // Menú inferior: Perfil ya es esta pantalla
        findViewById<View>(R.id.navInicio).setOnClickListener { irAlCatalogo() }
        findViewById<View>(R.id.navCategorias).setOnClickListener { irAlCatalogo() }
        findViewById<View>(R.id.navCarrito).setOnClickListener { irAlCarrito() }
    }

    // Cada vez que se vuelve a esta pantalla se leen de nuevo los datos
    override fun onResume() {
        super.onResume()
        mostrarPerfil()
    }

    // ---------- Filas del menú ----------

    private fun crearFilas() {
        val actividad = findViewById<LinearLayout>(R.id.layoutActividad)
        val opciones = findViewById<LinearLayout>(R.id.layoutOpciones)

        // Mi actividad
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

        // Más opciones
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

    /** Crea una fila con item_perfil_opcion, la agrega al contenedor y la devuelve. */
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

        // "Cerrar sesión" va en rosa
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

    // ---------- Datos en pantalla ----------

    private fun mostrarPerfil() {
        val p = PerfilCompradorRepositorio.obtener()
        tvIniciales.text = p.iniciales
        tvNombre.text = p.nombre
        tvCorreo.text = p.correo

        tvPedidos.text = PerfilCompradorRepositorio.misPedidos().size.toString()
        tvPuntos.text = PerfilCompradorRepositorio.puntos().toString()

        // "1 activo" / "2 activos" (se oculta si no hay)
        val activos = PerfilCompradorRepositorio.pedidosActivos()
        tvBadgePedidos.visibility = if (activos > 0) View.VISIBLE else View.GONE
        tvBadgePedidos.text = if (activos == 1) "1 activo" else "$activos activos"

        // Dirección guardada (la misma que se usa al comprar)
        tvSubDirecciones.text = if (EntregaRepositorio.tieneDireccion()) {
            "${EntregaRepositorio.etiquetaDireccion} · ${EntregaRepositorio.direccion}"
        } else {
            "Agrega tu dirección de entrega"
        }

        actualizarTextoHuella()

        // Globito del carrito
        val total = CarritoRepositorio.cantidadTotal()
        tvBadgeNav.visibility = if (total == 0) View.GONE else View.VISIBLE
        tvBadgeNav.text = if (total > 9) "9+" else total.toString()
    }

    // ---------- Editar perfil ----------

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

    // ---------- Direcciones (misma dirección y mismo formulario de Entrega) ----------

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

    /** Abre la hoja completa y evita que el teclado tape los campos. */
    private fun abrirHoja(dialogo: BottomSheetDialog, vista: View) {
        dialogo.setContentView(vista)
        dialogo.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialogo.behavior.skipCollapsed = true
        dialogo.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialogo.show()
    }

    // ---------- Más opciones ----------

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

    // Va a la selección de rol y borra el historial (Atrás no regresa al perfil)
    private fun cerrarSesion() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }
    // ---------- Acceso con huella ----------

    private fun actualizarTextoHuella() {
        val correo = PerfilCompradorRepositorio.obtener().correo
        tvSubHuella.text = when {
            BiometriaRepositorio.estaActivada(this, correo) -> "Activado en este celular"
            BiometriaHelper.estado(this) == EstadoBiometria.SIN_SENSOR ->
                "Este celular no tiene sensor biométrico"
            else -> "Desactivado"
        }
    }

    // Activa o desactiva la huella según el estado actual
    private fun gestionarHuella() {
        val correo = PerfilCompradorRepositorio.obtener().correo

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
            EstadoBiometria.DISPONIBLE ->
                BiometriaHelper.activar(this, correo) { activada ->
                    if (activada) mensaje("Acceso con huella activado")
                    actualizarTextoHuella()
                }
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
    // ---------- Navegación ----------

    // Vuelve al catálogo que ya estaba abierto (no abre uno nuevo encima)
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