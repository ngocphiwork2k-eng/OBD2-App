# Dùng OBD trên Android Auto zin - khi nghe nhạc/radio

Không cần mirror, không cần blur đè lên Maps nữa.

## Cách hoạt động

- Điện thoại kết nối iCar2 BT3.0 (V-LINK), chạy `ObdService` poll RPM/Speed/Coolant...
- Trên màn xe (Android Auto zin): mở app `iCar2 OBD` (category IoT).
- Nhạc/radio (Spotify, YouTube Music, radio zin, Zing MP3 sideload media) vẫn phát nền bình thường, không bị ngắt.
- Bạn chuyển qua lại giữa 2 icon trên taskbar xe: Nhạc <-> OBD. Không xem cùng lúc 2 app toàn màn hình được (luật Google), nhưng:
  - Màn xe rộng: Android Auto mới có split-screen, OBD 1 bên + media 1 bên.
  - Màn xe mới (2025+): ghim Glance widget OBD ở homescreen cạnh media.

## Cài đặt

1. Pair V-LINK / 1234 ở Bluetooth system.
2. `./gradlew assembleDebug` + `adb install -r app-debug.apk`
3. Mở app trên phone > Connect.
4. Cắm cáp vào xe > trên màn xe tìm icon `iCar2 OBD` > mở.
5. Bấm nút `Nhiên liệu/Điện` ở thanh dưới để đổi trang: Chính / Nhiên liệu / Lỗi-VIN.

## Test trên Desktop Head Unit (không cần xe)

Android Studio > Tools > Android Auto Desktop Head Unit:
```
adb forward tcp:5277 tcp:5277
./desktop-head-unit
```
Mở Android Auto trên phone > connect localhost.

## Lưu ý khi lái

- Template chỉ refresh 1s/lần, chữ to, không đồ thị - đúng luật driver-distraction.
- Đọc/xóa DTC chỉ làm khi đỗ (P). Dùng phone để xem chi tiết freeze frame.
- `FloatingWidgetService` cũ vẫn giữ để dùng blur trên phone, nhưng KHÔNG cần cho xe nữa.
