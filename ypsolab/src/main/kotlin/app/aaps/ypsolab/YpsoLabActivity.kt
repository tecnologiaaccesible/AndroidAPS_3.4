package app.aaps.ypsolab

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
    private var output: TextView? = null
    private var deviceList: LinearLayout? = null

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
                    setOnClickListener { ble.connect(address) }
                })
                appendMessage("Bomba encontrada: $name ($shortAddress)")
            }
        )
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
                ble.scan()
            } else {
                appendMessage(getString(R.string.grant_bluetooth))
            }
        }
    }

    override fun onStop() {
        ble.setListeners(null, null)
        ble.stopScan()
        ble.disconnect()
        super.onStop()
    }

    override fun onDestroy() {
        deviceList = null
        output = null
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_BLUETOOTH = 804
    }
}
