# Prompt bàn giao cho Gemini (Moto Nav HUD)

Dán nguyên văn phần dưới vào Gemini (chạy trong thư mục `/Users/thanhng224/Dev/Personal/Xiaomi watch apps/moto-nav-hud`).

---

Bạn tiếp quản dự án cá nhân **moto-nav-hud**: HUD chỉ đường xe máy trên đồng hồ tròn Xiaomi Watch S5 (Vela QuickApp `.rpk`, màn 466 px, designWidth 480), nhận dữ liệu từ thông báo Google Maps trên điện thoại Android qua BLE. Người dùng nói tiếng Việt, trả lời bằng tiếng Việt, ngắn gọn. Chỉ làm việc trên nhánh hiện tại, không tạo branch/worktree. Tự thao tác bằng adb/CLI mọi thứ có thể; chỉ nhờ người dùng việc không tự động hoá được (mở khoá máy, đăng nhập, cài đặt hệ thống, xác nhận trực quan trên đồng hồ). Hỏi trước khi xoá thứ nhạy cảm.

## Đọc trước
- `README.md`, `docs/protocol.md` (hợp đồng v1.2, nguồn sự thật), `docs/plans/2026-11-23-moto-nav-hud.md` (mục Tiến độ), `watch-app/README.md`, `phone-app/README.md`.
- Review của Codex: `docs/research/06`, `07`, `08-codex-sol-high-review-round3.json` (vòng 3; mọi phát hiện đã sửa trừ token ghép cặp dùng chung `MOTONAV1`, chấp nhận cho dùng cá nhân). Vòng 4 (xác nhận) xem ghi chú cuối file này.

## Kiến trúc
- `phone-app/` (Kotlin, applicationId `dev.motonavhud.app`): NotificationListener đọc thông báo Google Maps -> `MapsNotificationParser` -> `PayloadBuilder` (JSON ≤ min(180, MTU-3)) -> `BlePeripheralServer` (GATT server, foreground service `NavForegroundService`). Có chế độ Demo và `NotificationCapture` để lưu thông báo thật làm fixture. Log logcat tag `MotoNav`.
- `watch-app/` (JS QuickApp): `src/common/transport-ble.js` (central: quét, nối, subscribe), `hud-logic.js`, `haptics.js`, `status.js`, `log.js` (prefix `[MotoNav]`, cờ `cfg.DEBUG_LOG`), `src/pages/index/index.ux`, mũi tên PNG tạo bởi `tools/gen_arrows.py`. 42 test (`npm test`).

## Đã kiểm chứng trên phần cứng (OPPO CPH2873 Android 17 + Watch S5)
Quét -> nối -> subscribe -> nhận frame, Demo (mũi tên, khoảng cách, tên đường, chữ Việt có dấu), rung đúng ngưỡng (d≤200 rung 2 ngắn, d≤50 rung 3 dài). Biến thể quét V3 (`filters:[{}]` + so khớp UUID thủ công) là biến thể chạy được trên S5; `options:{dutyMode}` lỗi 202; filter serviceUuid hệ thống không lọc.
**Chưa kiểm chứng trên máy:** các thay đổi sau vòng review 3 (sửa lỗi transport, haptics, parser, logging, mũi tên mới, layout trạng thái), thông báo Google Maps thật, wrist-down/AOD, chạy đường dài.

## Lưu ý vận hành (rất quan trọng)
- Adb: Oppo USB serial `3B164T00XVG00000` (`adb -s ...`). Cài rpk lên đồng hồ qua app **Notify for Xiaomi** (com.mc.xiaomi1) trên Oppo (Mi Fitness 3.60.0i không có trang debug); hướng dẫn: `docs/guides/S1-cai-rpk-len-s5.md`.
- Notify giữ kết nối LE tới đồng hồ và tự khởi động lại; trước khi chạy HUD phải `am force-stop com.mc.xiaomi1`, rồi khởi động lại app phone (force-stop `dev.motonavhud.app`, mở lại, bấm Start service, rồi Demo) thì HUD mới nối được.
- Oppo phải mở khoá/không tự khoá khi agent thao tác. Không nhập mật khẩu hộ người dùng.
- Build watch: `cd watch-app && npm test && npx aiot build && npx aiot release` (rpk debug ở `out/`, release ở `dist/`). Build phone: `cd phone-app && ./gradlew testDebugUnitTest assembleDebug lintDebug` (APK `app/build/outputs/apk/debug/app-debug.apk`).

## Việc cần làm tiếp (theo thứ tự)
1. Chạy lại `npm test`, build debug + release watch, gradle phone; đảm bảo xanh.
2. Nếu ghi chú review vòng 4 cuối file còn phát hiện chưa xử lý: kiểm trong code, sửa lỗi đúng và thuộc phạm vi, chạy lại test.
3. Cài rpk debug mới + APK mới (quy trình ở "Lưu ý vận hành"), nhờ người dùng xem đồng hồ: mũi tên mới (nhất là chéo 45°/135°, giữ làn), dòng trạng thái/chẩn đoán không bị cắt đáy màn tròn, Demo + rung. Xem log: `adb -s <serial> logcat -s MotoNav` (phone); log watch `[MotoNav]` xem qua công cụ của rpk nếu có.
4. Thu thông báo Google Maps thật (S4): bật `NotificationCapture` trong app, đi/chạy chế độ xe máy ở VN, lưu fixture; đối chiếu `MapsNotificationParser` (đặc biệt câu dạng "Trên đường X, rẽ trái" hiện không ra hướng rẽ vì đã siết fallback; thêm test từ fixture thật).
5. Kiểm wrist-down/AOD (màn hình giữ sáng, app không bị dừng) và chạy thử đường thật.
6. Điều tra vì sao tên app không hiện trong danh sách app đồng hồ (manifest name/icon) — việc nhỏ, tuỳ chọn.
7. Dọn dẹp cuối (hỏi người dùng trước từng mục nhạy cảm): `/sdcard/Download/wearablelog/` (chứa khoá Xiaomi), bản `.rpk`/APK trong Download trên Oppo/Samsung, Notify trên Samsung, bật lại quyền/kết nối Mi Fitness, ảnh tạm. Cập nhật mục Tiến độ trong kế hoạch và README; `docs/plans/` không commit, xoá khi xong.

## Trạng thái MVP hiện tại (2026-11-23)
- Watch: 51 test pass (`npm test`); rpk debug `watch-app/out/dev.motonavhud.app.debug.0.1.0.rpk` (~35 KB), release `watch-app/out/dev.motonavhud.app.release.0.1.0.rpk` (~30 KB). Phone: 41 test method pass (0 lỗi), lint 0 lỗi, APK `phone-app/app/build/outputs/apk/debug/app-debug.apk`.
- Đã hoàn thiện & kiểm chứng trên phần cứng thật (**OPPO CPH2873 Android 17 + Xiaomi Watch S5 46mm fw 3.222.007**):
  - Tên app + icon hiện đúng trên đồng hồ; mũi tên `Gắt trái`/`Gắt phải` đã vẽ lại tách rõ nhánh 135°; sửa cắt mép dưới dòng trạng thái; thêm màu khẩn cấp khoảng cách (`>200 m` trắng, `≤200 m` vàng, `≤50 m` đỏ cam) và màu banner/trạng thái.
  - S4 (Google Maps 26.47 thật chế độ xe máy, `Notification$ProgressStyle` Live Updates): hỗ trợ NBSP `\u00a0`, `Arrive HH:mm`, `shortCriticalText`, `progressMax - progress` mét còn lại.
  - Chống "MẤT DỮ LIỆU" giả khi đứng đèn đỏ/đường thẳng: mỗi 3 s kiểm tra ngầm `getActiveNotifications(arrayOf(selectedKey))` giữ `a` tươi khi notification ongoing còn sống, vẫn về `k=0` ngay khi tắt Maps (đã xác nhận đứng yên 20 s giữ `k=1`, tắt Maps về `k=0` trong 28 ms).
  - Tối ưu hiệu năng & chống spam log: tắt `tickTimer` 1 Hz khi không debug trên watch, chỉ ghi thuộc tính reactive khi giá trị đổi, gộp count log 10 s; phía phone bỏ log `notify sent` mỗi gói, heartbeat chỉ log 30 s/lần, bỏ serialize JSON preview khi `MainActivity` đóng; thêm nút `Dừng HUD` ngay trên thanh thông báo.
  - Giữ nguyên Notify for Xiaomi để tiện cài/update `.rpk` các bản sau.

## Ràng buộc
- Giữ giải pháp tối giản, không thêm dependency/abstraction không cần. Không đổi hợp đồng `docs/protocol.md` nếu không cần (đổi phải sửa cả hai phía + test).
- Với thay đổi logic/hành vi đáng kể, nhờ một lượt review độc lập trước khi báo xong.
- Báo trung thực: nói rõ cái gì đã chạy/kiểm chứng, cái gì chưa.
