# S0 - Hello RPK (Xiaomi Watch S5, 480x480)

Mục tiêu: dựng được `.rpk` hello-world bằng CLI để S1 sideload lên đồng hồ thật.

## Phiên bản
- Node v22.23.3, npm 10.9.9 (macOS)
- `aiot-toolkit` 2.0.5, `@aiot-toolkit/jsc` ^1.0.3 (devDependencies cục bộ, không cài global)

## Rebuild
```
cd spikes/s0-hello-rpk
npm install
npx aiot build      # debug  -> dist/com.example.motonavhud.debug.0.0.1.rpk   (~9 KB)
npx aiot release    # release -> dist/com.example.motonavhud.release.0.0.1.rpk (~8 KB)
```
Mỗi lệnh build xóa `dist/` trước, nên bản đã build sẵn được copy vào `out/` (gitignored).

## Cấu trúc
- `src/manifest.json`: package `com.example.motonavhud` (placeholder, sẽ đổi khớp app id Android), designWidth 480, features: router, brightness, vibrator, interconnect (khai báo, chưa dùng).
- `src/pages/index/index.ux`: nền đen, "Hello S5", dòng tiếng Việt, `arrow.png` xoay 45 độ, `setKeepScreenOn(true)` trong `onShow`.
- `src/common/arrow.png`: mũi tên trắng 128x128 RGBA (sinh bằng Python thuần vì máy không có Pillow).
- `sign/debug`, `sign/release`: khóa RSA 2048 tự ký (openssl, 10 năm). Đã gitignore `sign/**/*.pem`. Debug build vẫn dùng sign/debug.

## Đã kiểm chứng
- Cả debug và release build exit 0; rpk chứa manifest, app.js, pages/index/index.js, arrow.png, META-INF/CERT.

## Chưa kiểm chứng
- Hiển thị trên đồng hồ/simulator (chữ, dấu tiếng Việt, xoay ảnh, giữ màn hình sáng): chờ S1.
- Không chạy simulator (cần AIoT-IDE GUI, tải Vela image). Không kết nối đồng hồ.
- Chưa xác nhận `package` có xung đột với "native app" (xem tài liệu 01 mục 2.1).

## Lưu ý
- CLI in cảnh báo "please use AIoT-IDE" nhưng build vẫn thành công.
- aiot-toolkit tạo thư mục tạm `spikes/.temp_s0-hello-rpk` khi build rồi tự xóa (kiểm tra sau build: không còn).
- `npm install` báo vài cảnh báo audit (devDependency, bỏ qua).
