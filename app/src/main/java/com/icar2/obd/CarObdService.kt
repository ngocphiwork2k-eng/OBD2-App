package com.icar2.obd

import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

// Service Android Auto chính thức (category IOT).
// Cách dùng mới theo ý bạn: mở nhạc/radio trên xe, nhạc vẫn phát nền,
// bạn mở app "iCar2 OBD" trên màn xe để xem số. Không cần đè blur lên Maps nữa.
class CarObdService : CarAppService() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
    }

    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: android.content.Intent): Screen =
            ObdCarScreen(carContext)
    }
}
