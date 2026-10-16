package com.icar2.obd

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*

// Màn xe: 3 trang + cảnh báo quá nhiệt đỏ. Nhạc/radio phát nền bình thường.
class ObdCarScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null
    private var page = 0 // 0: chính, 1: trip+tiền, 2: lỗi/VIN
    private var lastToastOverheat = 0L

    init { lifecycle.addObserver(this) }

    override fun onResume(owner: LifecycleOwner) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                // Toast quá nhiệt trên màn xe (tối đa 1 lần/30s để đỡ phiền)
                val oh = ObdService.overheat.value
                if (oh && System.currentTimeMillis() - lastToastOverheat > 30000) {
                    lastToastOverheat = System.currentTimeMillis()
                    try {
                        CarToast.makeText(carContext,
                            "⚠ QUÁ NHIỆT ${ObdService.coolantNow.value?.toInt()}°C - kiểm tra ngay!",
                            CarToast.LENGTH_LONG).show()
                    } catch (_: Exception) {}
                }
                invalidate()
                delay(1000)
            }
        }
    }

    override fun onPause(owner: LifecycleOwner) { job?.cancel() }

    override fun onGetTemplate(): Template {
        val live = ObdService.liveData.value
        val status = ObdService.status.value
        val isOh = ObdService.overheat.value
        val th = SettingsStore.overheatThreshold.value
        val title = if (isOh) "⚠ QUÁ NHIỆT ${ObdService.coolantNow.value?.toInt()}°C!" else "iCar2 OBD • $status"

        fun item(t: String, pid: String, fmt: (Double?) -> String): GridItem =
            GridItem.Builder().setTitle(t).setText(fmt(live[pid])).build()

        val grid = ItemList.Builder()
        if (page == 0) {
            grid.addItem(item("Tốc độ", "0D") { v -> if (v==null) "-- km/h" else "${v.toInt()} km/h" })
            grid.addItem(item("Vòng tua", "0C") { v -> if (v==null) "-- rpm" else "${v.toInt()} rpm" })
            // Ô nước mát hiện cảnh báo rõ khi quá nhiệt
            grid.addItem(GridItem.Builder().setTitle("Nước mát").setText(
                if (isOh) "⚠ ${ObdService.coolantNow.value?.toInt()}°C DỪNG XE"
                else live["05"]?.let { "${it.toInt()} °C" } ?: "-- °C"
            ).build())
            grid.addItem(item("Bướm ga", "11") { v -> if (v==null) "-- %" else "${v.toInt()} %" })
            grid.addItem(item("MAF", "10") { v -> if (v==null) "-- g/s" else String.format("%.1f g/s", v) })
            grid.addItem(item("Ắc quy", "42") { v -> if (v==null) "-- V" else String.format("%.1f V", v) })
        } else if (page == 1) {
            grid.addItem(GridItem.Builder().setTitle("Quãng đường").setText(String.format("%.1f km", ObdService.tripKm.value)).build())
            grid.addItem(GridItem.Builder().setTitle("Xăng dùng").setText(String.format("%.2f L", ObdService.tripFuelL.value)).build())
            grid.addItem(GridItem.Builder().setTitle("Tiền xăng").setText("%,d đ".format(ObdService.tripCost.value)).build())
            val avg = if (ObdService.tripKm.value > 0.5) ObdService.tripFuelL.value / ObdService.tripKm.value * 100 else null
            grid.addItem(GridItem.Builder().setTitle("TB/100km").setText(avg?.let { String.format("%.1f L", it) } ?: "--").build())
            grid.addItem(GridItem.Builder().setTitle("Giá/L").setText("%,d đ".format(SettingsStore.fuelPrice.value)).build())
            grid.addItem(GridItem.Builder().setTitle("Ghi log").setText(if (ObdService.tripActive.value) "● ĐANG GHI" else "○ Dừng").build())
        } else {
            grid.addItem(GridItem.Builder().setTitle("Ngưỡng nhiệt").setText("$th °C").build())
            grid.addItem(GridItem.Builder().setTitle("VIN").setText(ObdService.vinText.value.ifEmpty { "chưa đọc" }.take(17)).build())
            grid.addItem(GridItem.Builder().setTitle("DTC").setText(ObdService.dtcText.value.ifEmpty { "OK / chưa quét" }.take(30)).build())
            grid.addItem(GridItem.Builder().setTitle("Hỗ trợ").setText("${ObdService.supported.value.size} PID").build())
        }

        val nextTitle = when (page) {
            0 -> "Trip + Tiền"
            1 -> "Lỗi/VIN"
            else -> "Về chính"
        }

        return GridTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setSingleList(grid.build())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setTitle(nextTitle).setOnClickListener {
                        page = (page + 1) % 3; invalidate()
                    }.build())
                    .build()
            )
            .build()
    }
}

// Đọc/xóa DTC chi tiết làm trên điện thoại (MainActivity) khi đỗ xe cho an toàn.
