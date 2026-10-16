package com.icar2.obd

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            ), 1)
        }
        setContent { Dashboard() }
    }
}

@Composable
fun Dashboard() {
    val ctx = LocalContext.current
    val live by ObdService.liveData.collectAsState()
    val status by ObdService.status.collectAsState()
    val supported by ObdService.supported.collectAsState()
    val dtc by ObdService.dtcText.collectAsState()
    val vin by ObdService.vinText.collectAsState()
    val isOh by ObdService.overheat.collectAsState()
    val coolant by ObdService.coolantNow.collectAsState()
    val tripActive by ObdService.tripActive.collectAsState()
    val tripKm by ObdService.tripKm.collectAsState()
    val tripFuel by ObdService.tripFuelL.collectAsState()
    val tripCost by ObdService.tripCost.collectAsState()
    val tripLog by ObdService.tripLog.collectAsState()
    val th by SettingsStore.overheatThreshold.collectAsState()
    val price by SettingsStore.fuelPrice.collectAsState()
    val autoTripOn by SettingsStore.autoTrip.collectAsState()
    val history by TripHistory.all.collectAsState()

    var addr by remember { mutableStateOf("") }
    var thText by remember(th) { mutableStateOf(th.toString()) }
    var priceText by remember(price) { mutableStateOf(price.toString()) }

    val paired = remember {
        try {
            val bt = ctx.getSystemService(BluetoothManager::class.java).adapter
            bt?.bondedDevices?.filter { it.name?.contains("V-LINK", true)==true || it.name?.contains("OBD", true)==true }?.toList() ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    // Banner quá nhiệt
    if (isOh) {
        // dùng Card đỏ đầu trang
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("iCar2 BT3.0 - OBD + Trip", style = MaterialTheme.typography.titleLarge)
            Text("Status: $status")
            if (vin.isNotEmpty()) Text("VIN: $vin", style = MaterialTheme.typography.bodySmall)
            if (isOh) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C))) {
                Text("⚠ QUÁ NHIỆT ${coolant?.toInt()}°C (ngưỡng $th°C) - Dừng xe kiểm tra!",
                    color = Color.White, modifier = Modifier.padding(12.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val a = addr.ifEmpty { paired.firstOrNull()?.address ?: "" }
                    if (a.isNotEmpty()) ctx.startForegroundService(Intent(ctx, ObdService::class.java).apply {
                        action="CONNECT"; putExtra("addr", a)
                    })
                }) { Text("Connect") }
                Button(onClick = { ctx.startService(Intent(ctx, ObdService::class.java).apply { action="DISCONNECT" }) }) { Text("Ngắt") }
            }
            paired.forEach { d ->
                TextButton(onClick = { addr = d.address }) {
                    Text("${d.name} - ${d.address}" + if (addr==d.address) " ✓" else "")
                }
            }
        }
        item {
            // Cài đặt: ngưỡng nhiệt + giá xăng
            Text("Cài đặt", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = thText, onValueChange = { thText = it },
                    label = { Text("Ngưỡng quá nhiệt °C") }, modifier = Modifier.weight(1f))
                Button(onClick = { thText.toIntOrNull()?.let { SettingsStore.setOverheat(it.coerceIn(90, 130)) } }) { Text("Lưu") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = priceText, onValueChange = { priceText = it },
                    label = { Text("Giá xăng VNĐ/lít") }, modifier = Modifier.weight(1f))
                Button(onClick = { priceText.toIntOrNull()?.let { SettingsStore.setFuelPrice(it.coerceIn(1000, 100000)) } }) { Text("Lưu") }
            }
        }
        item {
            // Hành trình TỰ ĐỘNG theo kết nối
            Text("Hành trình (tự động)", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = autoTripOn, onCheckedChange = { SettingsStore.setAutoTrip(it) })
                Text("Tự động ghi từ lúc kết nối -> ngắt kết nối")
            }
            Text("Chuyến hiện tại: ${String.format("%.2f km", tripKm)} • ${String.format("%.2f L", tripFuel)} • ${"%,d".format(tripCost)} đ")
            val avg = if (tripKm > 0.5) tripFuel / tripKm * 100 else null
            Text("TB: " + (avg?.let { String.format("%.1f L/100km", it) } ?: "--") + if (tripActive) " • ● ĐANG GHI" else " • ○ đã chốt")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ctx.startService(Intent(ctx, ObdService::class.java).apply { action="TRIP_RESET" }) }) { Text("Reset số hiện tại") }
                Button(onClick = {
                    // Refresh lịch sử + mở thư mục trips (xem qua file manager)
                    ctx.startService(Intent(ctx, ObdService::class.java).apply { action="TRIP_HISTORY" })
                }) { Text("Tải lại lịch sử") }
            }
            Text("Lịch sử chuyến (${history.size}):", style = MaterialTheme.typography.titleSmall)
        }
        items(history.take(20)) { t ->
            ListItem(
                headlineContent = { Text("${t.id} • ${String.format("%.1f km", t.km)} • ${"%,d".format(t.cost)} đ") },
                supportingContent = { Text("${t.startTime} -> ${t.endTime} • ${String.format("%.2f L", t.fuelL)} • ${t.csvName}") }
            )
            Divider()
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ctx.startService(Intent(ctx, ObdService::class.java).apply { action="READ_DTC" }) }) { Text("Đọc lỗi") }
                Button(onClick = { ctx.startService(Intent(ctx, ObdService::class.java).apply { action="CLEAR_DTC" }) }) { Text("Xóa lỗi") }
                Button(onClick = { ctx.startService(Intent(ctx, ObdService::class.java).apply { action="READ_VIN" }) }) { Text("VIN") }
            }
            if (dtc.isNotEmpty()) Text("DTC: $dtc", style = MaterialTheme.typography.bodySmall)
            Text("CSV lưu ở: Android/data/com.icar2.obd/files/trips/", style = MaterialTheme.typography.bodySmall)
        }
        items(tripLog.takeLast(10)) { line -> Text(line, style = MaterialTheme.typography.bodySmall) }
        item { Text("Live PID (${supported.size} hỗ trợ):", style = MaterialTheme.typography.titleMedium) }
        items(ObdPids.ALL_01) { def ->
            val v = live[def.pid]
            val sup = if (supported.isEmpty()) "?" else if (supported.contains(def.pid)) "●" else "○"
            val highlight = def.pid=="05" && isOh
            ListItem(
                headlineContent = { Text("$sup ${def.name} (${def.mode}${def.pid})") },
                supportingContent = { Text(if (v==null) "-- ${def.unit}" else String.format("%.1f %s", v, def.unit),
                    color = if (highlight) Color.Red else Color.Unspecified) }
            )
            Divider()
        }
    }
}
