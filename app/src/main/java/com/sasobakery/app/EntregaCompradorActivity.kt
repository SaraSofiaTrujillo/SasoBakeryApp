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

// elige domicilio o recoger, direccion con gps, fecha y horario
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

    // vistas del dialogo de direccion, quedan en null cuando se cierra
    private var campoDireccion: EditText? = null
    private var botonUbicacion: TextView? = null
    private var textoUbicacion: TextView? = null
    // evita pedir el gps dos veces seguidas
    private var buscandoUbicacion = false
    // true si se uso el gps, asi no se borran las coordenadas al guardar
    private var ubicacionCapturadaEnDialogo = false

    // resultado del permiso de ubicacion, si lo bloqueo para siempre manda a ajustes
    private val solicitudUbicacion = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        val concedido = resultado[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                resultado[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        when {
            concedido -> alConcederUbicacion()
            !ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) -> mostrarPermisoBloqueado()
            else -> mensaje("Sin ubicación. Puedes escribir tu dirección a mano.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // sin productos no hay entrega que configurar
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

    // pinta la opcion elegida, la direccion, las fechas y el horario
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

    // lista de horarios para escoger uno
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

    // hoja para escribir la direccion o llenarla con el gps
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
            // si cambio la direccion a mano las coordenadas viejas ya no sirven
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

    // vale con ubicacion precisa o aproximada
    private fun tienePermisoUbicacion(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    // si ya hay permiso captura, si lo nego una vez explica para que se usa
    private fun pedirUbicacion() {
        when {
            tienePermisoUbicacion() -> alConcederUbicacion()
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

    private fun alConcederUbicacion() {
        capturarUbicacion()
    }

    @SuppressLint("MissingPermission")
    // pide la ubicacion actual, si no hay usa la ultima conocida
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

    // guarda las coordenadas y trata de convertirlas en una direccion
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

    // de coordenadas a texto, en android 13 o mas es asincrono, antes va en un hilo
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

    // deja el boton listo otra vez y avisa el resultado
    private fun terminarBusqueda(texto: String) {
        buscandoUbicacion = false
        botonUbicacion?.text = "Usar mi ubicación actual"
        botonUbicacion?.isEnabled = true
        mostrarEstadoUbicacion()
        refrescar()
        mensaje(texto)
    }

    // muestra las coordenadas guardadas si hay
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

    // el permiso solo se puede activar desde ajustes
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

    // si es domicilio exige direccion antes de seguir
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