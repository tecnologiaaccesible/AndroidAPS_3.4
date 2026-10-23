package app.aaps.pump.ypsopump

import android.Manifest
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.aaps.pump.ypsopump.ble.YpsoBleDiagnostics
import app.aaps.pump.ypsopump.auth.YpsoPumpConnectionGate
import dagger.android.support.DaggerFragment
import javax.inject.Inject

/**
 * Accessible, explicitly read-only device inspection.
 * No bolus, basal, setting changes or cryptographic key handling.
 */
class YpsoLabFragment : DaggerFragment() {

    @Inject lateinit var ble: YpsoBleDiagnostics

    private val authGate = YpsoPumpConnectionGate()
    private var logText: TextView? = null
    private var deviceList: LinearLayout? = null
    private var inventoryText: TextView? = null
    private var inventory: String = ""
    private val messages = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val scroll = ScrollView(context)
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        content.addView(TextView(context).apply {
            text = getString(R.string.ypsolab_description)
            textSize = 17f
            setPadding(0, 0, 0, 16)
        })

        content.addView(Button(context).apply {
            text = getString(R.string.ypsolab_scan)
            setOnClickListener {
                if (!ble.hasBluetoothPermissions()) {
                    requestPermissions(
                        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                        815
                    )
                    appendLog(getString(R.string.ypsolab_grant_permission))
                } else {
                    deviceList?.removeAllViews()
                    ble.scan()
                }
            }
        })

        content.addView(Button(context).apply {
            text = "Comprobar requisitos de autenticación (sin enviar comandos)"
            contentDescription = "Comprobar enlace Bluetooth y disponibilidad de autorización segura"
            setOnClickListener {
                val link = ble.linkSnapshot()
                val state = authGate.updateBleLink(link.connected, link.identity)
                appendLog("Diagnóstico de sesión: $state")
                if (link.connected) {
                    appendLog("Resultado de autorización: ${authGate.tryAuthorization()}")
                } else {
                    appendLog("No hay enlace BLE. Conecta primero la bomba de laboratorio.")
                }
                appendLog("Sesión autenticada con YpsoPump: NO. Proveedor autorizado no configurado; no se envían solicitudes ni claves.")
            }
        })

        content.addView(Button(context).apply {
            text = getString(R.string.ypsolab_disconnect)
            setOnClickListener {
                ble.stopScan()
                ble.disconnect()
                authGate.updateBleLink(false, null)
            }
        })

        content.addView(TextView(context).apply {
            text = getString(R.string.ypsolab_results)
            textSize = 18f
            setPadding(0, 16, 0, 8)
        })
        deviceList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(deviceList)

        logText = TextView(context).apply {
            textSize = 16f
            setTextIsSelectable(true)
            setPadding(0, 16, 0, 8)
            text = messages.joinToString("\n")
        }
        content.addView(logText)
        content.addView(Button(context).apply {
            text = "Copiar diagnóstico y mapa GATT"
            contentDescription = "Copiar diagnóstico y mapa GATT de YpsoPump al portapapeles"
            setOnClickListener {
                val report = "YpsoPump AndroidAPS - solo lectura\\n" +
                    messages.joinToString("\\n") + "\\n\\n" + inventory
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("YpsoPump - diagnóstico", report))
                appendLog("Informe copiado al portapapeles.")
            }
        })
        inventoryText = TextView(context).apply {
            textSize = 15f
            setTextIsSelectable(true)
            text = "Mapa GATT: conecta la bomba para obtenerlo."
        }
        content.addView(inventoryText)
        return root
    }

    override fun onStart() {
        super.onStart()
        ble.setListeners(
            onEvent = { appendLog(it) },
            onDevice = { address, name ->
                if (isAdded) {
                    val button = Button(requireContext()).apply {
                        text = "$name (${address.takeLast(5)})"
                        contentDescription = getString(R.string.ypsolab_connect_to, name, address.takeLast(5))
                        setOnClickListener { ble.connect(address) }
                    }
                    deviceList?.addView(button)
                }
            },
            onLinkState = { _ ->
                val link = ble.linkSnapshot()
                authGate.updateBleLink(link.connected, link.identity)
                appendLog(if (link.connected) "BLE conectado; autenticación de aplicación pendiente." else "BLE desconectado; sesión invalidada.")
            },
            onInventory = { map ->
                inventory = map
                inventoryText?.text = map
            }
        )
    }

    private fun appendLog(message: String) {
        messages.add(message)
        while (messages.size > 40) messages.removeAt(0)
        logText?.text = messages.joinToString("\n")
    }

    override fun onStop() {
        ble.setListeners(null, null)
        ble.stopScan()
        ble.disconnect()
        authGate.updateBleLink(false, null)
        super.onStop()
    }

    override fun onDestroyView() {
        logText = null
        deviceList = null
        inventoryText = null
        super.onDestroyView()
    }
}
