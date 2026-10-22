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
    private var bondSession: Int = 0

    /** BLE transport state only; never implies pump authorization or insulin capability. */
    @Volatile var linkConnected: Boolean = false
        private set

    @Volatile private var eventListener: ((String) -> Unit)? = null
    @Volatile private var deviceListener: ((String, String) -> Unit)? = null
    @Volatile private var inventoryListener: ((String) -> Unit)? = null
    @Volatile private var linkListener: ((Boolean) -> Unit)? = null

    fun setListeners(
        onEvent: ((String) -> Unit)?,
        onDevice: ((String, String) -> Unit)?,
        onInventory: ((String) -> Unit)? = null,
        onLinkState: ((Boolean) -> Unit)? = null
    ) {
        eventListener = onEvent
        deviceListener = onDevice
        inventoryListener = onInventory
        linkListener = onLinkState
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

    /**
     * Explicit, user-initiated Android Bluetooth bonding.
     *
     * Android owns the six-digit passkey prompt. This operation does NOT perform
     * YpsoPump application-layer authentication or authorize any insulin control.
     * A separate button in the lab UI deliberately keeps bonding opt-in.
     */
    @SuppressLint("MissingPermission")
    fun requestBluetoothBond(address: String) {
        if (!hasBluetoothPermissions()) {
            report("Faltan permisos para vincular por Bluetooth.")
            return
        }
        val device = synchronized(devices) { devices[address] }
        if (device == null) {
            report("Dispositivo no encontrado; vuelve a buscar la bomba.")
            return
        }
        stopScan()
        disconnect()
        try {
            when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> {
                    report("Bluetooth: dispositivo ya vinculado en Android. No implica autenticación con la bomba.")
                }
                BluetoothDevice.BOND_BONDING -> {
                    report("Vinculación Bluetooth en curso. Consulta el aviso de Android e introduce el código de seis cifras de la bomba.")
                    observeBondState(device)
                }
                else -> {
                    report("Solicitando vinculación Bluetooth a Android; confirma la solicitud en la bomba e introduce en el teléfono su código de seis cifras.")
                    if (device.createBond()) {
                        observeBondState(device)
                    } else {
                        report("Android no ha podido iniciar la vinculación.")
                    }
                }
            }
        } catch (_: SecurityException) {
            report("Android ha rechazado el acceso Bluetooth.")
        } catch (_: IllegalStateException) {
            report("No se pudo iniciar la vinculación Bluetooth.")
        }
    }

    /**
     * Poll state only for UI diagnostics, never handle or store a passkey.
     * Some Android implementations hide the app while showing the system dialog.
     */
    @SuppressLint("MissingPermission")
    private fun observeBondState(device: BluetoothDevice) {
        val session = ++bondSession
        mainHandler.postDelayed(object : Runnable {
            var attempts = 0
            var observedBonding = false

            override fun run() {
                if (session != bondSession) return
                if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
                attempts++
                try {
                    when (device.bondState) {
                        BluetoothDevice.BOND_BONDED -> {
                            report("Vinculación Bluetooth completada. La autenticación cifrada del protocolo YpsoPump sigue sin implementarse.")
                            return
                        }
                        BluetoothDevice.BOND_BONDING -> observedBonding = true
                        BluetoothDevice.BOND_NONE -> {
                            if (observedBonding || attempts >= 10) {
                                report("Vinculación no completada o cancelada. Repite el procedimiento desde la bomba.")
                                return
                            }
                        }
                    }
                    if (attempts >= 60) {
                        report("Tiempo de espera de vinculación agotado.")
                        return
                    }
                    mainHandler.postDelayed(this, 1_000L)
                } catch (_: SecurityException) {
                    report("No se puede consultar el estado de vinculación Bluetooth.")
                }
            }
        }, 1_000L)
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
        linkConnected = false
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
            mainHandler.post { linkListener?.invoke(false) }
            report("Desconectado.")
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            // Android can deliver callbacks after a user disconnects or changes device.
            // A stale callback must never reactivate the diagnostic link or start reads.
            if (connection !== gatt) {
                gatt.close()
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                linkConnected = true
                mainHandler.post { linkListener?.invoke(true) }
                report("Enlace BLE establecido. Descubriendo servicios.")
                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    if (!gatt.discoverServices()) report("Falló el inicio del descubrimiento GATT.")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                linkConnected = false
                mainHandler.post { linkListener?.invoke(false) }
                report("Conexión BLE terminada (estado $status).")
                synchronized(pendingReads) { pendingReads.clear() }
                if (connection === gatt) connection = null
                gatt.close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (connection !== gatt || !linkConnected) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                report("No se pudieron descubrir los servicios: $status")
                return
            }
            val services = gatt.services
            val metadata = services.map { service ->
                YpsoGattInventory.Service(
                    service.uuid,
                    service.characteristics.map { characteristic ->
                        YpsoGattInventory.Characteristic(
                            characteristic.uuid,
                            characteristic.properties,
                            characteristic.descriptors.size
                        )
                    }
                )
            }
            val inventory = YpsoGattInventory.format(metadata)
            mainHandler.post { inventoryListener?.invoke(inventory) }
            report("Mapa GATT disponible: servicios, características y propiedades (sin comandos).")
            report("Servicios GATT encontrados: ${services.size}")
            val bonded = try {
                gatt.device.bondState == BluetoothDevice.BOND_BONDED
            } catch (_: SecurityException) { false }
            report(if (bonded) {
                "Estado Android: Bluetooth vinculado. Sesión cifrada YpsoPump: aún no implementada."
            } else {
                "Estado Android: Bluetooth no vinculado. Sesión cifrada YpsoPump: no disponible."
            })
            val found = services.flatMap { it.characteristics }.associateBy { it.uuid }
            val authorizationUuid = UUID.fromString("669a0c20-0008-969e-e211-fcbeb2147bc5")
            val authorization = found[authorizationUuid]
            report(if (authorization == null) {
                "Diagnóstico: característica de autorización del protocolo no encontrada."
            } else {
                val readable = authorization.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
                val writable = authorization.properties and (
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                ) != 0
                "Diagnóstico: característica de autorización presente (lectura: $readable, escritura: $writable). No se enviarán comandos."
            })
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
        if (connection !== gatt || !linkConnected) return
        val label = YpsoBleUuids.readableCharacteristics[uuid] ?: "Característica"
        if (status == BluetoothGatt.GATT_SUCCESS && bytes != null) {
            val decoded = YpsoBleUuids.formatReadOnlyValue(bytes)
            report("$label: $decoded")
        } else {
            report("No se pudo leer $label (GATT: $status)")
        }
        readNext(gatt)
    }

    @SuppressLint("MissingPermission")
    private fun readNext(gatt: BluetoothGatt) {
        if (connection !== gatt || !linkConnected) return
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
