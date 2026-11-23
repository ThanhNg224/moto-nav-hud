# 02 - Kênh truyền Điện thoại (Android) -> Đồng hồ (Xiaomi Watch S5, Vela QuickApp)

> Track: phone <-> watch transport. Ngày: 2026-11-23. Bám theo `00-brief.md`.
> Ký hiệu độ tin cậy: **cao** = tài liệu chính thức Xiaomi / mã nguồn đã merge; **vừa** = repo cộng đồng, nhiều nguồn khớp nhau; **thấp** = suy luận, diễn đàn, chưa đo.

## TL;DR

Có kênh chính thức cho app bên thứ ba: **`@system.interconnect`** (phía đồng hồ) + **XMS Wearable SDK `com.xiaomi.xms.wearable`** (phía Android), cầu nối là app **Mi Fitness (Xiaomi 运动健康 / Xiaomi Wear)** đang chạy trên điện thoại. Điều kiện cứng: **package name của rpk = applicationId của APK** và **rpk ký bằng cùng certificate với APK**. Không thấy cơ chế whitelist/duyệt từ Xiaomi để dùng kênh này khi sideload (Mi Fitness tự cấp quyền ở lần gọi đầu). Payload 1 Hz cho nav HUD là rất nhỏ, về băng thông không phải vấn đề; vấn đề thật là **độ bền nền trên Android/HyperOS** (cả app của mình và Mi Fitness phải sống) và **SDK phía phone không được phát hành công khai** (chỉ có AAR rò rỉ trong demo, hoặc bản clean-room MIT). Watch S5 còn có thêm một kênh dự phòng: **QuickApp làm BLE central (GATT client)** — chỉ S5 hỗ trợ, cấm chạy nền.

---

## 1. Kênh chính thức: `@system.interconnect` + XMS Wearable SDK qua Mi Fitness

### 1.1 Phía đồng hồ (QuickApp)

- Khai báo `{ "name": "system.interconnect" }`; `interconnect.instance()` trả về singleton `connect`. Kết nối **tự động thiết lập**, app không tự mở/đóng. Nguồn: https://iot.mi.com/vela/quickapp/en/features/network/interconnect.html — **cao**.
- API: `connect.send({data: Object, success, fail})` (lỗi 204 timeout, 1006 disconnected); `connect.onmessage({data: String})`; `onopen({isReconnected})`; `onclose`; `onerror` (1000 unknown, **1001 = app điện thoại chưa cài**, 1006 disconnected); `getReadyState` (1 connected / 2 disconnected); `diagnosis({timeout})` (0 OK, 204 CONNECT_TIMEOUT, 1001 APP_UNINSTALLED). Cùng nguồn — **cao**.
- Trang **không nêu giới hạn kích thước payload**, không có bảng thiết bị hỗ trợ, không nói interconnect có chạy nền được không. Cùng nguồn — **cao** (về việc tài liệu im lặng).
- Ràng buộc (nguyên văn): *"Ensure that the package field in the Quick App's manifest.json matches the package name of the third-party Android app"* và *"The Quick App signature must use the signature of the third-party Android app."* `private.pem`/`certificate.pem` sinh từ `.jks` của APK, đặt vào `/sign/debug` và `/sign/release`. Cùng nguồn — **cao**.
- FAQ: chữ ký được kiểm tra trước khi giao tiếp, sai là bị từ chối; đổi certificate có thể không lên store được; đọc trạng thái ngay khi vào trang thường ra `DISCONNECTED` nên cần poll. Nguồn: https://iot.mi.com/vela/quickapp/en/guide/other/faq.html — **cao**.
- Mẫu chính thức "Cross-Device Image Transfer" dùng interconnect, phone gửi ảnh dạng **Base64 fragments** (header/data/end) → ngụ ý có giới hạn kích thước mỗi message, phải tự chia gói. Nguồn: https://iot.mi.com/vela/quickapp/en/samples/ — **cao** (về mẫu), **thấp** (về con số giới hạn, chưa có).

### 1.2 Phía Android: XMS Wearable SDK ("小米穿戴第三方APP能力开放接口" v1.4)

Đã tải và đọc PDF chính thức được link từ trang interconnect: https://vela-docs.cnbj1.mi-fds.com/vela-docs/files/%E5%B0%8F%E7%B1%B3%E7%A9%BF%E6%88%B4%E7%AC%AC%E4%B8%89%E6%96%B9APP%E8%83%BD%E5%8A%9B%E5%BC%80%E6%94%BE%E6%8E%A5%E5%8F%A3%E6%96%87%E6%A1%A3_1.4.pdf — **cao**.

- Mô hình API giống Google Wear Data Layer (Task + listener): `Wearable.getNodeApi/getAuthApi/getMessageApi/getNotifyApi/getServiceApi(context)`.
- `NodeApi.getConnectedNodes()` — không cần quyền; ghi chú *"目前一次只能连接一个设备"* (mỗi lúc chỉ 1 thiết bị).
- `AuthApi.checkPermission/requestPermission(nodeId, Permission.DEVICE_MANAGER, Permission.NOTIFY)`; *"第一次调用授权会默认授予 DEVICE_MANAGER 和 NOTIFY 权限"* → **không có bước duyệt/whitelist phía server** trong tài liệu.
- `NodeApi.query(...)`: trạng thái kết nối, pin, sạc, đeo tay, ngủ; `subscribe(...)` theo dõi kết nối/sạc/đeo/ngủ; `isWearAppInstalled(nodeId)`; **`launchWearApp(nodeId, uri)` — mở app trên đồng hồ từ điện thoại** (hữu ích: khi bắt đầu dẫn đường, phone tự bật HUD trên đồng hồ).
- `MessageApi.sendMessage(nodeId, byte[])` / `addListener(nodeId, OnMessageReceivedListener)` — **hai chiều**, cần `DEVICE_MANAGER`. Ví dụ dùng `byte[1024]`; **không ghi giới hạn kích thước, rate, latency**.
- `NotifyApi.sendNotify(nodeId, title, message)` — đẩy thông báo hệ thống lên đồng hồ, *"表端应用无感知"* (app trên đồng hồ không nhận được). Dùng được làm fallback.
- `ServiceApi.registerServiceConnectionListener` — theo dõi kết nối giữa app mình và **app Xiaomi Wear/Mi Fitness** → xác nhận SDK là IPC (Binder) sang app Xiaomi, không phải BLE trực tiếp.
- Changelog 1.3: tương thích việc hợp nhất Xiaomi Wear và Xiaomi Health (Mi Fitness); 1.4: tương thích Android R. **SDK cũ (Android 11 era)** — rủi ro với Android 14/15/16 (package visibility, FGS type) — **thấp/vừa**, cần spike.
- **AAR không được phát hành công khai**: PDF không có link Maven; repo cộng đồng ghi SDK là proprietary, *"不随仓库分发"*, phải dùng reflection hoặc bản clean-room. Nguồn: https://github.com/Explore0416/SuchMusic-Concept — **vừa**. AAR `xms-wearable-lib_1.4_release.aar` được nhắc tới trong https://github.com/RogerLang/codex-quota-band — **vừa**. Khả năng AAR nằm trong demo zip chính thức https://cdn.cnbj3-fusion.fds.api.mi-img.com/quickapp-vela/interconnect_dev_test_demo.zip (chưa tải để kiểm) — **thấp**.
- Thay thế mã mở: **OrPudding/XMS_Wearable_SDK_CleanRoom** (MIT) tái hiện public API + Binder contract của SDK 1.4, giữ package `com.xiaomi.xms.wearable.*`, backend `XIAOMI` bind vào service của Mi Fitness/Xiaomi Wear. Nguồn: https://github.com/OrPudding/XMS_Wearable_SDK_CleanRoom — **vừa**. Đã được dùng với Band 9 Pro qua probe thật (codex-quota-band) — **vừa**.
- Repo A5245/WatchSDK (Band 8 Pro): cầu là service `XMS_WEARABLE_SERVICE` của Mi Fitness; có hàm không tài liệu `getApkStatus`; package + chữ ký phải khớp; app Android phải xin quyền một lần thì Mi Fitness mới tạo mục quyền; Mi Fitness mặc định cấp hết. Nguồn: https://github.com/A5245/WatchSDK — **vừa**.

### 1.3 Mi Fitness là điều kiện bắt buộc + hành vi nền

- Phải cài Mi Fitness, đăng nhập, đồng hồ đã ghép và đang kết nối. Repo cộng đồng: *"小米运动健康断开即全链路不通"* (Mi Fitness ngắt là toàn bộ chuỗi chết). Nguồn: https://gitea.com/sisheng-mirror-4/VoidTerminal-Vela ; FAQ Vela — **cao/vừa**.
- Xiaomi tự nói Mi Fitness phải chạy nền liên tục, trên máy không phải Xiaomi có thể bị hệ thống kill sau thời gian rảnh dài; khuyên không clear app, bật quyền nền. Nguồn: https://www.mi.com/global/support/faq/details/KA-586179/ — **vừa** (trang FAQ chính thức, trích qua kết quả tìm kiếm).
- Trên điện thoại Xiaomi/HyperOS, Mi Fitness là app "nhà" nên thường được ưu đãi hơn app bên thứ ba; app của mình cần **Foreground Service** (type `connectedDevice` hoặc `location`), tắt battery optimization, bật Autostart. Có báo cáo HyperOS 3 hạn chế BLE nền với app bên thứ ba (Garmin Connect) — https://forums.garmin.com/apps-software/mobile-apps-web/f/garmin-connect-mobile-andriod/443699/persistent-bluetooth-le-disconnection-between-fenix-6-pro-and-xiaomi-x15t-pro-hyperos-3-0-336-0 — **thấp** (diễn đàn, và không trực tiếp áp dụng vì app mình chỉ IPC sang Mi Fitness, không giữ BLE).
- Codex-quota-band bỏ foreground service và ghi rõ không cam kết giao tin khi bị kill → minh chứng thực tế là **phải có FGS** cho realtime. — **vừa**.

### 1.4 Phát hành / approval

- Sideload rpk: Mi Fitness → Tôi → Giới thiệu → (bấm nhiều lần) Debug → Third-Party Apps → nhập package → cài rpk local. Nguồn: FAQ Vela; VoidTerminal README — **cao/vừa**.
- Không tìm thấy yêu cầu Xiaomi phê duyệt/whitelist để dùng interconnect cho app sideload. Phát hành chính thức lên store đồng hồ: liên hệ email Xiaomi ("business group"), duyệt khi launch (đặc biệt nếu xin chạy nền). Nguồn: FAQ Vela; https://iot.mi.com/vela/quickapp/en/guide/framework/other/background-running.html — **cao** (có quy trình liên hệ), **thấp** (về việc không bao giờ bị chặn ở bản Mi Fitness tương lai).
- Debug real-device qua IDE chính thức **chỉ hỗ trợ Watch S4**, cần firmware OTA từ Xiaomi. Nguồn: https://iot.mi.com/vela/quickapp/en/tools/devicedebug/start.html — **cao**. Với S5: dev bằng sideload + log (Mi Fitness → Debug → Pull firmware logs) — **vừa**.
- Review báo chí ghi S5 "cannot install apps" (góc nhìn người dùng phổ thông, không có store), không mâu thuẫn với sideload qua debug menu. Nguồn: https://www.notebookcheck.net/Xiaomi-Watch-S5-review-Great-battery-life-but-not-really-smart.1403355.0.html — **vừa**. Premise "S5 chạy rpk" thuộc track 01; tài liệu Vela đã liệt kê S5 trong bảng hỗ trợ của `fetch` và `bluetooth` → S5 là thiết bị Vela JS được hỗ trợ — **cao**.

---

## 2. BLE trực tiếp (không qua Mi Fitness)

### 2.1 QuickApp trên S5 làm BLE central — CÓ (chỉ S5)

- Module `system.bluetooth.ble`: `createScanner()` (scan), `createGattClientDevice(deviceId, addressType)`, `connect/disconnect`, `getServices`, `read/writeCharacteristicValue`, `setNotifyCharacteristicChanged` + `onBLECharacteristicChange`, `setBLEMtuSize` (22–512). **Không có advertising / GATT server** → đồng hồ chỉ là central. Nguồn: https://iot.mi.com/vela/quickapp/en/features/system/bluetooth.html — **cao**.
- Bảng hỗ trợ: **Xiaomi Watch S5: Supported**; S4, S3, Band 9/10, Redmi Watch 5/6…: Not supported. **"Background Running Restrictions: Prohibited."** Cùng nguồn — **cao**.
- Hệ quả kiến trúc: app Android tự làm **BLE peripheral** (`BluetoothLeAdvertiser` + `BluetoothGattServer`, custom service UUID), HUD trên đồng hồ scan/connect và subscribe notify. **Bỏ được Mi Fitness khỏi đường dữ liệu và không cần khớp chữ ký** — nhưng chỉ chạy khi HUD ở foreground, và chưa ai xác nhận S5 cho phép link GATT thứ hai tới chính điện thoại đang ghép qua Mi Fitness (BLE nhiều kết nối song song là chuẩn, nhưng firmware có thể chặn/lọc). — **thấp**, cần spike.
- Kênh này cũng loại bỏ phụ thuộc vào việc Mi Fitness bị kill, nhưng thêm rủi ro: Android peripheral mode trên HyperOS khi màn hình tắt (FGS `connectedDevice` thường giữ được) — **thấp/vừa**.

### 2.2 Điện thoại nói thẳng giao thức Xiaomi (thay Mi Fitness)

- Giao thức Xiaomi "protobuf" có **authentication + encryption**, cần **auth key lấy từ cloud Xiaomi/Mi Fitness** (server pairing). Gadgetbridge liệt kê **Watch S4 và S5** (thực nghiệm), có thể BLE hoặc Classic tùy firmware. Nguồn: https://gadgetbridge.org/basics/topics/xiaomi-protobuf/ ; https://gadgetbridge.org/basics/pairing/huami-xiaomi-server/ — **cao** (tài liệu dự án), **vừa** (độ ổn định với S5).
- Gadgetbridge **đã merge (2026-11-02) PR #7204**: gửi message tới QuickApp rpk qua RPK service (command type 20: 20/4 launch, 20/6 launched, **20/8 message**, 20/7 end), có Intent API `nodomain.freeyourgadget.gadgetbridge.command.SEND_WATCH_APP_MESSAGE` (extras `device`, `appId`, `payload` UTF-8). Test trên Band 10. **Chưa có chiều watch→phone** (sub-command 9 được nhắc trong review). Nguồn: https://codeberg.org/Freeyourgadget/Gadgetbridge/pulls/7204 ; https://gadgetbridge.org/internals/automations/intents/ — **cao**.
  - Không rõ watch có kiểm tra chữ ký khi message đến từ Gadgetbridge (có vẻ dùng sha của app từ danh sách rpk) — **thấp**.
  - Có báo cáo Band 10 Pro firmware global không trả lời service THIRDPARTY_APP (type 20) → hành vi phụ thuộc firmware — https://github.com/atc1441/MiBand10-BES2700iMP-BEST1503-Hacking/issues/3 — **thấp/vừa**.
- Đánh đổi: dùng Gadgetbridge thay thế Mi Fitness (thường không chạy song song được với Mi Fitness vì 1 kết nối/thiết bị — **vừa**), người dùng mất tính năng chính thức (sync sức khỏe, OTA). Hợp cho dân DIY, không hợp sản phẩm đại chúng.
- AstroBox (toolbox Tauri/Rust cho thiết bị Vela protobuf, cần auth key) cũng thay Mi Fitness, có plugin FetchBridge cho `system.fetch` của watch. Nguồn: https://github.com/if-kurt/xiaomi-watch-selfhost-monitor — **vừa/thấp**.

---

## 3. Tiền lệ mã nguồn mở / sản phẩm

| Dự án | Liên quan | Nguồn | Tin cậy |
|---|---|---|---|
| **Notify Maps** (trong Notify for Xiaomi ≥ v22) | Đúng bài toán: rpk "Notify Maps" trên đồng hồ, đọc chỉ đường Google Maps/Komoot/OsmAnd trên phone, relay qua Bluetooth, rung theo kiểu rẽ; liệt kê Watch S4, Band 9/10; phone tắt màn hình vẫn rung. Không công bố transport/tần suất. | https://mibandnotify.com/xiaomi-mi-band/notify-maps.php | vừa (trang marketing, chưa xác nhận S5) |
| Gadgetbridge Navigation | Google Maps → parse persistent notification; OsmAnd/CoMaps qua API. Với Xiaomi protobuf: gửi **dạng notification có icon**, không có màn nav native. Google Maps "Live Updates" làm hỏng việc đọc (issue #5420) → phải tắt. | https://gadgetbridge.org/basics/integrations/navigation/ ; https://www.gadgetbridge.org/basics/features/navigation/ | cao |
| Gadgetbridge RpkService (#5864, merged 2026-03) + #7204 | Cài/quản lý rpk và gửi message tới rpk | https://codeberg.org/Freeyourgadget/Gadgetbridge/pulls/5864 | cao |
| A5245/WatchSDK | Demo phone↔rpk qua XMS service của Mi Fitness (Band 8 Pro) | https://github.com/A5245/WatchSDK | vừa |
| XMS_Wearable_SDK_CleanRoom | SDK phía phone mã mở (MIT) | https://github.com/OrPudding/XMS_Wearable_SDK_CleanRoom | vừa |
| VoidTerminal-Vela / MoshiNovel-Vela | rpk + Android companion qua interconnect, JSON action, FGS | https://gitea.com/sisheng-mirror-4/VoidTerminal-Vela | vừa |
| MiBandNavigator, StarPath | NotificationListener Google Maps → thông báo qua Zepp/Gadgetbridge (Mi Band cũ, Amazfit) | https://github.com/satvikpandurangi/MiBandNavigator ; https://apt.izzysoft.de/ftp/repo/fdroid/index/apk/app.starpath?repo=main | vừa |
| Bangle.js / PineTime | Nhận nav từ Gadgetbridge (tiền lệ UX, khác nền tảng) | https://gadgetbridge.org/blog/release-0_75_0/ | cao |

Notify Maps chứng minh **nav HUD dạng rpk trên đồng hồ Xiaomi Vela nhận dữ liệu realtime từ phone là khả thi về thương mại** — **vừa**.

---

## 4. Con số thực tế (băng thông, độ trễ, độ bền)

- **Nhu cầu**: 1 msg/s, JSON kiểu `{"a":12,"d":180,"s":"Nguyễn Văn Linh","e":"12:45","t":1732340000}` ≈ 60–120 byte (UTF-8 tiếng Việt 2–3 byte/ký tự). Tức < 1 kbit/s — nhỏ hơn rất nhiều so với BLE (thực dụng hàng chục kB/s). — **cao** (tính toán).
- **Giới hạn payload interconnect / MessageApi**: **không có tài liệu**. Ví dụ SDK dùng 1024 byte; mẫu ảnh chính thức chia Base64 fragment. Đề xuất giữ mỗi message ≤ 512 byte (an toàn) — **thấp**, cần đo.
- **Latency**: không có số liệu công khai. Chuỗi phone app → Binder → Mi Fitness → BLE/SPP (mã hóa protobuf) → firmware → JS runtime. Ước lượng 100–500 ms/msg, đủ cho HUD (khoảng cách tự nội suy trên đồng hồ cũng được) — **thấp**, cần spike đo RTT bằng echo.
- **Rate**: không có giới hạn tài liệu hóa; nên gửi theo thay đổi (delta) + heartbeat 1 s, coalesce (chỉ giữ message mới nhất), không xếp hàng tồn đọng — **thấp** (thiết kế).
- **Reconnect**: watch có `onopen({isReconnected})`, phone có `ServiceApi` + `subscribe(ITEM_CONNECTION)` → làm được state machine reconnect hai phía, gửi lại full snapshot khi `onopen` — **cao** (API có sẵn).
- **Phone tắt màn hình**: phụ thuộc FGS của app mình + Mi Fitness không bị kill. Notify Maps nói phone tắt màn vẫn chạy — **vừa**. HyperOS cần whitelist pin/Autostart cho cả hai app — **vừa**.
- **Đồng hồ**: interconnect không nằm trong danh sách interface được chạy nền (chỉ audio, request, geolocation) → HUD phải ở foreground (giữ màn hình sáng là việc của track display) — **cao** về danh sách, **thấp** về việc interconnect có tiếp tục nhận khi màn hình đồng hồ tắt.

---

## 5. Fallback nếu kênh bên thứ ba hỏng

1. **BLE GATT trực tiếp (S5)**: phone làm peripheral, HUD làm central — mục 2.1. Fallback mạnh nhất, không cần Mi Fitness/chữ ký, chỉ foreground. — **thấp** (chưa thử).
2. **`NotifyApi.sendNotify` hoặc notification thường mirror qua Mi Fitness**: hiển thị chữ + rung, nhưng là thông báo hệ thống (không HUD, mỗi lần cập nhật là một thông báo/rung; spam). Hợp cho cảnh báo rẽ, không hợp đếm ngược 1 Hz. — **cao** (API có), **vừa** (UX kém).
3. **Gadgetbridge** (thay Mi Fitness): nav dạng notification có icon, hoặc Intent `SEND_WATCH_APP_MESSAGE` tới HUD rpk. — **cao/vừa**.
4. **Lạm dụng media control**: metadata MediaSession (title/artist) hiển thị trên màn điều khiển nhạc của đồng hồ (Gadgetbridge mô tả Xiaomi hỗ trợ track info) → nhét tên đường/khoảng cách vào title. Hack, tần suất cập nhật và UX không kiểm soát được. — **thấp**.
5. **`system.fetch` từ đồng hồ** (S5 hỗ trợ) poll một endpoint (cloud hoặc HTTP server trên phone qua proxy của Mi Fitness/AstroBox): không rõ fetch đi qua đường nào, latency cao, tốn pin; chỉ hợp làm debug. Nguồn: https://iot.mi.com/vela/quickapp/en/features/network/fetch.html — **thấp**.
6. **Wi-Fi trực tiếp**: không thấy API Wi-Fi/WebSocket cho QuickApp — **vừa** (theo danh sách feature và README VoidTerminal).

---

## Kết luận ngắn

- **Khả thi, có kênh chính thức**: rpk dùng `@system.interconnect`; APK dùng XMS Wearable SDK 1.4 (`MessageApi.sendMessage(nodeId, byte[])`, hai chiều, có `launchWearApp` để mở HUD từ phone). Điều kiện: cùng package name, cùng certificate, Mi Fitness cài và đang kết nối. Không thấy cần Xiaomi duyệt khi sideload. (**cao**)
- Băng thông không phải vấn đề với ~100 B/s; **độ bền nền** mới là vấn đề (FGS + whitelist pin cho cả app mình và Mi Fitness). (**vừa**)
- **Watch S5 có lợi thế riêng**: QuickApp làm BLE central được (S4 trở xuống không) → kênh dự phòng không phụ thuộc Mi Fitness, nhưng chỉ chạy foreground. (**cao** về API, **thấp** về thực tế)
- Có tiền lệ thương mại (Notify Maps) và mã mở (Gadgetbridge #7204, WatchSDK, clean-room SDK). (**vừa/cao**)
- Đề xuất: kênh chính = interconnect/XMS; spike song song kênh BLE GATT S5 làm plan B.

## Rủi ro chặn

1. **SDK phía phone không phát hành công khai**: phải lấy AAR từ demo zip Xiaomi (bản quyền không rõ) hoặc dùng clean-room MIT (có thể vỡ khi Mi Fitness đổi Binder contract). SDK 1.4 nhắm Android R — có thể lỗi trên Android 14+.
2. **Mi Fitness thay đổi/khóa** service XMS hoặc cơ chế cấp quyền tự động ở bản cập nhật → toàn bộ kênh chết, ngoài tầm kiểm soát.
3. **Mi Fitness hoặc app mình bị HyperOS kill** khi tắt màn hình trên đường → HUD đứng hình (nguy hiểm khi lái nếu không có chỉ báo "mất kết nối/dữ liệu cũ").
4. **Chưa xác minh trên Watch S5 thật**: interconnect không có bảng thiết bị; debug IDE chỉ hỗ trợ S4; firmware global có thể khác (tiền lệ Band 10 Pro không trả lời type 20).
5. **HUD phải foreground trên đồng hồ** (interconnect và BLE không được liệt kê là chạy nền) — phụ thuộc khả năng giữ màn hình sáng (track display).

## Câu hỏi còn mở

1. Giới hạn kích thước một message interconnect/`sendMessage` và độ trễ thực tế (cần spike echo RTT, 1 Hz trong 30 phút, phone tắt màn hình).
2. Demo zip chính thức `interconnect_dev_test_demo.zip` có kèm AAR không, và giấy phép sử dụng? (cần sự đồng ý của người dùng để tải và kiểm tra.)
3. S5 có cho QuickApp mở link GATT tới chính điện thoại đang ghép qua Mi Fitness không? Android peripheral có sống khi màn hình tắt trên HyperOS không?
4. Interconnect trên đồng hồ có tiếp tục nhận `onmessage` khi màn hình đồng hồ tắt/AOD không?
5. Gửi message qua Gadgetbridge (#7204) có cần khớp chữ ký không; Gadgetbridge có chạy ổn định với S5 không?
6. Notify Maps dùng transport gì (XMS qua Mi Fitness hay kết nối riêng)? Nếu dùng XMS thì đó là bằng chứng mạnh rằng kênh ổn định ở quy mô người dùng.
7. Người dùng chấp nhận yêu cầu "Mi Fitness phải chạy" hay muốn đi đường Gadgetbridge (DIY, mất tính năng chính thức)?
