package app.aaps.pump.ypsopump.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only BLE diagnostics for a disconnected, off-body YpsoPump test device.
 *
 * No Bluetooth characteristic is ever written and no insulin-delivery protocol
 * is implemented here. No pairing key, encrypted commands, or pump control.
 */
@Singleton
class YpsoBleDiagnostics @Inject constructor(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val devices = mutableMapOf<String, BluetoothDevice>()
    private val discovered = mutableSetOf<String>()
    private val pendingReads = ArrayDeque<Pair<String, BluetoothGattCharacteristic>>()
    private var scannerCallback: ScanCallback? = null
    private var connection: BluetoothGatt? = null

    @Volatile private var eventListener: ((String) -> Unit)? = null
    @Volatile private var deviceListener: ((String, String) -> Unit)? = null

    fun setListeners(onEvent: ((String) -> Unit)?, onDevice: ((String, String) -> Unit)?) {
        eventListener = onEvent
        deviceListener = onDevice
    }

    private fun report(message: String) {
        mainHandler.post { eventListener?.invoke(message) }
    }

    private fun hasPermission(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun hasBluetoothPermissions(): Boolean =
        hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)

    @SuppressLint("MissingPermission")
    fun scan() {
        if (!hasBluetoothPermissions()) {
            report("Faltan los permisos Bluetooth.")
            return
        }
        stopScan()
        synchronized(devices) {
            devices.clear()
            discovered.clear()
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter?.isEnabled != true) {
            report("Activa Bluetooth para buscar bombas.")
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            report("El teléfono no ofrece escaneo Bluetooth LE.")
            return
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!hasBluetoothPermissions()) return
                val device = result.device
                val name = result.scanRecord?.deviceName ?: device.name
                if (!YpsoBleUuids.isCandidateName(name)) return
                val address = device.address
                val fresh = synchronized(devices) {
                    devices[address] = device
                    discovered.add(address)
                }
                if (fresh) {
                    mainHandler.post { deviceListener?.invoke(address, name ?: "YpsoPump") }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                report("Error en escaneo BLE: $errorCode")
                scannerCallback = null
            }
        }
        try {
            scannerCallback = callback
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            report("Buscando YpsoPump. Selecciona manualmente el dispositivo.")
            mainHandler.postDelayed({
                if (scannerCallback === callback) stopScan()
            }, 20_000L)
        } catch (e: SecurityException) {
            scannerCallback = null
            report("Sin permiso para buscar dispositivos Bluetooth.")
        } catch (e: IllegalStateException) {
            scannerCallback = null
            report("No se pudo iniciar el escaneo Bluetooth.")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val callback = scannerCallback ?: return
        scannerCallback = null
        if (!hasBluetoothPermissions()) return
        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: SecurityException) {
            report("No se pudo detener el escaneo.")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (!hasBluetoothPermissions()) {
            report("Faltan los permisos Bluetooth.")
            return
        }
        val device = synchronized(devices) { devices[address] }
        if (device == null) {
            report("El dispositivo ya no está disponible. Escanea de nuevo.")
            return
        }
        stopScan()
        disconnect()
        try {
            report("Conectando al dispositivo seleccionado (solo lectura)...")
            connection = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (_: SecurityException) {
            report("Permiso Bluetooth denegado.")
        } catch (_: IllegalStateException) {
            report("No se pudo iniciar la conexión GATT.")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val gatt = connection
        connection = null
        synchronized(pendingReads) { pendingReads.clear() }
        if (gatt != null) {
            try {
                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) gatt.disconnect()
            } catch (_: SecurityException) {
                // Closing is required even if the Android permission was revoked.
            } finally {
                gatt.close()
            }
            report("Desconectado.")
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                report("Enlace BLE establecido. Descubriendo servicios.")
                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    if (!gatt.discoverServices()) report("Falló el inicio del descubrimiento GATT.")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                report("Conexión BLE terminada (estado $status).")
                synchronized(pendingReads) { pendingReads.clear() }
                if (connection === gatt) connection = null
                gatt.close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                report("No se pudieron descubrir los servicios: $status")
                return
            }
            val services = gatt.services
            report("Servicios GATT encontrados: ${services.size}")
            val found = services.flatMap { it.characteristics }.associateBy { it.uuid }
            synchronized(pendingReads) {
                pendingReads.clear()
                YpsoBleUuids.readableCharacteristics.forEach { (uuid, label) ->
                    val characteristic = found[uuid]
                    if (characteristic != null &&
                        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
                    ) pendingReads.addLast(label to characteristic)
                }
            }
            report("Lecturas sin autenticación disponibles: ${pendingReads.size}")
            readNext(gatt)
        }

        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // Older Android versions use this callback.
            if (android.os.Build.VERSION.SDK_INT < 33) {
                handleRead(gatt, characteristic.uuid, characteristic.value, status)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
            value: ByteArray, status: Int
        ) {
            handleRead(gatt, characteristic.uuid, value, status)
        }
    }

    private fun handleRead(gatt: BluetoothGatt, uuid: UUID, bytes: ByteArray?, status: Int) {
        val label = YpsoBleUuids.readableCharacteristics[uuid] ?: "Característica"
        if (status == BluetoothGatt.GATT_SUCCESS && bytes != null) {
            val isText = uuid in setOf(YpsoBleUuids.manufacturer, YpsoBleUuids.model,
                YpsoBleUuids.serial, YpsoBleUuids.firmware, YpsoBleUuids.software)
            val decoded = if (isText) String(bytes, StandardCharsets.UTF_8).trimEnd('\u0000')
                else bytes.joinToString(".") { (it.toInt() and 255).toString() }
            report("$label: $decoded")
        } else {
            report("No se pudo leer $label (GATT: $status)")
        }
        readNext(gatt)
    }

    @SuppressLint("MissingPermission")
    private fun readNext(gatt: BluetoothGatt) {
        val next = synchronized(pendingReads) {
            if (pendingReads.isEmpty()) null else pendingReads.removeFirst()
        }
        if (next == null) {
            report("Lectura de información Bluetooth completada.")
            return
        }
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            report("Permiso de conexión revocado.")
            return
        }
        try {
            if (!gatt.readCharacteristic(next.second)) {
                report("No se pudo solicitar: ${next.first}")
                readNext(gatt)
            }
        } catch (_: SecurityException) {
            report("Lectura denegada por Android.")
        }
    }
}
