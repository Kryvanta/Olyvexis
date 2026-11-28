# Olyvexis

**Chế độ desktop cho điện thoại Android chưa có desktop riêng.** Cắm màn hình ngoài qua USB-C, hoặc mở trình duyệt trên laptop / TV cùng Wi-Fi: màn hình lớn có desktop hoàn chỉnh với cửa sổ di chuyển/đổi cỡ tự do, thanh tác vụ và ngăn ứng dụng. Điện thoại trở thành trackpad và bàn phím.

[English](README.md) · Phát triển bởi **Kryvanta** · Dùng iPhone / iPad? Xem [iDesk](https://github.com/itshieuk/iDesk), dự án chị em có cùng giao diện và phím tắt

> **Lưu ý:** Olyvexis cần Android 11+ và [Shizuku](https://shizuku.rikka.app/) (bật bằng Gỡ lỗi không dây ngay trên máy - không cần root, không cần máy tính). Ứng dụng không thay thế Samsung DeX, Motorola Ready For hay Honor PC mode; hãy tắt chế độ của hãng trước khi dùng qua cáp.

## Tính năng

- **Mỗi cửa sổ một màn hình ảo**: Chạy trên mọi ROM Android 11+, kể cả máy không bật freeform hệ thống (HyperOS, ColorOS, MagicOS, OriginOS, Pixel...).
- **Cửa sổ**: Di chuyển, đổi cỡ từ cạnh/góc, nhấp đúp tiêu đề để phóng to, bám nửa hoặc góc một phần tư, ghim trên cùng, tự nhớ vị trí & kích thước từng app, không gian làm việc (`1-4`), chuyển cửa sổ `Alt + Tab`.
- **Desktop & Thanh tác vụ**: Hình nền động theo giờ và bộ hình nền gradient, widget đồng hồ, lịch tháng & hẹn giờ tập trung, ghi chú nhanh, lịch sử clipboard (`Meta + Shift + V`), khoá màn hình (`Meta + L`), Tìm kiếm nhanh (`Meta + Space`), Cài đặt Desktop & Cài đặt hệ thống Android, Quick Panel.
- **Hai cách xuất hình**:
  - **Qua Wi-Fi**: Mã hoá phần cứng H.264 (`MediaCodec`) qua WebSocket/WSS. Hỗ trợ **HTTPS tự ký** (`https://<ip>:8766`) để dùng **WebCodecs** độ trễ cực thấp, kèm **MSE** dự phòng (`http://<ip>:8765`) cho trình duyệt TV.
  - **Qua cáp USB-C / HDMI**: Mở desktop thẳng lên màn hình ngoài (`DisplayMode.External`), điện thoại làm trackpad kèm con trỏ trên desktop.
- **Điều khiển**: Chuột, cuộn, bàn phím đầy đủ (hỗ trợ gõ tiếng Việt Telex/VNI), trackpad cảm ứng và ô nhập văn bản trên điện thoại.

## Kết nối màn hình

| Cách dùng               | Cần có                                                                               |
| ----------------------- | ------------------------------------------------------------------------------------ |
| **Qua cáp**             | Cáp USB-C → HDMI / DisplayPort (máy hỗ trợ xuất hình)                                |
| **Laptop / TV (Wi-Fi)** | Bấm **Laptop** và mở địa chỉ `https://` (hoặc `http://`) trên trình duyệt cùng Wi-Fi |
| **Qua USB (adb)**       | `adb forward tcp:8765 tcp:8765` rồi mở `http://localhost:8765`                       |

## Build từ mã nguồn

Cần JDK 17 hoặc 21 và Android SDK (platform 35, `minSdk 30`).

```bash
git clone https://github.com/Kryvanta/Olyvexis.git && cd Olyvexis
./gradlew assembleDebug
```

APK nằm tại `app/build/outputs/apk/debug/app-debug.apk`. Cài vào máy qua ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Phím tắt

| Phím tắt                   | Tác dụng                                 |
| -------------------------- | ---------------------------------------- |
| `Meta`                     | Mở / đóng Ngăn ứng dụng                  |
| `Meta + D`                 | Thu nhỏ tất cả (Hiện Desktop)            |
| `Meta + ← / → / ↑ / ↓`     | Bám nửa trái / phải / phóng to / thu nhỏ |
| `Alt + Tab` / `Meta + Tab` | Chuyển cửa sổ                            |
| `Alt + F4`                 | Đóng cửa sổ đang chọn                    |
| `Esc`                      | Đóng Ngăn ứng dụng / Quick Panel / menu  |

[GPL-3.0](LICENSE) · © Kryvanta · Olyvexis không liên quan tới Google, Shizuku hay nhà phát triển app nào.
