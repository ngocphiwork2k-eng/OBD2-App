package com.icar2.obd

import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

// 1 chuyến = 1 lần kết nối -> ngắt kết nối. Lưu summary để hiện lịch sử.
data class TripSummary(
    val id: String, // yyyyMMdd_HHmmss
    val startTime: String,
    val endTime: String,
    val km: Double,
    val fuelL: Double,
    val cost: Long,
    val csvName: String
)

object TripHistory {
    val all = MutableStateFlow<List<TripSummary>>(emptyList())

    fun refresh(dir: File) {
        try {
            if (!dir.exists()) { all.value = emptyList(); return }
            // summary file: trip_xxx_summary.txt dạng key=value
            val list = dir.listFiles { f -> f.name.endsWith("_summary.txt") }
                ?.mapNotNull { parseSummary(it) }
                ?.sortedByDescending { it.id } ?: emptyList()
            all.value = list
        } catch (_: Exception) {}
    }

    private fun parseSummary(f: File): TripSummary? {
        return try {
            val m = f.readLines().mapNotNull {
                val p = it.split("=", limit = 2)
                if (p.size == 2) p[0].trim() to p[1].trim() else null
            }.toMap()
            TripSummary(
                id = m["id"] ?: f.nameWithoutExtension,
                startTime = m["start"] ?: "--",
                endTime = m["end"] ?: "--",
                km = m["km"]?.toDoubleOrNull() ?: 0.0,
                fuelL = m["fuel"]?.toDoubleOrNull() ?: 0.0,
                cost = m["cost"]?.toLongOrNull() ?: 0L,
                csvName = m["csv"] ?: ""
            )
        } catch (_: Exception) { null }
    }
}
