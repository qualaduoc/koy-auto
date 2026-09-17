# KoY-Auto (Android Auto & In-Car YouTube Player)

Ứng dụng xem YouTube trên màn hình xe hơi qua Android Auto, CarAuto hoặc chạy độc lập trên Android Box.

## 🚀 Cấu trúc dự án
- `app/src/main/java/com/koy/auto/`
  - `presentation/CarPresentation.kt`: Quản lý hiển thị và điều khiển trên màn hình phụ của xe.
  - `presentation/CarDisplayManager.kt`: Bắt sự kiện cắm/rút cáp Android Auto.
  - `service/CarProjectionService.kt`: Background Foreground Service duy trì kết nối.
  - `service/KoYMediaService.kt`: MediaBrowserService cho Android Auto.
  - `webview/`: Tầng WebKit Chromium tăng tốc phần cứng + bộ lọc chặn quảng cáo tầng mạng `AdBlockFilter`.
  - `MainActivity.kt`: Bảng điều khiển trên điện thoại và trình phát độc lập.

## 📦 Cách cài đặt vào điện thoại để hiện trên Android Auto
Do Google cấm sideload app bên thứ 3 lên Android Auto, có 2 cách cài đặt:

### Cách 1: Dùng lệnh ADB trên máy tính (Khuyến nghị)
1. Bật **Tùy chọn cho nhà phát triển** và **Gỡ lỗi USB (USB Debugging)** trên điện thoại.
2. Cắm cáp kết nối điện thoại với máy tính.
3. Chạy lệnh:
```bash
adb install -i "com.android.vending" app-debug.apk
```
*Cờ `-i "com.android.vending"` sẽ giả lập nguồn cài từ Google Play Store, giúp biểu tượng KoY-Auto hiển thị ngay trên màn hình xe hơi.*

### Cách 2: Cài bằng KingInstaller trên điện thoại
1. Tải và cài đặt ứng dụng mã nguồn mở **KingInstaller** (hoặc **AAAD**) trên điện thoại.
2. Chọn file `app-debug.apk` trong KingInstaller để cài đặt.
