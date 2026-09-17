# KoY-Auto (Android Auto Audio & External-Display Player)

Ứng dụng phát nhạc/radio nền trên điện thoại và cung cấp thư viện âm thanh cho Android Auto. Video có thể xuất ra màn hình phụ thật (HDMI, USB-C DisplayPort, Miracast tương thích Presentation) hoặc chạy trực tiếp trên Android Box.

> Android Auto qua cáp USB không cung cấp màn hình xe như một Android `Display` thông thường. Bản media app chỉ hiển thị giao diện âm thanh do Android Auto dựng và không thể chiếu WebView/video tùy ý lên màn hình xe. Video chỉ nên dùng trên màn hình phụ thật hoặc thiết bị Android Automotive/Android Box phù hợp, khi xe đang đỗ.

## 🚀 Cấu trúc dự án
- `app/src/main/java/com/koy/auto/`
  - `presentation/CarPresentation.kt`: Quản lý hiển thị và điều khiển trên màn hình phụ của xe.
  - `presentation/CarDisplayManager.kt`: Bắt sự kiện thêm/rút màn hình phụ hỗ trợ Presentation.
  - `service/CarProjectionService.kt`: Background Foreground Service duy trì kết nối.
  - `service/KoYMediaService.kt`: MediaBrowserService cho Android Auto.
  - `webview/`: Tầng WebKit Chromium tăng tốc phần cứng + bộ lọc chặn quảng cáo tầng mạng `AdBlockFilter`.
  - `MainActivity.kt`: Bảng điều khiển trên điện thoại và trình phát độc lập.

## 📦 Cài APK thử nghiệm

GitHub Actions tạo `KoY-Auto-Debug-APK` sau mỗi lần push. Đây là APK debug để kiểm thử, không phải bản release đã ký để phân phối.

### Cách 1: Dùng lệnh ADB trên máy tính (Khuyến nghị)
1. Bật **Tùy chọn cho nhà phát triển** và **Gỡ lỗi USB (USB Debugging)** trên điện thoại.
2. Cắm cáp kết nối điện thoại với máy tính.
3. Chạy lệnh:
```bash
adb install -i "com.android.vending" app-debug.apk
```
Sau khi cài, bật chế độ nhà phát triển của Android Auto và cho phép ứng dụng không rõ nguồn nếu thiết bị yêu cầu. Việc giả lập nguồn cài đặt không thay đổi các danh mục ứng dụng hoặc giới hạn an toàn của Android Auto.

### Cách 2: Cài bằng KingInstaller trên điện thoại
1. Tải và cài đặt ứng dụng mã nguồn mở **KingInstaller** (hoặc **AAAD**) trên điện thoại.
2. Chọn file `app-debug.apk` trong KingInstaller để cài đặt.

## ✅ Kiểm thử bắt buộc trên thiết bị

1. Phát từng mục VOV3, VOV Giao Thông và VOV2; bấm Home và khóa màn hình ít nhất 10 phút.
2. Kiểm tra Play/Pause/Stop từ thông báo, màn hình khóa và nút vô-lăng.
3. Kết nối Android Auto và xác nhận ba mục âm thanh xuất hiện, phát qua loa xe.
4. Với màn hình HDMI/USB-C thật, cấp quyền “hiển thị trên ứng dụng khác”, sau đó kiểm tra video trên màn hình phụ.
5. Không kiểm thử video khi xe đang di chuyển.
