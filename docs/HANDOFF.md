# Prompt bàn giao cho Gemini (Moto Nav HUD)

Dán nguyên văn phần dưới vào Gemini (chạy trong thư mục `/Users/thanhng224/Dev/Personal/Xiaomi watch apps/moto-nav-hud`).

---

Bạn tiếp quản dự án cá nhân **moto-nav-hud**: HUD chỉ đường xe máy trên đồng hồ tròn Xiaomi Watch S5 (Vela QuickApp `.rpk`, màn 466 px, designWidth 480), nhận dữ liệu từ thông báo Google Maps trên điện thoại Android qua BLE. Người dùng nói tiếng Việt, trả lời bằng tiếng Việt, ngắn gọn. Chỉ làm việc trên nhánh hiện tại, không tạo branch/worktree. Tự thao tác bằng adb/CLI mọi thứ có thể; chỉ nhờ người dùng việc không tự động hoá được (mở khoá máy, đăng nhập, cài đặt hệ thống, xác nhận trực quan trên đồng hồ). Hỏi trước khi xoá thứ nhạy cảm.

## Đọc trước
- `README.md`, `docs/protocol.md` (hợp đồng v1.4, nguồn sự thật), `docs/plans/2026-11-23-moto-nav-hud.md` (mục Tiến độ), `watch-app/README.md`, `phone-app/README.md`.
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
  - Chống "MẤT DỮ LIỆU" giả khi đứng đèn đỏ/đường thẳng: mỗi 3 s kiểm tra ngầm `getActiveNotifications(arrayOf(selectedKey))` xác nhận notification ongoing còn sống (KHÔNG làm mới `a`; `a` chỉ gắn với lần Maps đăng thật, xem mục 0.4.2 bên dưới), vẫn về `k=0` ngay khi tắt Maps (đã xác nhận đứng yên 20 s giữ `k=1`, tắt Maps về `k=0` trong 28 ms).
  - Tối ưu hiệu năng & chống spam log: tắt `tickTimer` 1 Hz khi không debug trên watch, chỉ ghi thuộc tính reactive khi giá trị đổi, gộp count log 10 s; phía phone bỏ log `notify sent` mỗi gói, heartbeat chỉ log 30 s/lần, bỏ serialize JSON preview khi `MainActivity` đóng; thêm nút `Dừng HUD` ngay trên thanh thông báo.
  - Giữ nguyên Notify for Xiaomi để tiện cài/update `.rpk` các bản sau.

## Watch 0.4.3 (2026-11-25)

Phiên bản: watch `versionName` 0.4.3 (`versionCode` 9). Phone vẫn 0.5.0. BLE/payload không đổi (`v`=1, hợp đồng `docs/protocol.md` v1.4).

- **Đồng hồ nhỏ (`clock-top`)**: HH:mm ở góc trên bên phải mặt HUD, nằm trong cung 223 px, định dạng 24 giờ, dùng lại `clockStr()`.
- **Phone**: preview không có đồng hồ (có chủ đích).
- **Chưa kiểm chứng trên máy**: vị trí và hiển thị đồng hồ trên Watch S5 thật.

## Phone 0.5.0 – UI refresh (2026-11-25)

Phiên bản: phone `versionName` 0.5.0 (`versionCode` 7, giữ nguyên); watch 0.4.3 (`versionCode` 9). BLE/payload không đổi (`v`=1, hợp đồng `docs/protocol.md` v1.4).

- **Màn hình chính mới**: hero đồng hồ (xem trước mũi tên), mục "Quick Go" (dẫn nhanh tới địa điểm đã lưu), mục "Trạng thái", dock điều hướng dưới cùng.
- **Mở Google Maps**: bấm địa điểm gửi `google.navigation:` với chế độ mặc định xe máy (`mode=l`); đổi sang Ô tô/Xe đạp/Đi bộ trong Cài đặt.
- **Địa điểm nhanh**: Nhà, Cơ quan và một mục khác, tối đa 3; mỗi mục lưu địa chỉ và tọa độ lat/lng.
- **Phím tắt launcher**: shortcut dynamic theo địa điểm (`ShortcutPublisher`, `res/xml/shortcuts.xml`).
- **Thông báo**: nút "Về Maps" trên thông báo dịch vụ.
- **Quick Settings tile**: mở Maps sau khi bật dịch vụ; tùy chọn `open_maps_on_start` (mặc định bật).
- **Quyền vị trí (tùy chọn)**: `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`, dùng để lấy vị trí hiện tại; không dùng Google Play services.
- **Khai báo `<queries>`** cho `com.google.android.apps.maps`.
- **Đồng hồ**: viền (bezel) nền tối vẽ lại theo dark theme (chỉ giao diện phone; không đổi watch).
- **Chưa kiểm chứng trên máy**: nút "Về Maps" trên thông báo; phím tắt launcher; QS tile mở Maps sau start; vị trí hiện tại với fix GPS thật; giao diện tiếng Việt trên máy; cỡ chữ lớn; xoay ngang; việc Google Maps có tôn trọng `mode=l` ở Việt Nam hay không.

## Thay đổi 0.4.2 (2026-11-25) và nhật ký sự kiện

Phiên bản: `versionName` 0.4.2 cho cả hai app lúc phát hành (phone `versionCode` 7, watch `versionCode` 8). Payload không đổi (`v`=1); đổi quy tắc tuổi nguồn, xem `docs/protocol.md` v1.4.

- **Watch, `k=1`** (`config.js`): tươi tới `MAX_SOURCE_AGE_MS` = 10 s; từ 10 s tới `NAV_LOST_SOURCE_AGE_MS` = 65 s là "cũ" (giữ mũi tên/khoảng cách/đường, dấu mờ "cũ Ns" cạnh khoảng cách, không banner, không rung mất dữ liệu, không rung tiệm cận, không đổi màu/vòng cung); quá 65 s là MẤT DỮ LIỆU. `k=2`/`k=3` vẫn 10 s. Giới hạn 3 s im lặng BLE không đổi (cũng áp dụng khi đang "cũ").
- **Phone (`Throttle.kt`)**: `k=1` hết hạn sau `navStaleMs` = 75 s không có bản đăng Maps mới rồi gửi `k=0`; `k=3` vẫn 15 s; `k=2` không hết hạn.
- **Vì sao**: Google Maps chỉ đăng lại thông báo khi chữ hiển thị đổi, nên im khoảng 60 s lúc đứng yên là bình thường (đã thấy trên OPPO CPH2873 + Maps 26.47). 65 s nằm trên mức đó; phone chờ thêm 10 s trước khi về `k=0`.
- Heartbeat và kiểm tra 3 s không làm mới `a`.

### Nhật ký sự kiện (phone, `EventLog.kt`)

- File `files/events.log`; khi vượt 256 KB thì chuyển sang `files/events.log.1` (chỉ giữ một bản sao). Lúc khởi động app, file quá 7 ngày bị xóa. Không ghi gì khác.
- Dòng payload được khử trùng lặp (bỏ qua `q` và `a` khi so sánh), nhưng vẫn ghi ít nhất một dòng mỗi 30 s.
- Sự kiện mới:
  - `maps_post gap_ms=<ms từ bản đăng Maps trước, -1 lần đầu hoặc sau khi im> k=<k>`: khoảng im giữa hai lần Maps đăng.
  - `src_age_cross bucket=<0|1|2> a=<ms> k=<k>`: ghi mỗi lần đổi bucket khi `k=1` (0: `a` < 10 s, 1: `a` >= 10 s, 2: `a` >= 65 s).
- Đọc sau chuyến đi:
  ```bash
  adb shell run-as dev.motonavhud.app cat files/events.log.1 files/events.log
  ```
- Cách dùng để chỉnh 10/65/75 s:
  - Lấy phân bố `gap_ms` của `k=1` trên đường thật. Nếu phần lớn khoảng im ngắn hơn 10 s thì `bucket=1` xuất hiện vô ích; cân nhắc giữ 10 s nhưng không dùng nó làm tín hiệu cảnh báo.
  - Nếu `bucket=2` xuất hiện khi Maps vẫn đang dẫn đường bình thường, tăng `NAV_LOST_SOURCE_AGE_MS` (watch). Giữ `navStaleMs` (phone) lớn hơn ngưỡng watch để phone luôn là lớp dự phòng cuối cùng.
  - Nếu `gap_ms` vượt 65 s liên tục khi đứng đèn đỏ, Maps không đăng lại trong thời gian đó và 65 s không đủ; xem xét tín hiệu khác thay vì nâng ngưỡng mãi.
  - Mọi thay đổi ngưỡng phải sửa `docs/protocol.md` trước, rồi cả hai phía và test tương ứng.

## Ràng buộc
- Giữ giải pháp tối giản, không thêm dependency/abstraction không cần. Không đổi hợp đồng `docs/protocol.md` nếu không cần (đổi phải sửa cả hai phía + test).
- Với thay đổi logic/hành vi đáng kể, nhờ một lượt review độc lập trước khi báo xong.
- Báo trung thực: nói rõ cái gì đã chạy/kiểm chứng, cái gì chưa.

## Phản biện review vòng 5 (`docs/research/10-codex-sol-high-audit-of-gemini-round5.json`)

Đã kiểm chứng từng phát hiện (11/11) trực tiếp trên code thật. Cả **11/11 phát hiện đều có lỗi thật hoặc khoảng hở test thật** và đều đã được sửa tối giản kèm test hồi quy (không có phát hiện nào bị bác bỏ). Riêng phát hiện #1 có một **đánh đổi thực tế** cần ghi rõ để theo dõi khi chạy đường thật:

- **Phát hiện #1 (Cao — `NavNotificationListener.verifySelectedAlive` & `docs/protocol.md` dòng 17/34)**:
  - **Đã sửa**: Bỏ hoàn toàn `NavBus.touchMaps()`. `verifySelectedAlive()` nay tách rõ hai việc: (1) nếu thông báo biến mất/mất `FLAG_ONGOING_EVENT` → về `IDLE` ngay; (2) nếu có bản đăng cùng key với `postTime > selectedPostTime` (lỡ callback) → đọc bù bằng `handle(active, seed = true)` theo đúng tuổi `postTime`. Nếu `postTime` không đổi thì **không** làm mới `a`. Đã thống nhất định nghĩa `a` ở dòng 17 và dòng 34 của `docs/protocol.md`.
  - **Đánh đổi UX thực tế cần lưu ý**: Trước đây khi kiểm trên OPPO CPH2873 + Google Maps 26.47 lúc đứng yên tại chỗ, Google Maps không đăng lại thông báo cho tới khi phút ETA đổi (có thể tới 60 s). Ngưỡng 10 s cũ (watch) và 15 s (phone) khiến xe dừng đèn đỏ >10 s hiện `MẤT DỮ LIỆU`. **Đã xử lý ở 0.4.2** (xem mục bên dưới): `k=1` nay tươi tới 10 s, im lặng "cũ" tới 65 s (không báo mất dữ liệu), và phone giữ `k=1` tới 75 s. Chưa chạy đường thật đủ dài để xác nhận ngưỡng 65 s/75 s.
- **Phát hiện #6 (Vừa — `ProgressStyle` mét còn lại)**: Đã siết `extractProgressRemainingM` chỉ chấp nhận `category == "navigation"`, `template` có `ProgressStyle`, `!indeterminate`, có `android.progressSegments` không rỗng và `progressMax > 100` (loại thang phần trăm 0..100 mặc định của Android). **Chưa kiểm lại trên thông báo Google Maps 26.47 sống sau khi thêm điều kiện `android.progressSegments`**: nếu một phiên bản Maps nào đó không điền extra `android.progressSegments`, trường `r` sẽ an toàn về `-1` (ẩn km còn lại, không ảnh hưởng mũi tên hay `d`).
