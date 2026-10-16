package com.icar2.obd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ForegroundService: poll OBD + cảnh báo quá nhiệt + log hành trình + tính tiền xăng.
class ObdService : Service() {
    companion object {
        var activePids: List<PidDef> = listOf(
            ObdPids.ALL_01.first { it.pid=="0C" },
            ObdPids.ALL_01.first { it.pid=="0D" },
            ObdPids.ALL_01.first { it.pid=="05" },
            ObdPids.ALL_01.first { it.pid=="11" },
            ObdPids.ALL_01.first { it.pid=="10" },
            ObdPids.ALL_01.first { it.pid=="42" },
            ObdPids.ALL_01.first { it.pid=="5E" }, // fuel rate L/h (nếu xe hỗ trợ)
            ObdPids.ALL_01.first { it.pid=="2F" }, // fuel level
        )
        val liveData = MutableStateFlow<Map<String, Double?>>(emptyMap())
        val status = MutableStateFlow("Disconnected")
        val supported = MutableStateFlow<Set<String>>(emptySet())
        val dtcText = MutableStateFlow("")
        val vinText = MutableStateFlow("")
        // Quá nhiệt
        val overheat = MutableStateFlow(false)
        val coolantNow = MutableStateFlow<Double?>(null)
        // Hành trình
        val tripActive = MutableStateFlow(false)
        val tripKm = MutableStateFlow(0.0)
        val tripFuelL = MutableStateFlow(0.0)
        val tripCost = MutableStateFlow(0L)
        val tripLog = MutableStateFlow<List<String>>(emptyList())
        var deviceAddress: String? = null
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var client: Elm327Client? = null
    private var pollJob: Job? = null
    private var lastPollTime = 0L
    private var lastOverheatNotify = 0L
    private var logFile: File? = null
    private var tripStartDate: Date? = null
    private var emptyPolls = 0

    override fun onBind(i: Intent?): IBinder? = Binder()

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        TripHistory.refresh(tripDir())
        startForeground(1, notif("OBD service running", false))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "CONNECT" -> {
                deviceAddress = intent.getStringExtra("addr")
                scope.launch { doConnect() }
            }
            "DISCONNECT" -> scope.launch { doDisconnect() }
            "READ_DTC" -> scope.launch { dtcText.value = client?.readDtcs() ?: "not connected" }
            "CLEAR_DTC" -> scope.launch { dtcText.value = client?.clearDtcs() ?: "not connected" }
            "READ_VIN" -> scope.launch { vinText.value = client?.readVin() ?: "not connected" }
            "TRIP_START" -> startTrip()
            "TRIP_STOP" -> stopTrip()
            "TRIP_RESET" -> resetTrip()
            "TRIP_HISTORY" -> TripHistory.refresh(tripDir())
        }
        return START_STICKY
    }

    private suspend fun doConnect() {
        val addr = deviceAddress ?: return
        status.value = "Connecting $addr..."
        val bt = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        if (!bt.isEnabled) { status.value = "Bật Bluetooth trước"; return }
        client = Elm327Client(bt, addr)
        val ok = client!!.connect()
        if (!ok) { status.value = "Connect FAILED - kiểm tra V-LINK + PIN 1234"; return }
        status.value = "Connected - quét PID..."
        val sup = mutableSetOf<String>()
        for (q in ObdPids.SUPPORTED_QUERIES) {
            try {
                val raw = client!!.send("01$q")
                sup.addAll(ObdPids.decodeSupported(raw))
                delay(150)
            } catch (_: Exception) {}
        }
        supported.value = sup
        status.value = "Connected - ${sup.size} PID"
        startPoll()
        try { vinText.value = client!!.readVin() } catch (_: Exception) {}
        // TỰ ĐỘNG: mở trip mới ngay khi kết nối thành công
        if (SettingsStore.autoTrip.value && !tripActive.value) startTrip(auto = true)
        TripHistory.refresh(tripDir())
    }

    private fun startPoll() {
        pollJob?.cancel()
        lastPollTime = System.currentTimeMillis()
        emptyPolls = 0
        pollJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val dtHours = (now - lastPollTime) / 3600000.0
                lastPollTime = now
                val map = mutableMapOf<String, Double?>()
                for (pid in activePids) {
                    if (supported.value.isNotEmpty() && !supported.value.contains(pid.pid)) continue
                    try {
                        withTimeout(2500) { map[pid.pid] = client?.poll(pid) }
                    } catch (_: Exception) { map[pid.pid] = null }
                }
                liveData.value = map
                // Mất kết nối giữa chừng (rút OBD / tắt máy): toàn bộ null liên tục -> tự chốt trip
                if (map.values.all { it == null }) {
                    emptyPolls++
                    if (emptyPolls >= 12) { // ~6s không có data
                        status.value = "Mất kết nối - đã chốt trip"
                        if (tripActive.value) stopTrip(auto = true)
                        emptyPolls = 0
                    }
                } else emptyPolls = 0
                checkOverheat(map["05"])
                updateTrip(map, dtHours)
                delay(500)
            }
        }
    }

    // ---- Cảnh báo quá nhiệt (chỉ cái này, không cảnh báo tốc độ) ----
    private fun checkOverheat(coolant: Double?) {
        coolantNow.value = coolant
        val th = SettingsStore.overheatThreshold.value
        if (coolant == null) return
        if (coolant >= th && !overheat.value) {
            overheat.value = true
            // Chống spam: chỉ notify lại sau 60s
            if (System.currentTimeMillis() - lastOverheatNotify > 60000) {
                lastOverheatNotify = System.currentTimeMillis()
                notifyOverheat(coolant, th)
            }
        } else if (coolant < th - 3 && overheat.value) {
            overheat.value = false // hysteresis 3°C
            updateNotif("OBD connected - ${coolant.toInt()}°C", false)
        }
    }

    private fun notifyOverheat(coolant: Double, th: Int) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("overheat", "Cảnh báo quá nhiệt", NotificationManager.IMPORTANCE_HIGH))
        val n = NotificationCompat.Builder(this, "overheat")
            .setContentTitle("⚠ QUÁ NHIỆT ${coolant.toInt()}°C (ngưỡng ${th}°C)")
            .setContentText("Dừng xe, tắt máy, kiểm tra nước mát ngay!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(false)
            .build()
        nm.notify(2, n)
        updateNotif("⚠ QUÁ NHIỆT ${coolant.toInt()}°C", true)
    }

    // ---- Log hành trình TỰ ĐỘNG theo kết nối + tiền xăng ----
    // Mỗi lần CONNECT thành công = 1 trip mới. DISCONNECT / mất tín hiệu = chốt trip.
    private fun startTrip(auto: Boolean = false) {
        // Nếu đang ghi dở mà connect lại -> chốt cái cũ trước
        if (tripActive.value) stopTrip(auto = true)
        val dir = File(getExternalFilesDir(null), "trips").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        logFile = File(dir, "trip_$ts.csv").apply {
            writeText("time,speed_kmh,rpm,coolant_c,maf_gs,fuelRate_Lh,fuelLevel_pct,voltage,km_cum,fuelL_cum\n")
        }
        tripStartDate = Date()
        tripKm.value = 0.0; tripFuelL.value = 0.0; tripCost.value = 0L
        tripLog.value = emptyList()
        tripActive.value = true
        status.value = if (auto) "Connected - tự động ghi trip $ts" else "Trip recording..."
    }

    private fun stopTrip(auto: Boolean = false) {
        if (!tripActive.value && logFile == null) return
        tripActive.value = false
        try {
            val dir = tripDir()
            val end = Date()
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val id = logFile?.nameWithoutExtension?.removePrefix("trip_") ?: "unknown"
            File(dir, "trip_${id}_summary.txt").writeText(
                "id=$id\nstart=${tripStartDate?.let { fmt.format(it) } ?: "--"}\n" +
                "end=${fmt.format(end)}\nkm=${tripKm.value}\nfuel=${tripFuelL.value}\n" +
                "cost=${tripCost.value}\ncsv=${logFile?.name ?: ""}\n"
            )
            TripHistory.refresh(dir)
            status.value = "Đã chốt trip ${String.format("%.1f km • %.2f L • %,d đ", tripKm.value, tripFuelL.value, tripCost.value)}"
        } catch (_: Exception) {}
    }

    private fun resetTrip() {
        tripActive.value = false
        tripKm.value = 0.0; tripFuelL.value = 0.0; tripCost.value = 0L
        tripLog.value = emptyList()
    }

    private fun updateTrip(map: Map<String, Double?>, dtHours: Double) {
        if (!tripActive.value || dtHours <= 0 || dtHours > 0.01) return // bỏ qua dt bất thường
        val speed = map["0D"] ?: 0.0
        val dKm = speed * dtHours
        // Xăng: ưu tiên PID 5E (L/h), fallback ước lượng từ MAF: fuel = MAF/14.7/720*3600 L/h
        val fuelRateLh = map["5E"] ?: run {
            val maf = map["10"]
            if (maf != null && maf > 0) maf / 14.7 / 0.72 else 0.0
        }
        val dFuel = fuelRateLh * dtHours
        if (dKm in 0.0..2.0 && dFuel in 0.0..1.0) {
            tripKm.value += dKm
            tripFuelL.value += dFuel
            tripCost.value = (tripFuelL.value * SettingsStore.fuelPrice.value).toLong()
        }
        // Ghi CSV mỗi vòng
        try {
            val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            logFile?.appendText("$ts,${map["0D"]},${map["0C"]},${map["05"]},${map["10"]},${map["5E"]},${map["2F"]},${map["42"]},${tripKm.value},${tripFuelL.value}\n")
            val line = "$ts • ${speed.toInt()}km/h • ${String.format("%.1f", tripKm.value)}km • ${String.format("%.2f", tripFuelL.value)}L"
            tripLog.value = (tripLog.value + line).takeLast(50)
        } catch (_: Exception) {}
    }

    fun tripDir(): File = File(getExternalFilesDir(null), "trips").apply { mkdirs() }

    private suspend fun doDisconnect() {
        // Ngắt kết nối = chốt trip tự động, giữ số liệu lại để xem, không reset
        if (tripActive.value) stopTrip(auto = true)
        pollJob?.cancel(); client?.close(); status.value = "Disconnected"
    }

    private fun notif(text: String, alert: Boolean): Notification {
        val ch = "obd"
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel(ch, "OBD", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, ch)
            .setContentTitle("iCar2 OBD").setContentText(text)
            .setSmallIcon(if (alert) android.R.drawable.ic_dialog_alert else android.R.drawable.ic_menu_compass)
            .build()
    }

    private fun updateNotif(text: String, alert: Boolean) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, notif(text, alert))
    }

    override fun onDestroy() {
        try { if (tripActive.value) stopTrip(auto = true) } catch (_: Exception) {}
        scope.cancel(); super.onDestroy()
    }
}
