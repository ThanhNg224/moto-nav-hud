# 03 – Nguồn dữ liệu chỉ đường phía điện thoại (Android)

> Track: lấy trạng thái turn-by-turn realtime trên Android để đẩy sang Watch S5. Ngày nghiên cứu: 2026-11-23.
> Quy ước độ tin cậy: **cao** = tài liệu chính thức hoặc mã nguồn đã đọc trực tiếp; **vừa** = nhiều nguồn thứ cấp khớp nhau, hoặc suy luận từ mã nguồn; **thấp** = forum/tin đồn/suy đoán chưa kiểm chứng.

---

## 0. Tóm tắt điều hành

- Google Maps **không có API công khai** nào cho app bên thứ ba đọc trạng thái dẫn đường đang chạy. Mọi cách "đọc từ Google Maps" đều là **reverse-engineering không chính thức**: đọc notification hoặc đọc UI qua Accessibility. (cao: không có API nào trong tài liệu Google Maps Platform/Android; các dự án lớn như Gadgetbridge cũng phải parse notification, xem §2)
- Cách thực tế nhất, đã được kiểm chứng rộng rãi là **NotificationListenerService đọc notification dẫn đường của Google Maps**. Gadgetbridge (đang được duy trì, commit gần nhất 2026-11-22) dùng `extras` (`android.title`, `android.text`, `android.subText`) và so khớp **large icon** với template bitmap 32x32. (cao, đã đọc mã nguồn)
- Rủi ro lớn nhất của cách này: **Android 16 "Live Updates"** (Maps dùng `Notification.ProgressStyle`) đã làm Gadgetbridge parse hỏng trên Pixel. Cách khắc phục hiện tại là người dùng tắt Live Updates của Maps trong Settings. (vừa–cao)
- Phương án chính thống, bền vững nhất là **Google Navigation SDK for Android**: tự làm app dẫn đường, có `TravelMode.TWO_WHEELER`, có **turn-by-turn data feed** (`NavInfo`/`StepInfo`, khoảng 1 lần/giây) dành cho màn hình phụ. Điều khoản **cho phép dùng không kèm Google Map**. Miễn phí 1.000 destination/tháng, sau đó $25/1.000. Đổi lại người dùng **không dùng app Google Maps** mà dùng app của mình. (cao)
- Đề xuất: **MVP = NotificationListener (Maps)**, kiến trúc có lớp `NavSource` trừu tượng để sau này cắm **Navigation SDK** (fallback chính thống) hoặc **OsmAnd/CoMaps** (API có cấu trúc, miễn phí) mà không đổi giao thức với đồng hồ.

---

## 1. Đánh giá từng phương án

### 1a. NotificationListenerService đọc notification của Google Maps

**Cơ chế.** Khi dẫn đường, Maps (`com.google.android.apps.maps`) đăng một notification ongoing. App có quyền Notification Access nhận `onNotificationPosted(StatusBarNotification)` mỗi lần Maps cập nhật.

**Có hai trường phái parse:**

| Trường phái | Dự án | Đọc gì | Đánh giá |
|---|---|---|---|
| **Extras (ổn định hơn)** | Gadgetbridge `GoogleMapsNotificationHandler.java` | `extras["android.title"]` = khoảng cách tới điểm rẽ (vd "100 yd"); `extras["android.text"]` = chỉ dẫn / tên đường (vd "High St towards Blah"); `extras["android.subText"]` = "13 min · 4.6 mi · 11:55 ETA", tách theo dấu `·`; `notification.getLargeIcon()` = mũi tên; chỉ nhận notification `localOnly` | Đơn giản, không phụ thuộc layout id nội bộ. Đang được duy trì. (cao, đã đọc mã nguồn: https://codeberg.org/Freeyourgadget/Gadgetbridge , file `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/externalevents/notifications/GoogleMapsNotificationHandler.java`) |
| **Inflate RemoteViews (giòn)** | GMapsParser (3v1n0), fork sang `esp32-google-maps` (maisonsmd, tác giả có vẻ là người Việt, font tiếng Việt) | `Notification.Builder.recoverBuilder(...).createBigContentView()`, inflate bằng resource của Maps, rồi duyệt view theo **resource entry name**: `nav_title`, `nav_description`, `nav_time`, `nav_notification_icon`, `lockscreen_directions`, `lockscreen_eta`, `right_icon`, `dismiss_nav`… | Phụ thuộc tên view nội bộ của Maps, rất dễ vỡ khi Maps đổi layout. GMapsParser có commit cuối 2022-08-24. (cao, đã đọc mã nguồn: https://github.com/3v1n0/GMapsParser , https://github.com/maisonsmd/esp32-google-maps , commit cuối 2025-07-25) |

**Mũi tên (maneuver) không có trong text, phải phân loại từ bitmap:**
- Gadgetbridge vẽ large icon vào canvas 32x32, lấy **bit alpha** thành 1bpp, tính khoảng cách Hamming (`Integer.bitCount(xor)`) với khoảng 33 template đã biết, khớp nếu diff < 32 bit. Icon lạ được log ra dạng chuỗi 0/1 để bổ sung template. Enum có 18 giá trị: `CONTINUE, TURN_LEFT/RIGHT (+SLIGHTLY, SHARPLY), KEEP_LEFT/RIGHT, UTURN_LEFT/RIGHT, OFFROUTE, ROUNDABOUT_LEFT/RIGHT/STRAIGHT/UTURN, FINISH, MERGE`. **Chưa có template cho SHARP turn** và chỉ có một phần biến thể RHD/LHD. (cao, mã nguồn `NavigationInfoSpec.kt` và handler)
- `esp32-google-maps` **không phân loại**: nén icon về 64x62 đen trắng 1bpp (khoảng 500 B), băm MD5 (lấy 10 ký tự), chỉ gửi bitmap một lần cho mỗi hash, còn các gói sau chỉ gửi `iconHash`. (cao, `BroadcastService.kt` dòng ~399–440)
- Suy luận: cho Watch S5, cách lai là tốt nhất: **phân loại ra enum** (nhẹ BLE, đồng hồ vẽ mũi tên vector tương phản cao), và **fallback gửi bitmap 1bpp có cache theo hash** khi icon không khớp template. (vừa, suy luận)

**Độ ổn định qua các phiên bản Maps / Android:**
- **Android 16 Live Updates:** Maps (từ v25.09.02) là app đầu tiên dùng Live Updates dựa trên `Notification.ProgressStyle`, có chip trên status bar hiển thị ETA và thời gian tới lượt rẽ. (vừa: https://9to5google.com/2025/03/06/google-maps-android-16-live-updates-feature/ ; API: https://developer.android.com/about/versions/16/features/progress-centric-notifications , cao)
- Gadgetbridge issue #5420 (09/2025): trên Pixel Android 16, Gadgetbridge **không còn parse được** notification Maps. Bật lại được bằng cách **tắt Live Updates** trong Settings, Apps, Maps, Notifications. Maintainer không tái hiện được trên 2 máy non-Pixel, nên có thể đang rollout dần. Câu hỏi "có đọc được nội dung Live Updates không" **chưa được trả lời**. (cao về triệu chứng: https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/5420 ; https://gadgetbridge.org/basics/integrations/navigation/)
- Diễn đàn Espruino/Bangle: Maps từng gửi khoảng cách theo đơn vị đổi giữa chừng (mét rồi feet). Parser phải đọc đơn vị trong chuỗi, không được giả định. (thấp–vừa: https://forum.espruino.com/conversations/386610)
- HyperOS 2/3 trên điện thoại Xiaomi có thể có cách hiển thị riêng ("Super Island"/focus notification) cho Live Updates. **Chưa kiểm chứng** nó có thay đổi `extras` mà listener nhận hay không. (thấp, câu hỏi mở)

**Locale tiếng Việt:**
- Gadgetbridge chỉ lấy ETA nếu chuỗi chứa literal `"ETA"`, nên **sẽ hỏng với tiếng Việt**. Regex khoảng cách `^\s*([0-9]+(?:[.,][0-9]+)?)\s*([a-zA-Z]+)$` có xử lý dấu phẩy thập phân ("1,2 km") và đơn vị m/km, nên dùng được với tiếng Việt. (cao, mã nguồn)
- Chưa có mẫu thật của chuỗi subText/text tiếng Việt (vd dạng "12 phút · 5,3 km · 10:35"?). **Phải chụp mẫu thật trong spike** (dump toàn bộ `extras` và icon) trước khi viết parser. Nên parse ETA theo regex giờ `\d{1,2}:\d{2}`, không theo từ khóa. (thấp cho dạng chuỗi; cao cho việc cần spike)

**Chế độ xe máy (two-wheeler):** Maps có motorbike mode tại Việt Nam từ 2018. (cao: https://e.vnexpress.net/news/news/google-maps-launches-motorcycle-mode-in-vietnam-3782007.html) Không tìm thấy tài liệu nào nói notification ở chế độ xe máy khác chế độ ô tô. Giả định là cùng format, cùng bộ icon maneuver. **Cần xác minh trong spike.** (thấp)

**Điều khoản:** Google Maps/Earth Additional Terms §2.5 cấm *"use any part of Google Maps with other people's products or services for or in connection with real-time navigation"*, trừ khi dùng tính năng Google cung cấp (vd Android Auto). Mirror notification Maps sang đồng hồ là **vùng xám/vi phạm về chữ**. Với công cụ cá nhân sideload, rủi ro bị cưỡng chế thực tế thấp, nhưng **không nên phát hành công khai** dưới dạng "dùng Google Maps". (cao về văn bản: https://www.google.com/help/terms_maps/ ; thấp về khả năng bị cưỡng chế)

### 1b. Google Navigation SDK for Android

- **Turn-by-turn data feed:** đăng ký một Service nhận `Messenger`, rồi `TurnByTurnManager` chuyển message thành `NavInfo`: `NavState` (ENROUTE/REROUTING/STOPPED), `StepInfo` (tên đường đầy đủ, maneuver enum gồm turn slight/sharp, U-turn, ramp, roundabout có exit, merge, fork, destination left/right, ferry), khoảng cách tới bước kế, lane guidance, thời gian và quãng đường còn lại. Cập nhật **khoảng 1 lần/giây**. Google ghi rõ dùng cho *"two-wheeled vehicle displays… small screens"*. Có tùy chọn tự sinh bitmap maneuver/lane. (cao: https://developers.google.com/maps/documentation/navigation/android-sdk/tbt-feed)
- **Two-wheeler:** `RoutingOptions.TravelMode.TWO_WHEELER = 3` có trong SDK Android. (cao: https://developers.google.com/maps/documentation/navigation/android-sdk/reference/com/google/android/libraries/navigation/RoutingOptions.TravelMode) Routes API liệt kê **VN** trong danh sách quốc gia hỗ trợ two-wheeler. (cao: https://developers.google.com/maps/documentation/routes/coverage-two-wheeled) Chưa có xác nhận riêng rằng Nav SDK dùng cùng bảng coverage. (vừa)
- **Headless:** có hướng dẫn chính thức chạy Nav SDK không hiển thị bản đồ. Location và callback vẫn chạy. (cao: https://developers.google.com/maps/architecture/navsdk-headless-mode) Trang này không nói rõ feed TBT vẫn chạy ở chế độ headless, nhưng feed là cơ chế độc lập với view. (vừa)
- **Điều khoản:** Service Specific Terms §12.1 *"may use Google Maps Content from the Navigation SDK… without a corresponding Google Map"*; §12.2 cấm dùng kèm bản đồ không phải Google. Hiển thị mũi tên và tên đường trên đồng hồ là **hợp lệ**. (cao: https://cloud.google.com/maps-platform/terms/maps-service-terms)
- **Chi phí:** SKU *Navigation Request* (Enterprise), **1.000 destination/tháng miễn phí**, bậc đầu **$25/1.000**. Không tính phí thêm khi bắt đầu dẫn đường hay reroute. (cao: https://developers.google.com/maps/billing-and-pricing/pricing ; https://developers.google.com/maps/documentation/navigation/android-sdk/pricing ; giảm giá 05/2025: https://mapsplatform.google.com/resources/blog/making-navigation-sdks-more-accessible-with-new-pricing/) Dùng cá nhân (dưới khoảng 30 chuyến/ngày) thì gần như **miễn phí**, nhưng vẫn phải có billing account.
- **Hạn chế:** app không được chứa đồng thời Maps SDK và Nav SDK. Phải tự làm UI chọn điểm đến (Places API tính phí riêng, hoặc nhận địa chỉ/intent share từ Maps). Người dùng bỏ thói quen mở Google Maps. Lần đầu phải chấp nhận hộp thoại Terms của SDK. (cao/vừa: https://developers.google.com/maps/documentation/navigation/android-sdk/overview)

### 1c. Android Auto / Automotive / Car App Library

- Car App Library `NavigationTemplate`/`MapWithContentTemplate` dùng để **làm app dẫn đường hiển thị lên head unit**, không giúp **đọc** dữ liệu của Google Maps. App sideload có thể chạy trên Android Auto bằng developer mode ("unknown sources"). (cao: https://developer.android.com/training/cars/apps/navigation ; https://developer.android.com/training/cars/testing)
- Ý tưởng "giả làm head unit Android Auto để nhận navigation status/cluster của Maps" phải reverse protocol AA (TLS, chứng chỉ head unit), quá phức tạp và dễ vỡ, không phù hợp. (thấp, suy luận)
- Kết luận: **không phải nguồn dữ liệu**. Chỉ hữu ích nếu tự làm app dẫn đường (1b/1f) và muốn thêm hiển thị AA.

### 1d. Maps URL/intents + Routes API / Directions API + tự theo dõi GPS

- Intent `google.navigation:` chỉ **khởi động** Maps, không trả dữ liệu về. (cao, hành vi đã biết của Maps URLs)
- Routes API hỗ trợ `travelMode: TWO_WHEELER`, Compute Routes Essentials miễn phí 10.000/tháng, $5/1.000. (cao: https://developers.google.com/maps/billing-and-pricing/pricing) Muốn dùng thì phải tự viết map-matching, phát hiện lệch tuyến, reroute, ngưỡng thông báo: khối lượng lớn và kém hơn Nav SDK.
- Điều khoản GMP §3.2.3(d)(iv) cấm kết hợp Directions/Geolocation/Maps SDK để *"create real-time navigation functionality substantially similar to… Google Maps for Android"*. Tự dẫn đường bằng Routes API là **vùng xám** về ToS. (cao về văn bản: https://cloud.google.com/maps-platform/terms ; vừa về việc áp dụng cho Routes API)
- Kết luận: **không đáng làm** khi đã có Nav SDK.

### 1e. AccessibilityService đọc UI Google Maps

- Đọc được text trên màn hình Maps (banner hướng dẫn), nhưng **chỉ khi Maps ở foreground hoặc màn hình bật**. Khi đi xe, điện thoại thường khóa màn hình, nên cách này không dùng được. Rất giòn vì view id bị obfuscate và đổi theo phiên bản. (vừa, suy luận)
- Play policy: app không phải công cụ trợ năng phải khai báo, bị cấm hành động tự động. Sideload thì bị chặn bởi "restricted settings" giống Notification Access. (cao: https://support.google.com/googleplay/android-developer/answer/10964491)
- Kết luận: **loại**.

### 1f. Engine bên thứ ba

| Engine | Two-wheeler | Dữ liệu maneuver | VN | Chi phí/ghi chú | Tin cậy |
|---|---|---|---|---|---|
| **OsmAnd (AIDL API)** | Có profile moped/motorcycle | `ADirectionInfo{distanceTo, turnType, isLeftSide}` qua `registerForNavigationUpdates`, có cấu trúc, không phải parse | OSM tại VN: đủ đường chính, kém ở hẻm/one-way/cấm xe máy | Miễn phí. Gadgetbridge đã tích hợp sẵn | cao (mã AIDL trong Gadgetbridge; https://gadgetbridge.org/basics/integrations/navigation/) ; VN chất lượng: thấp |
| **CoMaps** (fork Organic Maps) | Chưa xác minh | ContentProvider `car_direction`, `dist_to_turn`, `next_street`, `current_street`, `total_time_seconds`, `exit_num` | Như OSM | Miễn phí | cao (mã `CoMapsNavigationReceiver.java`) |
| **Vietmap Navigation SDK** | Có motorcycle (theo README RN) | Turn-by-turn, voice | Dữ liệu bản đồ VN chuyên biệt | Thương mại, cần API key | vừa (https://maps.vietmap.vn/docs/sdk-mobile/overview/ ; https://npmjs.com/package/@vietmap/vietmap-react-native-navigation) |
| **Mapbox Navigation SDK** | Profile driving/cycling, motorcycle chưa rõ | Banner instruction + maneuver đầy đủ | Dựa OSM + dữ liệu riêng | MAU + trip (free tier có, chưa lấy được con số) | vừa (https://docs.mapbox.com/android/navigation/guides/pricing) |
| **Valhalla** (tự host / dịch vụ) | `motor_scooter` (mặc định top speed 45 km/h, tránh đường lớn), `motorcycle` (beta) | Enum maneuver 0–43, `street_names` | OSM | Miễn phí, tự host, tự theo dõi GPS | cao (https://valhalla.github.io/valhalla/api/route/api-reference/) |
| OSRM / GraphHopper / HERE | Có/không tùy profile | Có | OSM / HERE | Chưa đánh giá sâu | thấp |

Nhận xét: tại VN, chất lượng định tuyến xe máy của Google (hẻm, một chiều, cấm xe máy) gần như chắc chắn vượt OSM. Đây là lý do người dùng muốn "lấy từ Google Maps". (thấp–vừa, suy luận; khảo sát HERE nói 50% người đi xe máy VN dùng app dẫn đường: https://e.vnexpress.net/news/tech/tech-news/50-of-motorbike-riders-in-vietnam-regularly-use-navigation-apps-5075233.html)

---

## 2. Prior art (đã đọc mã nguồn hoặc README)

| Dự án | Nguồn dữ liệu | Truyền | Bài học |
|---|---|---|---|
| **Gadgetbridge** (AGPL, đang hoạt động) | Maps: notification extras + template icon; OsmAnd: AIDL; CoMaps: ContentProvider | BLE tới Bangle.js, Amazfit, Garmin, **Xiaomi** ("navigation cards") | Mẫu tham chiếu tốt nhất. Vỡ do Live Updates (#5420). Nên đọc thêm phần Xiaomi protocol cho track truyền dữ liệu. https://codeberg.org/Freeyourgadget/Gadgetbridge |
| **GMapsParser** (LGPL-3.0) | Inflate RemoteViews theo tên view nội bộ | Bundle/Parcelable, websocket JSON/CBOR | Giòn, ngừng từ 2022 |
| **esp32-google-maps / CatDrive** (maisonsmd) | Fork GMapsParser | BLE GATT; text dạng key=value; icon 64x62 1bpp cache theo MD5 | Hướng "gửi bitmap + hash" né được bài toán phân loại; app chạy FGS `location`. https://github.com/maisonsmd/esp32-google-maps |
| **CarPlayBLE** | NotificationListener | BLE (ESP32 là GATT server) | Hiển thị ETA, còn lại, đường hiện tại, khoảng cách maneuver. https://github.com/appleshaman/CarPlayBLE |
| **PebbleNTN** (F-Droid, MIT) | NotificationListener, luật parse khai báo theo app | Pebble | Nói rõ mũi tên và ETA của Maps phụ thuộc dữ liệu chỉ có trong notification. https://f-droid.org/packages/com.pebblentn.app/ |

---

## 3. Ràng buộc runtime Android cho chuyến đi dài

1. **NotificationListenerService** do hệ thống bind, không cần FGS để nhận notification. Khi bị ngắt, gọi `requestRebind(ComponentName)` trong `onListenerDisconnected()`; đây là API duy nhất an toàn ở trạng thái đó. (cao: https://developer.android.com/reference/android/service/notification/NotificationListenerService) Có báo cáo rebind ngừng hoạt động sau vài lần và listener chết sau reboot trên Xiaomi (thấp: https://gist.github.com/xinghui/b2ddd8cffe55c4b62f5d8846d5545bf9 ; https://bugs.kde.org/show_bug.cgi?id=515781). UI app cần có màn **health check** (listener connected? lần cuối nhận Maps?) và nút mở Notification Access để bật/tắt lại.
2. **Foreground service cho kết nối đồng hồ:** Android 14+ bắt buộc khai báo type. Dùng `connectedDevice` + `FOREGROUND_SERVICE_CONNECTED_DEVICE`, điều kiện runtime là quyền Bluetooth (`BLUETOOTH_CONNECT`…). (cao: https://developer.android.com/develop/background-work/services/fgs/service-types) Chỉ thêm `location` nếu tự lấy GPS (Nav SDK/engine riêng).
3. **Khởi động FGS từ nền** (vd khi Maps bắt đầu dẫn đường mà app đang ở nền) bị chặn từ Android 12, trừ các ngoại lệ: **người dùng tắt battery optimization cho app**, hoặc dùng **CompanionDeviceManager** với `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`. (cao: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) Khuyến nghị: yêu cầu bỏ tối ưu pin. Kết nối đồng hồ do app Xiaomi quản lý thì CDM có thể không áp dụng (phụ thuộc track kênh truyền).
4. **Doze:** FGS + listener đang bind không bị Doze dừng. Wakelock không cần nếu luồng là event-driven (notification tới thì gửi BLE). (vừa)
5. **Xiaomi HyperOS/MIUI:** cần **Autostart = bật**, **Battery saver = No restrictions**, tùy chọn khóa app trong Recents. Có báo cáo notification ngừng sau một thời gian dù đã cấu hình. (vừa, nhiều nguồn thứ cấp: https://dontkillmyapp.com/xiaomi ; https://www.xiaomi.eu/community/threads/how-to-fix-notification-sync-delay-after-upgrading.75846 ; https://traccar.org/forums/reply/98688) Phải đo thực tế trên máy của người dùng.
6. **Sideload và "Restricted settings" (Android 13+):** app cài ngoài store (không qua session installer) bị **chặn cấp Notification Access**, người dùng phải vào App info, menu ⋮, *Allow restricted settings*. Android 15 thêm Enhanced Confirmation Mode. Cài bằng `adb install` hoặc trình cài dùng session API thường không bị chặn. (vừa: https://9to5google.com/2024/09/12/android-15-sideloaded-apps-restrictions/ ; https://bayton.org/android/android-13-restricted-permissions/)
7. **Developer verification (2026–2027):** hiệu lực 30/09/2026 tại Brazil, Indonesia, Singapore, Thái Lan; toàn cầu (gồm VN) từ 2027. **`adb install` vẫn được phép** cho phát triển. Có tài khoản "limited distribution" miễn phí (tối đa 20 thiết bị) và luồng "advanced" cho power user. Công cụ cá nhân không bị chặn. (cao: https://support.google.com/android-developer-console/answer/16561738)
8. **Play Store:** Notification Access app phải chứng minh đó là tính năng cốt lõi. Mirror Maps cho dẫn đường còn vướng ToS Maps §2.5 (mục 1a). Khuyến nghị **sideload-only** cho bản cá nhân. (vừa)

---

## 4. Đề xuất data model và throttle

### 4.1 Mô hình logic (độc lập nguồn)

```text
NavState {
  seq        u16     // tăng mỗi gói, phát hiện mất gói
  state      u8      // 0 IDLE, 1 ENROUTE, 2 REROUTING, 3 ARRIVED, 4 STOPPED
  maneuver   u8      // enum dưới đây; 0xFF = UNKNOWN (kèm iconHash)
  exitNum    u8      // số exit vòng xuyến, 0 = không có
  distM      u16     // mét tới điểm rẽ (đã quy đổi; 65535 = >65 km)
  street     utf8    // tên đường kế tiếp, cắt <= 48 byte UTF-8
  remainM    u32     // quãng đường còn lại
  remainS    u32     // thời gian còn lại (giây)
  etaMin     u16     // ETA = phút trong ngày theo giờ địa phương
  alert      u8      // 0 none, 1 PREPARE(~300 m), 2 NOW(~50 m), 3 REROUTE, 4 ARRIVE: phone quyết định để đồng hồ rung
  iconHash   u32     // optional: hash bitmap khi maneuver = UNKNOWN
  src        u8      // 0 GMAPS_NOTIF, 1 NAV_SDK, 2 OSMAND, 3 COMAPS
}
```

Enum `maneuver` (đề xuất: gộp Gadgetbridge và Nav SDK, không dùng giá trị phụ thuộc bên trái/phải đường):
`1 STRAIGHT, 2 LEFT, 3 SLIGHT_LEFT, 4 SHARP_LEFT, 5 RIGHT, 6 SLIGHT_RIGHT, 7 SHARP_RIGHT, 8 KEEP_LEFT, 9 KEEP_RIGHT, 10 UTURN_LEFT, 11 UTURN_RIGHT, 12 ROUNDABOUT_LEFT, 13 ROUNDABOUT_RIGHT, 14 ROUNDABOUT_STRAIGHT, 15 ROUNDABOUT_UTURN, 16 MERGE, 17 FORK_LEFT, 18 FORK_RIGHT, 19 RAMP_LEFT, 20 RAMP_RIGHT, 21 DEPART, 22 ARRIVE, 23 ARRIVE_LEFT, 24 ARRIVE_RIGHT, 25 FERRY, 0xFF UNKNOWN`.

**Speed limit:** notification Maps **không có**. `NavInfo` của Nav SDK cũng không có trường speed limit. Nav SDK có cơ chế cảnh báo tốc độ riêng nhưng chưa xác minh dữ liệu đó có lộ ra cho app hay không (thấp). Không đưa vào MVP.

### 4.2 Định dạng

- **Debug/dev:** JSON mini với key ngắn (`{"s":1,"m":5,"d":180,"st":"Nguyễn Huệ","eta":635,"a":1}`), khoảng 60–90 B.
- **Sản xuất:** binary TLV hoặc cố định khoảng 20 B + chuỗi street. Cả hai đều vừa một gói BLE/bridge nhỏ. Định dạng cuối cùng phụ thuộc kênh truyền và API JS phía Vela (track khác) quyết định. Có thể đồng hồ chỉ nhận string/JSON thì giữ JSON.
- **Bitmap fallback:** 1bpp 48x48 (288 B) hoặc 64x64 (512 B), gửi **một lần cho mỗi hash**, đồng hồ cache LRU (vd 16 icon).

### 4.3 Throttle (phía điện thoại)

- **Gửi ngay** khi: `state`, `maneuver`, `street`, `exitNum` đổi, hoặc `alert` vượt ngưỡng.
- **Khoảng cách:** chỉ gửi khi đổi bucket hiển thị: >1 km bước 100 m; 200–1000 m bước 50 m; <200 m bước 10 m. Tối đa **1 gói/giây** (Nav SDK cũng chỉ khoảng 1 Hz).
- **ETA/remain:** gửi kèm khi có gói khác, hoặc tối đa 1 lần/30 s.
- **Keepalive:** 10 s không có gì thì gửi lại gói gần nhất để đồng hồ biết link còn sống. Đồng hồ hiện trạng thái "mất tín hiệu" nếu >20 s không nhận gì.
- **Dedupe:** Maps có thể đăng lại notification giống hệt nhau, nên so sánh payload đã chuẩn hóa trước khi gửi.
- **Ngưỡng rung:** phía điện thoại tính `alert` (PREPARE khi khoảng 300 m, NOW khi khoảng 50 m; khi đi xe máy trong phố có thể 150 m/30 m, cho phép cấu hình) để đồng hồ chỉ việc rung, không phải tự suy luận.

---

## 5. Bảng xếp hạng phương án

| Hạng | Phương án | Dùng Google Maps của người dùng | Two-wheeler VN | Độ bền | ToS | Chi phí | Công sức | Khuyến nghị |
|---|---|---|---|---|---|---|---|---|
| **1** | NotificationListener đọc Maps (extras + template icon, fallback bitmap) | Có | Có (motorbike mode của Maps) | Trung bình–thấp (Live Updates, đổi icon/chuỗi) | Xám (§2.5 Maps terms) | 0 | Thấp | **MVP, chỉ sideload** |
| **2** | Google Navigation SDK + TBT feed, `TWO_WHEELER` | Không (app riêng) | Có (Routes coverage gồm VN, vừa) | Cao (API chính thức) | Hợp lệ (§12.1) | ~0 khi dưới 1.000 dest/tháng | Trung bình (UI tìm điểm đến, billing) | **Fallback chính thống / nếu công khai** |
| 3 | OsmAnd AIDL / CoMaps provider | Không | Có profile, dữ liệu OSM VN yếu | Cao (API công khai) | Hợp lệ | 0 | Thấp (Gadgetbridge làm mẫu) | Nguồn phụ, test giao thức |
| 4 | Vietmap Navigation SDK | Không | Có, dữ liệu VN | Cao | Theo hợp đồng | Thương mại | Trung bình | Cân nhắc nếu Google không đạt |
| 5 | Routes API + tự tracking GPS | Không | Có | Phụ thuộc mã tự viết | Xám (§3.2.3(d)(iv)) | Thấp | Cao | Không |
| 6 | Mapbox / Valhalla / GraphHopper / HERE | Không | Một phần | Cao | Hợp lệ | Thay đổi | Cao | Không cho MVP |
| 7 | AccessibilityService đọc UI Maps | Có | Có | Rất thấp, cần màn hình bật | Xám + Play cấm | 0 | Trung bình | Loại |
| 8 | Android Auto / Car App Library | Không áp dụng | Không | Không | Không | Không | Rất cao | Loại (không phải nguồn dữ liệu) |

---

## Kết luận ngắn

- Premise "lấy dữ liệu realtime từ Google Maps trên Android" **chỉ đúng một phần**: không có API chính thức. Cách duy nhất thực tế là **parse notification dẫn đường** bằng NotificationListenerService, như Gadgetbridge đang làm. Cách này làm được, chi phí 0, giữ trải nghiệm Google Maps (gồm motorbike mode VN), nhưng **giòn** và **vi phạm về chữ** Maps Additional Terms §2.5. Chỉ nên dùng làm công cụ cá nhân sideload.
- Parse theo `extras` (`title`/`text`/`subText`) + phân loại large icon bằng template 1bpp. Bổ sung fallback gửi bitmap có cache hash. Không parse RemoteViews theo view id.
- Nếu cần bền vững hoặc phát hành: **Google Navigation SDK** (TWO_WHEELER, TBT feed khoảng 1 Hz, được phép dùng không kèm map, gần như miễn phí ở quy mô cá nhân). Đổi lại phải dùng app riêng thay vì Google Maps.
- Thiết kế `NavSource` trừu tượng + payload chung (mục 4) để đổi nguồn mà không đổi đồng hồ.

## Rủi ro chặn

1. **Live Updates / ProgressStyle (Android 16+) và có thể HyperOS "Super Island"** làm notification Maps không còn text/icon parse được. Workaround hiện tại phụ thuộc việc người dùng tắt Live Updates của Maps. Google có thể bỏ tùy chọn này. Cần spike trên đúng điện thoại của người dùng.
2. **Locale tiếng Việt chưa có mẫu**: dạng chuỗi ETA/khoảng cách/chỉ dẫn tiếng Việt và bộ icon ở motorbike mode chưa được kiểm chứng.
3. **HyperOS kill app / listener chết sau reboot**, ảnh hưởng độ tin cậy trong chuyến đi dài. Cần đo thực tế.
4. **Sideload + Restricted settings / Enhanced Confirmation Mode** có thể làm khó việc cấp Notification Access trên Android 15+ của Xiaomi. Cài qua `adb` để giảm rủi ro.
5. **ToS Google Maps §2.5**: không chặn dùng cá nhân, nhưng chặn phát hành công khai theo hướng mirror Maps.

## Câu hỏi còn mở

1. Điện thoại của người dùng là máy gì, Android/HyperOS bản nào, Maps có bật Live Updates không? (quyết định rủi ro 1)
2. Người dùng chấp nhận **dùng app dẫn đường riêng** (Nav SDK) thay vì Google Maps không, nếu cách notification không ổn định? Có sẵn sàng tạo billing account GMP không?
3. Đồng hồ (Vela JS) có vẽ được bitmap 1bpp nhận qua kênh truyền không, hay chỉ vẽ được mũi tên vector theo enum? (ảnh hưởng chiến lược icon)
4. Notification Maps ở motorbike mode, locale vi-VN có cấu trúc `extras` giống locale en không? Cần dump `extras` + icon trong spike, khoảng 30 phút đi thử.
5. Sản phẩm chỉ dùng cá nhân hay có ý định phát hành (Play/cộng đồng)? Câu trả lời quyết định hạng 1 hay hạng 2 là kiến trúc chính.
6. Gadgetbridge đã có "navigation cards" cho đồng hồ Xiaomi. Cần track kênh truyền kiểm tra Watch S5 có hỗ trợ native navigation card qua protocol Xiaomi không. Nếu có, có thể không cần mini-app `.rpk` cho phần hiển thị cơ bản.
