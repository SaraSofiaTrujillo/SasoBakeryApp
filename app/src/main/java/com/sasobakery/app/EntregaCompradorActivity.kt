package com.sasobakery.app

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.annotation.SuppressLint
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

class EntregaCompradorActivity : AppCompatActivity() {

    private lateinit var cardDomicilio: View
    private lateinit var cardRecoger: View
    private lateinit var seccionDireccion: View
    private lateinit var seccionRecoger: View
    private lateinit var tvTituloFecha: TextView
    private lateinit var tvDirEtiqueta: TextView
    private lateinit var tvDirDetalle: TextView
    private lateinit var tvHorario: TextView
    private lateinit var chipsFecha: List<TextView>
    private lateinit var fechas: List<OpcionFecha>


    // Partes del diálogo de dirección que se actualizan al capturar la ubicación.
    // Son null cuando el diálogo está cerrado.
    private var campoDireccion: EditText? = null
    private var botonUbicacion: TextView? = null
    private var textoUbicacion: TextView? = null
    private var buscandoUbicacion = false // evita lanzar dos búsquedas a la vez
    private var ubicacionCapturadaEnDialogo = false // ¿se capturó mientras el diálogo estaba abierto?

    // Pide los permisos de ubicación y recibe la respuesta del comprador.
    // Se pide la precisa y la aproximada: Android 12 o superior deja elegir cuál dar.
    private val solicitudUbicacion = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        val concedido = resultado[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                resultado[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        when {
            concedido -> alConcederUbicacion()
            // Ya no deja volver a preguntar: solo se puede activar desde los ajustes
            !ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) -> mostrarPermisoBloqueado()
            else -> mensaje("Sin ubicación. Puedes escribir tu dirección a mano.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (CarritoRepositorio.cantidadTotal() == 0) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_entrega_comprador)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        cardDomicilio = findViewById(R.id.cardDomicilio)
        cardRecoger = findViewById(R.id.cardRecoger)
        seccionDireccion = findViewById(R.id.seccionDireccion)
        seccionRecoger = findViewById(R.id.seccionRecoger)
        tvTituloFecha = findViewById(R.id.tvTituloFecha)
        tvDirEtiqueta = findViewById(R.id.tvDirEtiqueta)
        tvDirDetalle = findViewById(R.id.tvDirDetalle)
        tvHorario = findViewById(R.id.tvHorario)
        chipsFecha = listOf(
            findViewById(R.id.chipFecha1),
            findViewById(R.id.chipFecha2),
            findViewById(R.id.chipFecha3)
        )
        fechas = EntregaRepositorio.fechasDisponibles()

        findViewById<TextView>(R.id.tvCostoDomicilio).text =
            formatearCop(EntregaRepositorio.COSTO_DOMICILIO)

        findViewById<View>(R.id.btnAtras).setOnClickListener { finish() }

        cardDomicilio.setOnClickListener {
            EntregaRepositorio.tipo = TipoEntrega.ENTREGA
            refrescar()
        }
        cardRecoger.setOnClickListener {
            EntregaRepositorio.tipo = TipoEntrega.RECOJO
            refrescar()
        }

        findViewById<View>(R.id.btnDireccion).setOnClickListener { mostrarDialogoDireccion() }

        chipsFecha.forEachIndexed { indice, chip ->
            chip.setOnClickListener {
                EntregaRepositorio.indiceFecha = indice
                refrescar()
            }
        }

        findViewById<View>(R.id.btnHorario).setOnClickListener { mostrarDialogoHorario() }

        findViewById<View>(R.id.btnContinuarPago).setOnClickListener { continuarAlPago() }

        refrescar()
    }


    private fun refrescar() {
        val domicilio = EntregaRepositorio.tipo == TipoEntrega.ENTREGA

        cardDomicilio.setBackgroundResource(
            if (domicilio) R.drawable.bg_entrega_opcion_seleccionada else R.drawable.bg_users_input
        )
        cardRecoger.setBackgroundResource(
            if (domicilio) R.drawable.bg_users_input else R.drawable.bg_entrega_opcion_seleccionada
        )

        seccionDireccion.visibility = if (domicilio) View.VISIBLE else View.GONE
        seccionRecoger.visibility = if (domicilio) View.GONE else View.VISIBLE
        tvTituloFecha.text = if (domicilio) "Fecha y hora" else "Fecha y hora de recogida"

        if (EntregaRepositorio.tieneDireccion()) {
            tvDirEtiqueta.text = EntregaRepositorio.etiquetaDireccion
            tvDirDetalle.text = EntregaRepositorio.direccion +
                    if (EntregaRepositorio.tieneUbicacion()) "\nUbicación GPS adjunta" else ""
        } else {
            tvDirEtiqueta.text = "Agregar dirección"
            tvDirDetalle.text = "Toca para escribir tu dirección de entrega"
        }

        chipsFecha.forEachIndexed { indice, chip ->
            val f = fechas[indice]
            chip.text = "${f.dia}\n${f.fecha}"
            if (indice == EntregaRepositorio.indiceFecha) {
                chip.setBackgroundResource(R.drawable.bg_users_chip_selected)
                chip.setTextColor(0xFFFFFFFF.toInt())
            } else {
                chip.setBackgroundResource(R.drawable.bg_users_chip)
                chip.setTextColor(0xFF5D477F.toInt())
            }
        }

        tvHorario.text = EntregaRepositorio.horario
    }

    private fun mostrarDialogoHorario() {
        val opciones = EntregaRepositorio.HORARIOS.toTypedArray()
        val actual = EntregaRepositorio.HORARIOS.indexOf(EntregaRepositorio.horario)

        MaterialAlertDialogBuilder(this)
            .setTitle("Horario")
            .setSingleChoiceItems(opciones, actual) { dialogo, posicion ->
                EntregaRepositorio.horario = EntregaRepositorio.HORARIOS[posicion]
                refrescar()
                dialogo.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoDireccion() {
        val dialogo = BottomSheetDialog(this)
        val vista = layoutInflater.inflate(R.layout.dialog_direccion_entrega, null)

        val etEtiqueta = vista.findViewById<EditText>(R.id.etDirEtiqueta)
        val etDireccion = vista.findViewById<EditText>(R.id.etDirDireccion)

        etEtiqueta.setText(EntregaRepositorio.etiquetaDireccion)
        etDireccion.setText(EntregaRepositorio.direccion)
        val direccionInicial = EntregaRepositorio.direccion
        ubicacionCapturadaEnDialogo = false

        campoDireccion = etDireccion
        botonUbicacion = vista.findViewById(R.id.btnUsarUbicacion)
        textoUbicacion = vista.findViewById(R.id.tvEstadoUbicacion)
        botonUbicacion?.setOnClickListener { pedirUbicacion() }
        mostrarEstadoUbicacion()
        // Al cerrar el diálogo se sueltan las referencias a sus vistas
        dialogo.setOnDismissListener {
            campoDireccion = null
            botonUbicacion = null
            textoUbicacion = null
        }


        vista.findViewById<View>(R.id.btnCancelarDireccion).setOnClickListener {
            dialogo.dismiss()
        }

        vista.findViewById<View>(R.id.btnGuardarDireccion).setOnClickListener {
            val direccion = etDireccion.text.toString().trim()
            if (direccion.isEmpty()) {
                etDireccion.error = "Escribe tu dirección"
                etDireccion.requestFocus()
                return@setOnClickListener
            }

            EntregaRepositorio.etiquetaDireccion =
                etEtiqueta.text.toString().trim().ifEmpty { "Dirección" }
            // Si cambió la dirección y no se capturó una ubicación nueva, las coordenadas
            // anteriores ya no corresponden: se descartan para no enviar al vendedor a otro lugar
            if (!ubicacionCapturadaEnDialogo && direccion != direccionInicial) {
                EntregaRepositorio.latitud = null
                EntregaRepositorio.longitud = null
            }
            EntregaRepositorio.direccion = direccion

            refrescar()
            dialogo.dismiss()
            mensaje("Dirección guardada")
        }

        dialogo.setContentView(vista)
        dialogo.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialogo.behavior.skipCollapsed = true
        dialogo.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialogo.show()
    }

    // ---------- Permiso de ubicación ----------

    /** ¿El comprador ya dio permiso de ubicación (precisa o aproximada)? */
    private fun tienePermisoUbicacion(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /** Punto de entrada del botón "Usar mi ubicación actual". */
    private fun pedirUbicacion() {
        when {
            tienePermisoUbicacion() -> alConcederUbicacion()
            // Ya lo negó una vez: antes de volver a pedirlo se explica para qué se usa
            ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) -> MaterialAlertDialogBuilder(this)
                .setTitle("¿Para qué usamos tu ubicación?")
                .setMessage(
                    "Solo la usamos para que el vendedor encuentre fácilmente el lugar " +
                            "de tu entrega. No la compartimos ni la usamos en otro momento."
                )
                .setNegativeButton("Ahora no", null)
                .setPositiveButton("Continuar") { _, _ -> lanzarSolicitudUbicacion() }
                .show()
            else -> lanzarSolicitudUbicacion()
        }
    }

    private fun lanzarSolicitudUbicacion() {
        solicitudUbicacion.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    /** El permiso ya está dado: se captura la ubicación actual. */
    private fun alConcederUbicacion() {
        capturarUbicacion()
    }

    /**
     * Pide las coordenadas actuales al servicio de ubicación de Google. Con permiso
     * "precisa" usa el GPS; con solo "aproximada" usa una ubicación de menor precisión.
     * Si no logra ubicarse (GPS apagado, sin señal) avisa y se puede escribir la dirección.
     */
    @SuppressLint("MissingPermission") // el permiso se verifica en tienePermisoUbicacion()
    private fun capturarUbicacion() {
        if (buscandoUbicacion || !tienePermisoUbicacion()) return
        buscandoUbicacion = true
        botonUbicacion?.text = "Buscando tu ubicación…"
        botonUbicacion?.isEnabled = false

        val precisa = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val prioridad = if (precisa) Priority.PRIORITY_HIGH_ACCURACY
        else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        val cliente = LocationServices.getFusedLocationProviderClient(this)
        cliente.getCurrentLocation(prioridad, CancellationTokenSource().token)
            .addOnSuccessListener { ubicacion ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                if (ubicacion != null) {
                    alObtenerUbicacion(ubicacion.latitude, ubicacion.longitude)
                } else {
                    // Sin lectura nueva: se intenta con la última ubicación conocida
                    cliente.lastLocation
                        .addOnSuccessListener { ultima ->
                            if (isFinishing || isDestroyed) return@addOnSuccessListener
                            if (ultima != null) {
                                alObtenerUbicacion(ultima.latitude, ultima.longitude)
                            } else {
                                terminarBusqueda(
                                    "No pudimos obtener tu ubicación. Revisa que el GPS esté activado."
                                )
                            }
                        }
                        .addOnFailureListener {
                            terminarBusqueda("No pudimos obtener tu ubicación.")
                        }
                }
            }
            .addOnFailureListener {
                if (isFinishing || isDestroyed) return@addOnFailureListener
                terminarBusqueda("No pudimos obtener tu ubicación. Revisa que el GPS esté activado.")
            }
    }

    /** Guarda las coordenadas y trata de convertirlas en una dirección escrita. */
    private fun alObtenerUbicacion(latitud: Double, longitud: Double) {
        EntregaRepositorio.latitud = latitud
        EntregaRepositorio.longitud = longitud
        ubicacionCapturadaEnDialogo = true
        buscarDireccion(latitud, longitud) { texto ->
            if (isFinishing || isDestroyed) return@buscarDireccion
            if (texto != null) {
                campoDireccion?.setText(texto)
                terminarBusqueda("Ubicación guardada. Agrega los detalles (apto, torre, casa).")
            } else {
                terminarBusqueda("Ubicación guardada. Escribe tu dirección y detalles.")
            }
        }
    }

    /**
     * Convierte coordenadas en una dirección ("Cra. 7 #45-20, Bogotá"). Depende del
     * servicio del celular: si no está disponible o falla, devuelve null y no pasa nada.
     */
    private fun buscarDireccion(latitud: Double, longitud: Double, alTerminar: (String?) -> Unit) {
        if (!Geocoder.isPresent()) {
            alTerminar(null)
            return
        }
        val geocoder = Geocoder(this, Locale.forLanguageTag("es-CO"))
        if (Build.VERSION.SDK_INT >= 33) {
            geocoder.getFromLocation(latitud, longitud, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(direcciones: MutableList<Address>) {
                    runOnUiThread { alTerminar(direcciones.firstOrNull()?.getAddressLine(0)) }
                }

                override fun onError(errorMessage: String?) {
                    runOnUiThread { alTerminar(null) }
                }
            })
        } else {
            // En versiones anteriores la búsqueda es lenta: se hace fuera del hilo principal
            Thread {
                val texto = try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitud, longitud, 1)
                        ?.firstOrNull()?.getAddressLine(0)
                } catch (e: Exception) {
                    null
                }
                runOnUiThread { alTerminar(texto) }
            }.start()
        }
    }

    /** Cierra la búsqueda: restaura el botón, actualiza la línea verde y avisa. */
    private fun terminarBusqueda(texto: String) {
        buscandoUbicacion = false
        botonUbicacion?.text = "Usar mi ubicación actual"
        botonUbicacion?.isEnabled = true
        mostrarEstadoUbicacion()
        refrescar()
        mensaje(texto)
    }

    /** Línea verde del diálogo: aparece solo si ya hay una ubicación guardada. */
    private fun mostrarEstadoUbicacion() {
        val texto = textoUbicacion ?: return
        if (EntregaRepositorio.tieneUbicacion()) {
            texto.text = String.format(
                Locale.US, "Ubicación GPS guardada (%.5f, %.5f)",
                EntregaRepositorio.latitud, EntregaRepositorio.longitud
            )
            texto.visibility = View.VISIBLE
        } else {
            texto.visibility = View.GONE
        }
    }

    /** El comprador marcó "no volver a preguntar": se le ofrece ir a los ajustes. */
    private fun mostrarPermisoBloqueado() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permiso de ubicación desactivado")
            .setMessage(
                "Para usar tu ubicación actual, actívala en Ajustes → Permisos. " +
                        "Mientras tanto puedes escribir tu dirección a mano."
            )
            .setNegativeButton("Ahora no", null)
            .setPositiveButton("Abrir ajustes") { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    )
                )
            }
            .show()
    }

    private fun continuarAlPago() {
        if (EntregaRepositorio.tipo == TipoEntrega.ENTREGA && !EntregaRepositorio.tieneDireccion()) {
            mensaje("Agrega la dirección de entrega")
            mostrarDialogoDireccion()
            return
        }

        startActivity(android.content.Intent(this, PagoCompradorActivity::class.java))
    }

    private fun mensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }
}