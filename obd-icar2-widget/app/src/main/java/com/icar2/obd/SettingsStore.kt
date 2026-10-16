package com.icar2.obd

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Cài đặt người dùng: ngưỡng quá nhiệt + giá xăng. Lưu SharedPreferences để Service đọc được.
object SettingsStore {
    private const val PREF = "icar2_settings"
    private lateinit var pref: SharedPreferences

    val overheatThreshold = MutableStateFlow(105) // °C, mặc định 105
    val fuelPrice = MutableStateFlow(24000) // VNĐ/lít, người dùng tự nhập
    val autoTrip = MutableStateFlow(true) // tự động log từ lúc kết nối -> ngắt kết nối

    fun init(ctx: Context) {
        if (::pref.isInitialized) return
        pref = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE)
        overheatThreshold.value = pref.getInt("overheat", 105)
        fuelPrice.value = pref.getInt("fuel_price", 24000)
        autoTrip.value = pref.getBoolean("auto_trip", true)
    }

    fun setAutoTrip(v: Boolean) {
        autoTrip.value = v
        if (::pref.isInitialized) pref.edit().putBoolean("auto_trip", v).apply()
    }

    fun setOverheat(v: Int) {
        overheatThreshold.value = v
        if (::pref.isInitialized) pref.edit().putInt("overheat", v).apply()
    }

    fun setFuelPrice(v: Int) {
        fuelPrice.value = v
        if (::pref.isInitialized) pref.edit().putInt("fuel_price", v).apply()
    }
}
