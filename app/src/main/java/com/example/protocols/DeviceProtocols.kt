package com.example.protocols

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real binary frame representations and state systems modeling actual hardware servicing.
 */

// 1. Qualcomm EDL (Emergency Download Mode) Sahara Protocol
object SaharaProtocol {
    // Command Constants
    const val SAHARA_CMD_HELLO = 0x01
    const val SAHARA_CMD_HELLO_RESP = 0x02
    const val SAHARA_CMD_READ = 0x03
    const val SAHARA_CMD_WRITE = 0x04
    const val SAHARA_CMD_END = 0x05
    const val SAHARA_CMD_RESET = 0x06
    const val SAHARA_CMD_READY = 0x09
    const val SAHARA_CMD_SWITCH_MODE = 0x0C

    // Protocol Constants
    const val SAHARA_VERSION_2 = 2
    const val SAHARA_SUCCESS = 0x00

    /**
     * Build standard EDL Sahara Hello Response binary packet containing matching memory region configs
     */
    fun buildHelloResponse(mode: Int = 0): ByteArray {
        val buffer = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(SAHARA_CMD_HELLO_RESP) // Header command
        buffer.putInt(48)                    // Packet length
        buffer.putInt(SAHARA_VERSION_2)      // Sahara Version
        buffer.putInt(SAHARA_VERSION_2)      // Compatible Version
        buffer.putInt(statusToResCode(SAHARA_SUCCESS)) // Status success
        buffer.putInt(mode)                  // Mode
        // Pad out the secure hardware token alignment registers
        for (i in 0..5) {
            buffer.putInt(0)
        }
        return buffer.array()
    }

    private fun statusToResCode(status: Int): Int = status
}

// 2. Spreadtrum / Unisoc FDL (First Download Loader) HDLC Handshake Protocol
object FdlProtocol {
    // HDLC Byte boundaries
    const val HDLC_FLAG = 0x7E
    const val HDLC_ESCAPE = 0x7D
    const val HDLC_ESCAPE_BYTE = 0x20

    // FDL Commands
    const val BSL_CMD_CONNECT = 0x05
    const val BSL_REP_ACK = 0x80
    const val BSL_CMD_START_DATA = 0x10
    const val BSL_CMD_MID_DATA = 0x11
    const val BSL_CMD_END_DATA = 0x12
    const val BSL_CMD_EXEC_DATA = 0x13

    /**
     * Frame raw servicing command into Spreadtrum FDL HDLC compatible packets
     */
    fun buildHdlcFrame(commandType: Int, data: ByteArray): ByteArray {
        val payload = ByteBuffer.allocate(8 + data.size).order(ByteOrder.BIG_ENDIAN)
        payload.putShort(commandType.toShort())
        payload.putShort(data.size.toShort())
        payload.put(data)

        // Generate CRC-16 checksum over payload
        val crc = calculateCrc16(payload.array(), 0, payload.position())
        payload.putShort(crc)

        // Escape specialized bytes inside packets
        val escaped = mutableListOf<Byte>()
        escaped.add(HDLC_FLAG.toByte())

        val rawBytes = payload.array().copyOf(payload.position())
        for (b in rawBytes) {
            val iVal = b.toInt() and 0xFF
            if (iVal == HDLC_FLAG || iVal == HDLC_ESCAPE) {
                escaped.add(HDLC_ESCAPE.toByte())
                escaped.add((iVal xor HDLC_ESCAPE_BYTE).toByte())
            } else {
                escaped.add(b)
            }
        }
        escaped.add(HDLC_FLAG.toByte())
        return escaped.toByteArray()
    }

    private fun calculateCrc16(bytes: ByteArray, offset: Int, length: Int): Short {
        var crc = 0xFFFF
        for (i in offset until (offset + length)) {
            val byteVal = bytes[i].toInt() and 0xFF
            crc = crc xor (byteVal shl 8)
            for (j in 0..7) {
                if (crc and 0x8000 != 0) {
                    crc = (crc shl 1) xor 0x1021
                } else {
                    crc = crc shl 1
                }
            }
        }
        return (crc and 0xFFFF).toShort()
    }
}

// 3. MediaTek MTK BootROM Protocol
object MtkBootromProtocol {
    // BootROM Command Bytes
    const val MTK_CMD_GET_VERSION = 0x30
    const val MTK_CMD_READ32 = 0xD1
    const val MTK_CMD_WRITE32 = 0xD4
    const val MTK_CMD_REBOOT = 0xD9
    const val MTK_CMD_SEND_DA = 0xD7
    const val MTK_CMD_RUN_DA = 0x3E

    // Handshake sequences
    val MTK_START_SEQUENCE = byteArrayOf(0xA0.toByte(), 0x0A.toByte(), 0x50.toByte(), 0x05.toByte())

    /**
     * Format register writing stream payload to configure custom clock/power dividers on MTK lines
     */
    fun buildRegisterWrite(address: Long, value: Long): ByteArray {
        val buffer = ByteBuffer.allocate(11).order(ByteOrder.BIG_ENDIAN)
        buffer.put(MTK_CMD_WRITE32.toByte())
        buffer.putInt(address.toInt())
        buffer.putInt(1) // Number of words
        buffer.putInt(value.toInt())
        return buffer.array()
    }
}

// 4. USB DFU (Device Firmware Upgrade) Protocol Specification
object DfuProtocol {
    // DFU Request Codes
    const val DFU_DETACH = 0
    const val DFU_DNLOAD = 1
    const val DFU_UPLOAD = 2
    const val DFU_GETSTATUS = 3
    const val DFU_CLRSTATUS = 4
    const val DFU_GETSTATE = 5
    const val DFU_ABORT = 6

    // DFU State Codes
    const val STATE_DFU_IDLE = 2
    const val STATE_DFU_DNLOAD_IDLE = 5
    const val STATE_DFU_MANIFEST = 7
    const val STATE_DFU_ERROR = 10

    /**
     * Representation of actual USB control request transfers used to flash files to DFU targets.
     */
    data class DfuControlTransfer(
        val requestType: Int, // 0x21 for host to device Class request
        val request: Int,
        val value: Int,       // typically block number
        val index: Int,       // interface id
        val data: ByteArray
    )
}

// 5. Fastboot Flashing Protocol
object FastbootProtocol {
    /**
     * Generate standard fastboot message format e.g. "flash:system" or "reboot-bootloader"
     */
    fun buildFastbootCommand(command: String): ByteArray {
        val commandBytes = command.toByteArray(Charsets.US_ASCII)
        val buffer = ByteArray(64) // Command packet boundary
        System.arraycopy(commandBytes, 0, buffer, 0, commandBytes.size.coerceAtMost(64))
        return buffer
    }

    /**
     * Parse returned Fastboot buffer status response. Fastboot outputs strictly 4-char headers.
     */
    sealed class response {
        data class Info(val message: String) : response()
        data class Okay(val data: String) : response()
        data class Fail(val reason: String) : response()
        data class Data(val size: Int) : response()
    }

    fun parseResponse(reply: ByteArray): response {
        val replyString = reply.toString(Charsets.US_ASCII).trim()
        if (replyString.length < 4) return response.Fail("Invalid Short Response")
        val status = replyString.substring(0, 4)
        val details = replyString.substring(4)
        return when (status) {
            "INFO" -> response.Info(details)
            "OKAY" -> response.Okay(details)
            "FAIL" -> response.Fail(details)
            "DATA" -> response.Data(details.toIntOrNull(16) ?: 0)
            else -> response.Fail("Unknown Status: $status")
        }
    }
}

// 6. Direct ADB Channel Handshake
object AdbProtocol {
    // ADB Header size is 24 bytes
    const val ADB_HEADER_SIZE = 24

    // Commands
    const val A_SYNC = 0x434e5953
    const val A_CNXN = 0x4e584e43
    const val A_OPEN = 0x4e45504f
    const val A_OKAY = 0x59414b4f
    const val A_CLSE = 0x45534c43
    const val A_WRTE = 0x45545257

    /**
     * Build ADB transaction Packet with exact byte check-summing requirements
     */
    fun buildAdbMessage(command: Int, arg0: Int, arg1: Int, data: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(ADB_HEADER_SIZE + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(command)
        buffer.putInt(arg0)
        buffer.putInt(arg1)
        buffer.putInt(data.size)
        buffer.putInt(calculateDataChecksum(data))
        buffer.putInt(command xor -0x1) // magic check
        buffer.put(data)
        return buffer.array()
    }

    private fun calculateDataChecksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) {
            sum += (b.toInt() and 0xFF)
        }
        return sum
    }
}
