package app.aaps.ypsolab

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import app.aaps.pump.ypsopump.auth.YpsoAuthSession
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.aaps.pump.ypsopump.ble.YpsoBleDiagnostics

/**
 * Standalone, read-only BLE diagnostic app.
 *
 * Not an AAPS therapy app: no insulin algorithms, pump drivers or pump writes.
 * Use only for off-body pump testing.
 */
class YpsoLabActivity : Activity() {

    private val ble by lazy { YpsoBleDiagnostics(applicationContext) }
    private val messages = ArrayDeque<String>()
    private val authSession by lazy { YpsoAuthSession(clockMillis = SystemClock::elapsedRealtime) }
    private var authStatus: TextView? = null
    private var authCheckButton: Button? = null
    private var output: TextView? = null
    private var deviceList: LinearLayout? = null
    private var gattMapText: TextView? = null
    private var copyGattButton: Button? = null
    private var gattInventory: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val scrollView = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(body)
        container.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        body.addView(TextView(this).apply {
            text = getString(R.string.warning)
            textSize = 17f
            setPadding(0, 0, 0, 20)
        })
        body.addView(Button(this).apply {
            text = getString(R.string.scan)
            setOnClickListener {
                if (ble.hasBluetoothPermissions()) {
                    deviceList?.removeAllViews()
                    clearGattInventory()
                    ble.scan()
                } else {
                    requestPermissions(arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ), REQUEST_BLUETOOTH)
                }
            }
        })
        body.addView(Button(this).apply {
            text = getString(R.string.disconnect)
            setOnClickListener { ble.stopScan(); ble.disconnect() }
        })
        body.addView(TextView(this).apply {
            text = "Estado de la sesión YpsoPump"
            textSize = 20f
            setPadding(0, 16, 0, 8)
        })
        authStatus = TextView(this).apply {
            textSize = 17f
            text = "Bluetooth desconectado. Autenticación de la bomba no disponible."
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body.addView(authStatus)
        authCheckButton = Button(this).apply {
            text = "Comprobar disponibilidad de autenticación"
            isEnabled = false
            setOnClickListener {
                val result = authSession.beginAuthentication()
                updateAuthState(result)
            }
        }
        body.addView(authCheckButton)
        body.addView(TextView(this).apply {
            text = getString(R.string.devices)
            textSize = 20f
            setPadding(0, 16, 0, 8)
        })
        deviceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(deviceList)
        body.addView(TextView(this).apply {
            text = getString(R.string.log)
            textSize = 20f
            setPadding(0, 16, 0, 8)
        })
        output = TextView(this).apply {
            textSize = 16f
            setTextIsSelectable(true)
            text = ""
        }
        body.addView(output)
        body.addView(Button(this).apply {
            text = "Copiar registro de diagnóstico"
            contentDescription = "Copiar registro de diagnóstico Bluetooth al portapapeles"
            setOnClickListener {
                // On-demand only: the log may include the device's advertised name.
                val log = messages.joinToString("\n")
                if (log.isNotBlank()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("YpsoPump Lab - registro", log))
                    Toast.makeText(this@YpsoLabActivity, "Registro copiado", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@YpsoLabActivity, "Aún no hay mensajes", Toast.LENGTH_SHORT).show()
                }
            }
        })

        body.addView(TextView(this).apply {
            text = getString(R.string.gatt_map_title)
            textSize = 20f
            setPadding(0, 20, 0, 8)
        })
        body.addView(TextView(this).apply {
            text = getString(R.string.gatt_map_description)
            textSize = 16f
        })
        copyGattButton = Button(this).apply {
            text = getString(R.string.copy_gatt_map)
            isEnabled = false
            setOnClickListener {
                if (gattInventory.isNotBlank()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("YpsoPump Lab - mapa GATT", gattInventory))
                    Toast.makeText(this@YpsoLabActivity, R.string.gatt_map_copied, Toast.LENGTH_SHORT).show()
                }
            }
        }
        body.addView(copyGattButton)
        body.addView(Button(this).apply {
            text = "Compartir informe de laboratorio"
            contentDescription = "Compartir informe de diagnóstico Bluetooth y mapa GATT"
            setOnClickListener {
                if (gattInventory.isBlank()) {
                    Toast.makeText(this@YpsoLabActivity, "Conecta la bomba para obtener el mapa GATT", Toast.LENGTH_LONG).show()
                } else {
                    val report = buildString {
                        appendLine("YpsoPump Lab - informe de laboratorio")
                        appendLine("Solo diagnóstico; ninguna orden de administración de insulina.")
                        appendLine()
                        appendLine("REGISTRO")
                        appendLine(messages.joinToString("\n"))
                        appendLine()
                        appendLine(gattInventory)
                    }
                    // Advertised device names can include a pump identifier: redact it.
                    val redacted = report
                        .replace(Regex("YpsoPump_[A-Za-z0-9]+"), "YpsoPump_[OCULTO]")
                        .replace(Regex("\\([A-Fa-f0-9]{2}:[A-Fa-f0-9]{2}\\)"), "(XX:XX)")
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "YpsoPump Lab - informe")
                        putExtra(Intent.EXTRA_TEXT, redacted)
                    }
                    startActivity(Intent.createChooser(intent, "Compartir informe de YpsoPump Lab"))
                }
            }
        })
        gattMapText = TextView(this).apply {
            text = getString(R.string.gatt_map_waiting)
            textSize = 15f
            setTextIsSelectable(true)
        }
        body.addView(gattMapText)
        setContentView(container)
    }

    override fun onStart() {
        super.onStart()
        ble.setListeners(
            onEvent = { message -> appendMessage(message) },
            onDevice = { address, name ->
                val shortAddress = address.takeLast(5)
                deviceList?.addView(TextView(this).apply {
                    text = "$name ($shortAddress)"
                    textSize = 16f
                })
                deviceList?.addView(Button(this).apply {
                    text = getString(R.string.pair_bluetooth)
                    contentDescription = getString(R.string.pair_with_device, name, shortAddress)
                    setOnClickListener { ble.requestBluetoothBond(address) }
                })
                deviceList?.addView(Button(this).apply {
                    text = getString(R.string.connect_read_only)
                    contentDescription = getString(R.string.choose_device, name, shortAddress)
                    setOnClickListener {
                        clearGattInventory()
                        ble.connect(address)
                    }
                })
                appendMessage("Bomba encontrada: $name ($shortAddress)")
            },
            onLinkState = { connected ->
                if (connected) authSession.connect() else authSession.disconnect()
                updateAuthState(authSession.state())
                authCheckButton?.isEnabled = connected
            },
            onInventory = { inventory ->
                gattInventory = inventory
                gattMapText?.text = inventory
                copyGattButton?.isEnabled = true
            }
        )
    }

    private fun updateAuthState(state: YpsoAuthSession.State) {
        val description = when (state.status) {
            YpsoAuthSession.Status.DISCONNECTED -> "Bluetooth desconectado."
            YpsoAuthSession.Status.LINKED -> "Bluetooth conectado."
            YpsoAuthSession.Status.AWAITING_AUTHORIZATION -> "Autorización pendiente (solo simulación)."
            YpsoAuthSession.Status.AUTHORIZED_SIMULATION -> "Sesión simulada, sin permiso de control."
            YpsoAuthSession.Status.EXPIRED -> "Sesión simulada caducada."
            YpsoAuthSession.Status.FAILED -> when (state.failure) {
                YpsoAuthSession.Failure.PROVIDER_UNAVAILABLE -> "No existe un proveedor de autenticación autorizado."
                else -> "Autenticación no disponible: ${state.failure}."
            }
        }
        authStatus?.text = "$description Autenticación real y control de insulina deshabilitados."
    }

    private fun clearGattInventory() {
        gattInventory = ""
        gattMapText?.text = getString(R.string.gatt_map_waiting)
        copyGattButton?.isEnabled = false
    }

    private fun appendMessage(message: String) {
        if (messages.size >= 50) messages.removeFirst()
        messages.addLast(message)
        output?.text = messages.joinToString("\n")
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH) {
            if (grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED } &&
                ble.hasBluetoothPermissions()
            ) {
                deviceList?.removeAllViews()
                clearGattInventory()
                ble.scan()
            } else {
                appendMessage(getString(R.string.grant_bluetooth))
            }
        }
    }

    override fun onStop() {
        ble.setListeners(null, null, null)
        ble.stopScan()
        ble.disconnect()
        authSession.disconnect()
        authCheckButton?.isEnabled = false
        updateAuthState(authSession.state())
        super.onStop()
    }

    override fun onDestroy() {
        deviceList = null
        output = null
        gattMapText = null
        copyGattButton = null
        authStatus = null
        authCheckButton = null
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_BLUETOOTH = 804
    }
}
