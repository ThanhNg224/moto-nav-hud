# Moto Nav HUD – app Android (phone)

Đọc notification dẫn đường của Google Maps, đổi thành `NavState` và đẩy sang HUD trên Xiaomi Watch S5 qua BLE GATT (phone = peripheral). Hợp đồng dữ liệu: `../docs/protocol.md` (v1.4) là nguồn sự thật duy nhất.

Phiên bản: phone **0.5.0** (`versionCode` 7); watch 0.4.2 (`versionCode` 8).

## Tính năng chính (0.5.0)

- **Màn hình chính mới**: hero đồng hồ, mục Quick Go (địa điểm nhanh), mục Trạng thái và dock điều hướng dưới cùng.
- **Mở Google Maps**: bấm địa điểm để mở Maps qua `google.navigation`. Chế độ mặc định là xe máy; đổi sang Ô tô, Xe đạp hoặc Đi bộ trong Cài đặt.
- **Địa điểm nhanh**: Nhà, Cơ quan và một mục khác (tối đa 3), lưu địa chỉ và tọa độ lat/lng.
- **Phím tắt launcher** theo địa điểm đã lưu; nút "Về Maps" trên thông báo; Quick Settings tile bật dịch vụ và mở Maps (tùy chọn `open_maps_on_start`, mặc định bật).
- **Quyền vị trí (tùy chọn)**: chỉ dùng để lấy vị trí hiện tại; không cần Google Play services. Không cấp quyền thì các tính năng khác vẫn dùng bình thường.
- Viền đồng hồ nền tối theo dark theme. Giao thức BLE và payload không đổi so với 0.4.2.

## Cấu trúc

```
app/src/main/java/dev/motonavhud/
  core/   (Kotlin thuần, không import Android, có unit test)
    NavState.kt                 trạng thái + hằng số maneuver
    PayloadBuilder.kt           JSON v1.1, ngân sách min(180, MTU-3) byte, cắt e rồi s theo grapheme
    Throttle.kt                 z/q/i/a, gửi khi đổi, heartbeat 1 s, k=1 im >75 s -> k=0, k=3 im >15 s -> k=0 (k=2 không hết hạn)
    MapsNotificationParser.kt   heuristic Việt + Anh, bảng luật (ManeuverRule, regex) dễ thêm mẫu thật
    Transport.kt                giao diện kênh truyền (BLE hiện tại, XMS thêm sau)
    AutoStartGate.kt            tự bật BLE service khi Maps bắt đầu dẫn đường
    ManeuverIconClassifier.kt   phân loại dự phòng largeIcon (mũi tên) khi chữ không có chỉ hướng
    PlacesCodec.kt              mã hóa/giải mã danh sách địa điểm nhanh sang JSON
    QuickPlace.kt               model địa điểm nhanh (tên, địa chỉ, lat/lng tùy chọn)
    TravelMode.kt               phương tiện Google Maps (urlMode cho google.navigation, webMode cho https)
  app/
    ArrowPreviewView.kt         vòng + mũi tên maneuver thu nhỏ trên phone, đồng bộ với Watch S5
    BlePeripheralServer.kt      GATT server + advertising, implement Transport
    EventLog.kt                 nhật ký sự kiện bền vững files/events.log (+ events.log.1)
    LocationHelper.kt           vị trí hiện tại + geocoding bằng API nền tảng (không Play services)
    MainActivity.kt             UI (View lập trình bằng code, không Compose)
    MapsLauncher.kt             mở Maps hoặc dẫn đường tới điểm đã lưu theo phương tiện; không ném lỗi
    NavBus.kt                   ảnh chụp bất biến của nguồn Maps, đọc/ghi nguyên tử
    NavForegroundService.kt     foregroundServiceType=connectedDevice, vòng lặp 200 ms, Demo
    NavLog.kt                   logcat tag "MotoNav", sự kiện ngắn; không log nguyên văn notification
    NavNotificationListener.kt  lọc com.google.android.apps.maps, chỉ notification ongoing
    NavTileService.kt           ô Quick Settings bật/tắt HUD và mở Maps
    NotificationCapture.kt      ghi JSON + icon PNG để làm fixture thật
    Palette.kt                  bảng màu chung phone + watch
    PermissionFlow.kt           quyền và hành động dùng chung MainActivity/SettingsActivity
    PlacesActivity.kt           màn Điểm đến nhanh (3 ô + form sửa)
    PlacesStore.kt              lưu điểm đến nhanh trong SharedPreferences "places"
    Prefs.kt                    cờ cài đặt trong SharedPreferences (tự bật, phương tiện, Capture)
    QuickLaunchActivity.kt      Activity trong suốt cho shortcut/thông báo: bật HUD rồi mở Maps
    SettingsActivity.kt         màn Cài đặt (Tự động, Maps, Quyền, Đồng hồ, Nâng cao)
    ShortcutPublisher.kt        đẩy điểm đến thành shortcut động (nhấn giữ icon app)
    Ui.kt                       hàm dựng View dùng chung (chữ, đường kẻ, nền bo góc, công tắc)
    UiLogic.kt                  logic thuần của UI, test được trên JVM
    WatchBezelView.kt           viền Xiaomi Watch vẽ tay, chứa ArrowPreviewView
app/src/main/res/xml/
  shortcuts.xml                 shortcut tĩnh của launcher (mở Maps dẫn đường)
app/src/test/java/dev/motonavhud/
  core/   AutoStartGateTest, ManeuverIconClassifierTest, MapsNotificationParserTest,
          PayloadBuilderTest, PlacesCodecTest, QuickPlaceTest, ThrottleTest, TravelModeTest
  app/    EventLogTest, NotificationCaptureDedupeTest, PaletteTest, UiLogicTest
```

BLE: service `6e4f0001-b5a3-f393-e0a9-e50e24dcca9e`, NAV characteristic `6e4f0002-...` chỉ **Notify** (không Read, CCCD 0x2902 chỉ nhận 0x0001/0x0000 ở offset 0, từ chối prepared write). Gói quảng bá chính chứa cờ + service UUID. Scan response chứa **tên Bluetooth hệ thống** của điện thoại (Android không cho đặt tên quảng bá tùy ý nếu không đổi tên adapter, nên không phải `MotoNavHUD`); nếu tên quá dài làm quảng bá lỗi `DATA_TOO_LARGE` thì app tự thử lại không kèm tên. Đồng hồ phải lọc theo service UUID, không theo tên.

Notify: chỉ gửi cho peer đã ghi CCCD, snapshot ngay khi subscribe, **một notify đang bay trên toàn server**, mỗi peer giữ duy nhất tin mới nhất; payload được dựng lúc gửi thật nên tuổi `a` và hết hạn luôn mới. MTU từng peer: gói không bao giờ vượt `min(180, MTU-3)`; lõi tệ nhất (z 10 chữ số, p 8 ký tự) khoảng 102 byte nên cần MTU >= 107, nếu không phone **không gửi** và hiện cảnh báo trên màn hình.

Mã ghép `p`: mặc định `MOTONAV1` (khớp `watch-app/src/common/config.js`), sửa được trong app (1-8 ký tự A-Za-z0-9) và có hiệu lực ngay ở tin kế tiếp.

Service giữ **PARTIAL_WAKE_LOCK** (timeout 10 phút, tự gia hạn) khi đang chạy để heartbeat 1 Hz không bị treo lúc tắt màn hình; lock được nhả khi dừng service. Tắt Bluetooth lúc khởi động thì service dừng kèm lý do; tắt/bật Bluetooth giữa phiên thì GATT server được dựng lại tự động.

## Build, test, cài

Cần JDK 17+ (đang dùng 21) và Android SDK (`local.properties` có `sdk.dir`, file này bị git bỏ qua). AGP 8.13.2, Gradle 8.14.6 (wrapper), Kotlin 2.2.21, minSdk 29, targetSdk 34.

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.motonavhud.app/.MainActivity
```

Ký bằng debug keystore mặc định. `*.jks` đã nằm trong `.gitignore`.

## Cấp Notification access (Android 13+, app cài ngoài store)

1. Mở app, bấm "Mở cài đặt Notification access". Nếu mục Moto Nav HUD bị mờ/báo "Restricted setting": thoát ra, vào Cài đặt > Ứng dụng > Moto Nav HUD > menu ⋮ (góc trên phải) > **Cho phép cài đặt bị hạn chế** (Allow restricted settings), rồi quay lại bật.
2. Bật công tắc Moto Nav HUD. Android 15+ có thể hỏi xác nhận bổ sung.
3. Cấp quyền Bluetooth và thông báo khi app hỏi (nút "Cấp quyền Bluetooth / thông báo").
4. (Tùy chọn) Cấp quyền vị trí (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) để dùng vị trí hiện tại cho địa điểm nhanh. Bỏ qua thì HUD vẫn chạy.

## Dùng Demo

Bấm "Demo": app tự bật service (nếu chưa chạy) và cho chạy 13 maneuver, mỗi 4 giây, khoảng cách giảm 260 m -> 20 m trong mỗi bước (đủ thời gian quan sát mũi tên và nghe trọn cả rung ngắn ≤ 200 m lẫn rung khẩn ≤ 50 m). Thoát Demo sẽ ép gửi lại trạng thái thật (k=0 nếu Maps không có nguồn hợp lệ). Không cần Google Maps. Màn hình hiện tóm tắt chỉ dẫn, payload gần nhất và số đồng hồ đã nối.

## Capture (spike S4: lấy mẫu notification thật)

1. Bật công tắc "Capture notification Maps".
2. Bắt đầu dẫn đường trong Google Maps (thử cả chế độ xe máy, tiếng Việt).
3. Mỗi notification Maps khác nhau được ghi vào `/sdcard/Android/data/dev.motonavhud.app/files/captures/`: `capture-NNNN-<ms>.json` (toàn bộ extras dạng chuỗi, tag, channel, flags), cộng `-large.png` / `-small.png`. Ghi trên luồng nền, tối đa 500 bộ (đếm trên đĩa, giữ qua lần khởi động); nút "Xóa các capture đã lưu" xóa hết.
4. Lấy về máy: `adb pull /sdcard/Android/data/dev.motonavhud.app/files/captures ./captures`. Thêm các chuỗi thật vào bảng test `MapsNotificationParserTest` (thay fixture SYNTHETIC).

## Pin và tự khởi động

Tắt tối ưu hóa pin cho app (Cài đặt > Pin > Moto Nav HUD > Không hạn chế). Trên Xiaomi/HyperOS bật Autostart, đặt Tiết kiệm pin = Không hạn chế và khóa app trong màn hình đa nhiệm, nếu không hệ thống có thể giết service giữa chuyến đi.

## Nhật ký sự kiện

App ghi `files/events.log` (và `events.log.1` khi xoay vòng) để xem lại sau chuyến đi, gồm `maps_post gap_ms=` (khoảng im giữa hai lần Maps đăng) và `src_age_cross bucket=` (tuổi nguồn vượt 10 s/65 s). Cách đọc và dùng để chỉnh ngưỡng: xem mục "Nhật ký sự kiện" trong `../docs/HANDOFF.md`. Lệnh đọc: `adb shell run-as dev.motonavhud.app cat files/events.log.1 files/events.log`.

## Hạn chế đã biết

- Đã kiểm chứng thông báo Google Maps 26.47 thật trên OPPO Android 17 (`Notification$ProgressStyle` Live Updates, `en-VN`, chế độ xe máy, NBSP `\u00a0`, `progressMax - progress` mét còn lại); vẫn cần thu thêm mẫu tiếng Việt (`vi-VN`) trên đường thật để mở rộng bảng luật. Không nhận ra gì thì gửi `k=0`.
- Tuổi `a` gắn với lần Maps đăng thật gần nhất (`postTime`). Khi đứng yên (đèn đỏ/đường thẳng), Maps có thể không đăng lại trong khoảng 60 s; `a` tăng theo thời gian và đồng hồ hiện trạng thái "cũ" từ 10 s, "MẤT DỮ LIỆU" sau 65 s. Phone giữ `k=1` tới 75 s không có bản đăng mới rồi gửi `k=0`. Mỗi 3 s phone gọi `getActiveNotifications(keys)` để phát hiện thông báo biến mất hoặc đọc bù bản đăng mới hơn bị lỡ callback; kiểm tra này không làm mới `a`. Khi Maps tắt hoặc listener ngắt vẫn lập tức gửi `k=0`.
- Large icon chỉ là dự phòng: `ManeuverIconClassifier` chỉ nhận rẽ trái, rẽ phải và đi thẳng, và chỉ khi chữ không có chỉ hướng (không mơ hồ) và thông báo là category navigation. Các maneuver còn lại (U-turn, vòng xuyến, rẽ nhẹ/gắt, khởi hành, đến nơi) vẫn chỉ suy ra từ chữ.
- Giao diện 0.5.0 chưa kiểm chứng trên máy cho: nút "Về Maps" trên thông báo, phím tắt launcher, QS tile, vị trí hiện tại với fix GPS thật, tiếng Việt, cỡ chữ lớn, xoay ngang. Chưa biết Google Maps có tôn trọng `mode=l` ở Việt Nam hay không.
- Đã kiểm chứng BLE với Watch S5 thật (`peer` nối, MTU, notify liên tục). Android không cho đặt local name tùy ý trong quảng bá mà không đổi tên Bluetooth hệ thống, nên đồng hồ lọc theo service UUID (đúng theo hợp đồng).
- Chưa có kênh Mi Fitness / XMS interconnect (chỉ cần thêm một `Transport` mới).
- `a` luôn là 0 khi `k=0` (không có dữ liệu dẫn đường để già đi); `i` tăng khi (m,x,s) đổi, khi bắt đầu dẫn đường, hoặc khi `d` tăng hơn 30 m.
