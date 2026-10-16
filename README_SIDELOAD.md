# OBD iCar2 Widget - BT3.0 + Sideload Android Auto

## Tại sao phải sideload + mirror?

Android Auto zin (Play Store) CẤM `SYSTEM_ALERT_WINDOW` overlay lên Maps.
Cách duy nhất để có widget blur đè lên app khác trên màn xe:

```
Phone [OBD Service + Floating Blur Widget] --mirror--> Screen2Auto/Fermata Mirror --> HeadUnit
```

Tức là widget blur chạy trên ĐIỆN THOẠI, rồi mirror nguyên màn hình điện thoại lên xe.
Trên xe bạn sẽ thấy Maps + cục widget blur nổi góc màn hình.

## 1. Pair vGate iCar2 BT3.0

1. Cắm iCar2 vào cổng OBD, bật máy xe (ON, chưa cần nổ máy).
2. Điện thoại: Settings > Bluetooth > quét > chọn `V-LINK` > PIN `1234`.
3. Đừng pair trong app Torque trước, pair ở system trước.

UUID SPP: `00001101-0000-1000-8000-00805F9B34FB`

## 2. Cài app này (sideload)

```bash
./gradlew assembleDebug
adb install -r app-debug.apk
```

Cấp quyền:
- Bluetooth Scan/Connect + Location (để scan BT classic)
- Display over other apps (để vẽ widget blur)
- Notifications (foreground service)

## 3. Chạy

1. Mở app > chọn `V-LINK` > Connect.
2. App tự gửi init: `ATZ / ATE0 / ATL0 / ATS0 / ATH1 / ATAL / ATSP0 / 0100`
3. Tab Dashboard hiện tất cả PID xe hỗ trợ (query 0100/0120/0140...).
4. Bật `Floating Widget` -> cục blur draggable hiện trên điện thoại.
5. Chỉnh trong widget: chọn 4 PID hiển thị + độ blur + opacity.

## 4. Đưa lên Android Auto (sideload mirror)

Trên điện thoại (Android 9-14 tốt nhất):

1. Android Auto > Settings > version tap 10 lần > bật Developer Mode.
2. 3 chấm > Developer settings > bật `Unknown sources`.
3. Cài AAAD (Android Auto Apps Downloader) > cài `Screen2Auto` hoặc `Fermata Mirror`.
4. Cắm cáp vào xe > mở Screen2Auto trên màn xe > mirror màn hình điện thoại.
5. Trên điện thoại mở floating widget trước, rồi mở Google Maps.
6. Trên màn xe bạn sẽ thấy Maps + widget blur góc phải.

> Tip: để widget không che nút Maps, kéo widget vào góc, bật chế độ thu gọn 2 số (Speed + RPM).

## 5. Danh sách PID đọc được (Mode 01)

App tự lọc theo xe. Full list trong `ObdPids.kt`:
0104 Load, 0105 Coolant, 010A Fuel Press, 010B MAP, 010C RPM, 010D Speed,
010E Timing, 010F IAT, 0110 MAF, 0111 Throttle, 0106-0109 Fuel Trim,
012F Fuel Level, 0142 Voltage, 0121 MIL dist, 0131 Baro, 015C Oil Temp,
0114-011B O2, 012C EGR, v.v. + Mode 09 VIN 0902 + Mode 03/07 DTC + Mode 04 Clear.

Tốc độ poll ELM327 clone: 5-8 PID/s. Đừng bật hết 40 PID cùng lúc, chọn 6-10 PID quan trọng.

## An toàn

Chỉ xem khi dừng. Sideload mirror có thể gây mất tập trung, bạn tự chịu trách nhiệm.
