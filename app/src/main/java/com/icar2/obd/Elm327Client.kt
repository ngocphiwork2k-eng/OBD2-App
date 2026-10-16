package com.icar2.obd

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

// Client ELM327 cho vGate iCar2 BT3.0 (Bluetooth Classic SPP).
// Pair system trước với V-LINK / pass 1234.
class Elm327Client(
    private val adapter: BluetoothAdapter,
    private val deviceAddress: String
) {
    companion object {
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val TAG = "iCar2"
    }

    private var socket: BluetoothSocket? = null
    private var ins: InputStream? = null
    private var outs: OutputStream? = null
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            adapter.cancelDiscovery()
            val dev = adapter.getRemoteDevice(deviceAddress)
            socket = dev.createRfcommSocketToServiceRecord(SPP)
            socket!!.connect()
            ins = socket!!.inputStream
            outs = socket!!.outputStream
            initElm()
            true
        } catch (e: Exception) {
            e.printStackTrace(); false
        }
    }

    private suspend fun initElm(): Boolean {
        // Sequence chuẩn cho clone v2.1. Delay giữa lệnh để ổn định.
        val cmds = listOf("ATZ","ATE0","ATL0","ATS0","ATH1","ATAL","ATSP0")
        for (c in cmds) { sendRaw(c); delay(250) }
        val r = send("0100")
        return r.contains("41 00", ignoreCase = true)
    }

    @Synchronized
    private fun sendRaw(cmd: String): String {
        val out = outs ?: return ""
        val inp = ins ?: return ""
        // Xóa buffer cũ
        try { while (inp.available() > 0) inp.read() } catch (_: Exception) {}
        out.write((cmd + "\r").toByteArray())
        out.flush()
        return readUntilPrompt(inp)
    }

    suspend fun send(cmd: String): String = withContext(Dispatchers.IO) { sendRaw(cmd) }

    private fun readUntilPrompt(inp: InputStream): String {
        val sb = StringBuilder()
        val buf = ByteArray(1)
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            try {
                if (inp.available() > 0) {
                    val n = inp.read(buf)
                    if (n > 0) {
                        val ch = buf[0].toInt().toChar()
                        if (ch == '>') break
                        sb.append(ch)
                    }
                } else Thread.sleep(20)
            } catch (_: Exception) { break }
        }
        return sb.toString().replace("SEARCHING...","").trim()
    }

    suspend fun readVin(): String {
        // Mode 09 VIN trả về multi-frame, cần nối ASCII.
        val r = send("0902")
        val hex = r.split(Regex("[^0-9A-Fa-f]")).filter { it.length==2 }
        // Bỏ header 49 02 xx, lấy byte ASCII
        return try {
            hex.windowed(2).firstOrNull { it[0]=="49" && it[1]=="02" }
                ?.let {
                    val idx = r.indexOf("49 02")
                    r.substring(idx).split(Regex("\\s+"))
                        .mapNotNull { it.toIntOrNull(16) }
                        .drop(3) // 49 02 xx
                        .filter { it in 32..126 }
                        .map { it.toChar() }.joinToString("")
                } ?: r
        } catch (_: Exception) { r }
    }

    suspend fun readDtcs(): String = send("03")
    suspend fun clearDtcs(): String = send("04")

    fun close() { try { socket?.close() } catch (_: Exception) {} }

    // Poll 1 PID, trả về giá trị parse sẵn
    suspend fun poll(def: PidDef): Double? {
        val raw = send("${def.mode}${def.pid}")
        return ObdPids.parseFrame(raw)[def.pid]
    }
}
