# Đóng gói APK để test

Máy hiện tại KHÔNG có Java/Android SDK nên không build trực tiếp được.
Bạn có 2 cách (chọn 1):

## Cách 1: Build trên máy bạn bằng Android Studio (khuyên dùng, 5 phút)

1. Cài Android Studio mới nhất (tự có SDK + Java 17).
2. Open project `C:\obd-icar2-widget` > đợi sync Gradle xong.
3. Cắm điện thoại bật USB debugging, hoặc Build > Build APK > `app-debug.apk`.
4. `adb install -r app-debug.apk`

Nếu thiếu Gradle wrapper: trong terminal của Android Studio chạy `gradle wrapper` 1 lần.

## Cách 2: Build tự động bằng GitHub (không cần cài gì)

1. Tạo repo GitHub mới, push toàn bộ thư mục `obd-icar2-widget` lên.
2. File `.github/workflows/build-apk.yml` đã có sẵn.
3. Vào tab Actions > Run workflow > đợi 3-5 phút > tải artifact `icar2-obd-apk`.
4. Copy APK về phone cài, cấp quyền Bluetooth + Location + Notification.

## Test checklist

- [ ] Pair V-LINK / 1234 ở Bluetooth system trước
- [ ] Mở app > Connect > thấy RPM/Speed/Coolant nhảy
- [ ] Cài đặt ngưỡng quá nhiệt (mặc định 105°C), giá xăng (mặc định 24000đ)
- [ ] Bấm Trip Bắt đầu > chạy thử > thấy km + lít + tiền tăng
- [ ] Rút thử cảm biến? Không cần - để test quá nhiệt: tạm set ngưỡng 70°C, chạy 5 phút sẽ báo đỏ + notification + toast trên màn xe
- [ ] Cắm vào Android Auto > mở icon iCar2 OBD > chuyển trang Trip + Tiền bằng nút dưới
- [ ] File CSV trip ở `Android/data/com.icar2.obd/files/trips/trip_*.csv`, copy ra Excel được

## Công thức tính xăng

- Ưu tiên PID 015E (Fuel Rate L/h) nếu xe hỗ trợ.
- Nếu không: ước lượng từ MAF: `L/h = MAF(g/s) / 14.7 / 720 * 3600`. Sai số ~10-15%, đủ để tính tiền tham khảo.
- Tiền = tổng lít * giá bạn nhập.
