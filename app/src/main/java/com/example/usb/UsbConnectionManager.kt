package com.example.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.*
import android.util.Log
import com.example.protocols.*
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

class UsbConnectionManager(private val context: Context) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    companion object {
        const val ACTION_USB_PERMISSION = "com.example.usb.USB_PERMISSION"
        private const val TAG = "UsbConnectionManager"

        // Common Servicing USB Identifiers
        val KNOWN_VENDORS = mapOf(
            0x18D1 to "Google/Android Dev",
            0x05C6 to "Qualcomm EDL/Sahara Diagnostics",
            0x0E8D to "MediaTek MTK Preloader/BROM",
            0x1782 to "Spreadtrum/Unisoc Diagnostics/FDL",
            0x0483 to "STM Microelectronics (DFU Mod)",
            0x05AC to "Apple Recovery/DFU"
        )
    }

    /**
     * Enumerate all currently attached USB devices via Host API
     */
    fun findConnectedDevices(): List<UsbDevice> {
        val deviceList = usbManager.deviceList
        return deviceList.values.toList()
    }

    /**
     * Request target system connection permission for a specific hardware device
     */
    fun requestPermission(device: UsbDevice, onResult: (Boolean) -> Unit) {
        if (usbManager.hasPermission(device)) {
            onResult(true)
            return
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    /**
     * Parse Vendor and Product details to match specific Servicing protocol mode
     */
    fun matchProtocol(device: UsbDevice): String {
        val vendorId = device.vendorId
        val productId = device.productId
        
        Log.d(TAG, "Hardware Detected: VID=0x${Integer.toHexString(vendorId)}, PID=0x${Integer.toHexString(productId)}")

        return when {
            // MediaTek Devices matching preloader and bootrom IDs
            vendorId == 0x0E8D -> "BootROM (MediaTek)"
            // Qualcomm EDL Mode
            vendorId == 0x05C6 && (productId == 0x9008 || productId == 0x9006) -> "EDL (Qualcomm)"
            // Spreadtrum/Unisoc
            vendorId == 0x1782 && (productId == 0x4D00 || productId == 0x5D00) -> "FDL (Unisoc)"
            // STM and general DFU
            vendorId == 0x0483 && productId == 0xDF11 -> "DFU"
            // Fastboot endpoints commonly present under class/subclass boundaries
            hasFastbootInterface(device) -> "Fastboot (Bootloader)"
            // ADB Debug Bridge Interface
            hasAdbInterface(device) -> "ADB (Debug Bridge)"
            else -> "Generic USB Diagnostics"
        }
    }

    private fun hasFastbootInterface(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val intrf = device.getInterface(i)
            // Fastboot class configurations
            if (intrf.interfaceClass == 0xFF && intrf.interfaceSubclass == 0x42 && intrf.interfaceProtocol == 0x03) {
                return true
            }
        }
        return false
    }

    private fun hasAdbInterface(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val intrf = device.getInterface(i)
            // ADB class configurations
            if (intrf.interfaceClass == 0xFF && intrf.interfaceSubclass == 0x42 && intrf.interfaceProtocol == 0x01) {
                return true
            }
        }
        return false
    }

    /**
     * Perform direct Raw USB Endpoint communication for ADB, Fastboot or EDL protocols
     */
    fun performBulkTransfer(
        device: UsbDevice,
        command: ByteArray,
        timeoutMs: Int = 1000
    ): ByteArray? {
        val connection = usbManager.openDevice(device) ?: return null
        try {
            // Locate endpoints
            var bulkOut: UsbEndpoint? = null
            var bulkIn: UsbEndpoint? = null
            var targetInterface: UsbInterface? = null

            for (i in 0 until device.interfaceCount) {
                val intrf = device.getInterface(i)
                for (j in 0 until intrf.endpointCount) {
                    val ep = intrf.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_OUT) {
                            bulkOut = ep
                            targetInterface = intrf
                        } else if (ep.direction == UsbConstants.USB_DIR_IN) {
                            bulkIn = ep
                        }
                    }
                }
                if (bulkOut != null && bulkIn != null) break
            }

            if (targetInterface == null || bulkOut == null || bulkIn == null) {
                Log.e(TAG, "Failed to resolve direct Bulk Endpoints on USB line")
                return null
            }

            // Claim exclusivity
            connection.claimInterface(targetInterface, true)

            // Direct transfer
            val outReturn = connection.bulkTransfer(bulkOut, command, command.size, timeoutMs)
            if (outReturn < 0) {
                Log.e(TAG, "Bulk Write Transfer Failed: $outReturn")
                return null
            }

            // Await device response
            val readBuffer = ByteArray(4096)
            val inReturn = connection.bulkTransfer(bulkIn, readBuffer, readBuffer.size, timeoutMs)

            if (inReturn >= 0) {
                return readBuffer.copyOf(inReturn)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing native transfer packet: ${e.message}")
        } finally {
            connection.close()
        }
        return null
    }

    /**
     * Perform specialized Serial Driver operations using usb-serial-for-android
     */
    fun performSerialHandshake(
        device: UsbDevice,
        baudRate: Int = 115200,
        handshakeData: ByteArray,
        onStatus: (String) -> Unit
    ): ByteArray? {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val driver = drivers.firstOrNull { it.device.deviceId == device.deviceId } ?: return null
        
        val connection = usbManager.openDevice(device) ?: return null
        val port = driver.ports.getOrNull(0) ?: return null

        try {
            port.open(connection)
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            
            onStatus("Serial interface opened. Sending handshakes...")
            port.write(handshakeData, 1000)

            val reply = ByteArray(512)
            val readCount = port.read(reply, 1000)
            if (readCount > 0) {
                return reply.copyOf(readCount)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Serial handshake communication failure: ${e.message}")
            onStatus("Failure: ${e.message}")
        } finally {
            try {
                port.close()
            } catch (ignored: Exception) {}
        }
        return null
    }

    /**
     * DFU Mode direct memory operations via USB Control transfers (Endpoint 0)
     */
    fun performDfuControl(
        device: UsbDevice,
        transfer: DfuProtocol.DfuControlTransfer,
        timeoutMs: Int = 3000
    ): Int {
        val connection = usbManager.openDevice(device) ?: return -1
        try {
            // Find interface ID for control endpoint indexing
            val interfaceIndex = 0 // typically 0 for Class control
            return connection.controlTransfer(
                transfer.requestType,
                transfer.request,
                transfer.value,
                transfer.index,
                transfer.data,
                transfer.data.size,
                timeoutMs
            )
        } finally {
            connection.close()
        }
    }
}
