# 01 — Nền tảng phía đồng hồ: Xiaomi Watch S5 và Xiaomi Vela JS (QuickApp)

> Track: WATCH side platform. Ngày nghiên cứu: 2026-11-23. Nguồn chính: tài liệu chính thức `iot.mi.com/vela/quickapp` (EN + ZH), review báo chí, repo GitHub cộng đồng. Một số nguồn (bandbbs.cn, mi.com specs, wareable) trả về HTTP 403 nên chỉ dùng đoạn trích từ kết quả tìm kiếm; đã ghi chú khi điều đó xảy ra.
>
> Quy ước độ tin cậy: **cao** = tài liệu chính thức Xiaomi hoặc nhiều nguồn độc lập trùng khớp; **vừa** = báo chí/review hoặc một repo cộng đồng có mô tả cụ thể; **thấp** = diễn đàn, đoạn trích search, suy luận.

---

## 1. Kiểm chứng premise: Watch S5 chạy gì?

### 1.1 Hệ điều hành

| Khẳng định | Kết quả | Nguồn | Tin cậy |
|---|---|---|---|
| Watch S5 **không** chạy Wear OS | Đúng. Xiaomi tách 2 dòng: **Xiaomi Watch 5** (không có chữ S) chạy Wear OS; **Watch S5** chạy HyperOS bản "watch" (RTOS) | https://www.wareable.com/smartwatches/xiaomi-watch-s5-debuts-in-china-with-21-day-battery-and-slimmer-design (đoạn trích search), https://www.wareable.com/smartwatches/xiaomi-watch-5-review | cao |
| S5 46mm ra mắt kèm "HyperOS 3" (review Notebookcheck ghi HyperOS 3.2), pin tới 21 ngày, AMOLED 1.48" 480x480, BT 5.4 | Đúng | https://www.notebookcheck.net/Xiaomi-Watch-S5-review-Great-battery-life-but-not-really-smart.1403355.0.html, https://gadgets.beebom.com/news/xiaomi-watch-s5-launched-globally-key-specifications-features-pricing | cao |
| Có thêm **Watch S5 41mm** (ra mắt TQ 2026-09-23) với "表端澎湃 OS 4" | Có, nghĩa là dòng S5 có ít nhất 2 kích thước và 2 thế hệ firmware | https://ithome.com/1/006/432.htm | vừa |
| Nhân là NuttX + Xiaomi Vela | Không có nguồn chính thức nào nói thẳng "Watch S5 = NuttX". Tuy nhiên tài liệu **Xiaomi Vela JS** chính thức liệt kê **Xiaomi Watch S5** trong bảng thiết bị (multi-screen) và trong danh sách "supported devices" của nhiều API (vibrator, event, geolocation). Xiaomi Vela được xây trên NuttX (open-vela). Nên premise "S5 = HyperOS trên nền Vela" là **đúng về thực chất** | https://iot.mi.com/vela/quickapp/en/guide/multi-screens/, https://iot.mi.com/vela/quickapp/en/features/system/vibrator.html, https://gitee.com/open-vela/packages_apps | cao (S5 chạy Vela JS runtime), vừa (chi tiết NuttX) |

**Kết luận premise:** đúng phần lớn. Không cài được `.apk`, không có Google Maps SDK trên đồng hồ. Ứng dụng bên thứ ba duy nhất là **Xiaomi Vela JS app** (`.rpk`, cú pháp `.ux` = template + style + script, kiểu QuickApp/hap). Điều **chưa đúng / thiếu** trong premise: "chạy được `.rpk` của bên thứ ba" **không** đồng nghĩa với "bên thứ ba cài và phát hành được dễ dàng" — xem mục 2.

### 1.2 Thông số màn hình (theo tài liệu Vela)

| Model | Hình dạng | Kích thước | Độ phân giải | PPI | Width (DP) |
|---|---|---|---|---|---|
| Xiaomi Watch S5 (46mm) | Tròn | 1.485" | 480x480 | 323 | 240 |
| Xiaomi Watch S4 | Tròn | 1.43" | 466x466 | 326 | 233 |

Nguồn: https://iot.mi.com/vela/quickapp/en/guide/multi-screens/ (cao). Review đo độ sáng tối đa thủ công ~602 nit (hãng công bố 2500 nit peak), 60 Hz: Notebookcheck (vừa). **S5 41mm: chưa tìm được độ phân giải** (thấp/không rõ).

FAQ chính thức: framework mặc định 480x480, khai báo `designWidth` trong `manifest.json` để tự scale: https://iot.mi.com/vela/quickapp/zh/guide/other/faq.html (cao). `device.getInfo()` trả `screenShape` (`rect`/`circle`/`pill-shaped`), `screenWidth/Height`, `screenDensity`: https://iot.mi.com/vela/quickapp/en/features/basic/device.html (cao).

### 1.3 Thiết bị hỗ trợ Vela JS

Bảng thiết bị trong tài liệu: Watch S1 Pro, H1, S3, S4 sport, S4, **S5**, REDMI Watch 5, Band 8 Pro, Band 9, Band 9 Pro, Band 10 (cao, link multi-screens ở trên). REDMI Watch 6 xuất hiện trong danh sách hỗ trợ API (vibrator/event/geolocation). Lưu ý cộng đồng: "Xiaomi Watch S5 ≠ REDMI Watch 5 ≠ Xiaomi Watch 5" (https://wiki.bandbbs.cn/Guides/watchface_custom_tool/watchface_custom_tool-install.html, vừa).

### 1.4 Khác biệt vùng (CN vs Global)

- Review bản global (Notebookcheck): "third-party apps cannot be installed", app đồng hành là **Mi Fitness** (`com.xiaomi.wearable`). (vừa) — nghĩa là với người dùng thường, bản global **không có kênh cài app bên thứ ba chính thức**.
- Bản TQ: app đồng hành 小米运动健康 có "应用市场" cho快应用 trên một số đời máy; wiki bandbbs nói快应用 có thể tìm trong 应用市场 và **S5 không giới hạn số快应用** (các máy khác giới hạn 20). (thấp–vừa, bandbbs 403 khi fetch thread, chỉ đọc được wiki)
- Một thread bandbbs (đoạn trích search) nói S5 có thay đổi "JS 底层架构" (jsc + protobuf) khiến một số快应用 cũ cho đồng hồ tròn lỗi. (thấp)

---

## 2. Đường phát triển (developer path)

### 2.1 Tài liệu và công cụ chính thức

| Hạng mục | Chi tiết | Nguồn | Tin cậy |
|---|---|---|---|
| Docs | https://iot.mi.com/vela/quickapp/en/guide/ (EN) và `/zh/` (ZH, thường đầy đủ hơn) | — | cao |
| IDE | **AIoT-IDE** (dựa trên VS Code), macOS 14+, Windows 10+, Ubuntu 20.04+. Có simulator (tải Vela image khi tạo instance) và multi-screen UI simulator | https://iot.mi.com/vela/quickapp/en/guide/start/use-ide.html | cao |
| CLI | `aiot-toolkit` (npm devDependency, lệnh `aiot start --watch`, build debug/release). Docs chính thức có trang "Build Parameters" `guide/start/toolkit-params.html`; repo cộng đồng dùng `npm run build` / `npm run release` | https://github.com/hyperpostulate/rw4-poc | vừa |
| Output | Package → `dist/*.debug.rpk`; Publish → `*.release.rpk` | use-ide.html | cao |
| Ký | `sign/private.pem` + `sign/certificate.pem`, tạo bằng `openssl req -newkey rsa:2048 -nodes -keyout private.pem -x509 -days 3650 -out certificate.pem` hoặc IDE tự sinh. Debug rpk không cần ký | use-ide.html, rw4-poc | cao |
| Ký khi dùng interconnect | **Bắt buộc** `package` của rpk = package Android app, và rpk ký bằng **cùng chứng chỉ** với APK (chuyển `.jks` → `.p12` → `.pem`). Sai chữ ký → "通信会被拒绝" | https://iot.mi.com/vela/quickapp/zh/features/network/interconnect.html, FAQ zh | cao |
| Không đổi cert | "如果证书改变，可能无法上架" | FAQ zh | cao |
| Mâu thuẫn tài liệu | Trang manifest nói `package` "must differ from the native app's package name", trong khi interconnect yêu cầu trùng package Android. Có lẽ "native app" = app hệ thống trên đồng hồ; cần xác minh bằng spike | https://iot.mi.com/vela/quickapp/en/guide/framework/manifest.html | thấp (diễn giải) |

### 2.2 Debug trên đồng hồ thật — **điểm nghẽn lớn**

- Real-device debugging qua AIoT-IDE: "currently only supported on `Xiaomi Watch S4`", phải flash OTA đặc biệt, và "**only open to specific partners**" (liên hệ nhân viên Xiaomi để lấy "小米Vela设备真机调试全流程指南"). **Không nhắc S5.** https://iot.mi.com/vela/quickapp/en/tools/devicedebug/start.html, bản ZH https://iot.mi.com/vela/quickapp/zh/tools/devicedebug/start.html (cao).
- Cài rpk chính thức: 小米运动健康/Mi Fitness → 我的 → 关于 → **Debug** → 第三方应用 → Install third app. Nhưng bản Mi Fitness có menu Debug này "目前是通过商务拉群对接的方式提供" (chỉ qua kênh business). Log thiết bị: Debug → 拉取固件日志 → `/sdcard/Android/data/com.mi.health/files/log`. https://iot.mi.com/vela/quickapp/zh/guide/other/faq.html (cao).
- Simulator ↔ điện thoại qua interconnect cần adapter Bluetooth ngoài, "配置较复杂" (FAQ, cao) → **gần như không test được luồng phone→watch trên simulator**.

### 2.3 Phát hành

- "Currently, you need to contact the staff member zhangyuanpu@xiaomi.com for collaborative release." Không có self-service store, không phân biệt cá nhân/doanh nghiệp. https://iot.mi.com/vela/quickapp/en/guide/publish/ (cao).
- Acceptance criteria: FMP trang chủ ≤ 2000 ms; app xin background running bị review "hợp lý". https://iot.mi.com/vela/quickapp/en/guide/publish/acceptance-criteria.html, background-running.html (cao).
- → Cá nhân **không có đường phát hành công khai thực tế**; với dự án cá nhân, mục tiêu khả thi là **sideload cho chính đồng hồ của mình**.

### 2.4 Sideload không chính thức (cộng đồng)

| Công cụ | Cách | Ghi chú | Nguồn | Tin cậy |
|---|---|---|---|---|
| Notify for Xiaomi (Android) | Device → Third party app → chọn `.rpk`, qua BLE; tự lấy auth key qua tài khoản Xiaomi | Chỉ một app giữ kết nối BLE tại một thời điểm → phải force-stop/thu quyền Mi Fitness. Có AAR riêng `xms-wearable-lib_1.4_release.aar` để interconnect với app Android khi Notify là app đồng hành | https://github.com/hyperpostulate/rw4-poc, http://www.mibandnotify.com/xiaomi-mi-band/notify-xms-app-instructions.php | vừa |
| AstroBox (desktop/Android, Tauri) | Cài rpk bằng auth key; plugin "FetchBridge" proxy `system.fetch` qua điện thoại | Ví dụ thực tế target "Watch S4, Redmi Watch 6"; S5 chưa xác nhận | https://github.com/if-kurt/xiaomi-watch-selfhost-monitor | vừa (thấp cho S5) |
| 表盘自定义工具 (bandbbs) | "蓝牙一键安装" bằng AuthKey đọc từ 小米运动健康 | Liệt kê S5 trong nhóm 小米手表; S5 không giới hạn số快应用 | https://wiki.bandbbs.cn/Guides/watchface_custom_tool/watchface_custom_tool-install.html | vừa |
| Gadgetbridge | Flash rpk qua Xiaomi protobuf (thử nghiệm) | Hỗ trợ từng model khác nhau | https://gadgetbridge.org/basics/topics/xiaomi-protobuf/ | vừa |
| MiWearBridge (LSPosed, cần root) | Hook Mi Fitness để cài rpk **và** mở interconnect cho app cùng chữ ký **không cần Xiaomi duyệt** | 1 sao, research/personal | https://github.com/batareya16/MiWearBridge | thấp–vừa |
| BandBox (iOS) | BLE `fe95`, AuthKey | Chưa liệt kê S5 | https://github.com/Lin-0226-github/BandBox | thấp |

Đã có快应用 cộng đồng "For Xiaomi Watch S5" (扫雷, 操盘手 S3/S4/S4Sport/S5) trên bandbbs → **cài rpk lên S5 bằng đường cộng đồng là khả thi** (vừa; chỉ thấy qua đoạn trích search, thread bị 403).

AuthKey = khóa điều khiển BLE toàn quyền; không chia sẻ (BandBox README, bandbbs wiki).

---

## 3. Khả năng runtime cho HUD

### 3.1 UI component (cao — https://iot.mi.com/vela/quickapp/en/components/general/, /zh/components/)

- Container: `div`, `list`, `list-item`, `scroll`, `stack`, `swiper`.
- Basic: `text`, `span`, `a`, `image`, `image-animator`, `progress`, `marquee`, `chart`, `qrcode`, `barcode`.
- Form: `input`, `picker`, `switch`, `slider`.
- **Không có `canvas`, không có `svg`** trong danh sách component (cao). → không vẽ vector tự do.
- Layout: chỉ flex (`display: flex|none`), `position: relative|absolute`, offset chỉ px; `border-style` chỉ `solid`; `box-shadow` từ API level 3; `background-repeat` không hỗ trợ. https://iot.mi.com/vela/quickapp/en/components/general/style.html (cao)
- `image`: chỉ **PNG, JPG**; `object-fit` contain/cover/none/scale-down. https://iot.mi.com/vela/quickapp/zh/components/basic/image.html (cao)
- `text`: `font-size` mặc định 30px; `font-weight` chỉ normal/bold; `lines` + `text-overflow: ellipsis`. Không có tài liệu về `font-family` / `@font-face`. https://iot.mi.com/vela/quickapp/en/components/basic/text.html (cao)
- Cảnh báo cộng đồng: trên **Redmi Watch 4**, `<text>` "paint no readable glyphs" và `@font-face` vô hiệu → phải render chữ bằng PNG atlas. (https://github.com/hyperpostulate/rw4-poc, vừa cho RW4, **thấp khi suy ra S5** — S5 có ebook/stock app dùng text nên nhiều khả năng text hoạt động; vẫn cần spike, đặc biệt **dấu tiếng Việt** của tên đường).

### 3.2 Animation và mũi tên xoay

- `transform`: `translate[X|Y]`, `scale[X|Y]`, **`rotate(<deg>)`**; `transform-origin` (%, px). `@keyframes` hỗ trợ `transform`, `opacity`, `width/height`, `background-color/position` (bắt buộc khai 0% và 100%). `transition` **không** hỗ trợ `transform`. https://iot.mi.com/vela/quickapp/en/components/general/animation-style.html (cao)
- Docs tổng quan quảng bá "30+ 插值和物理动效", mục tiêu 60 fps, GPU/CPU acceleration (cao về lời tuyên bố, chưa đo).
- **Cách vẽ mũi tên rẽ khuyến nghị:** bộ PNG đã render sẵn cho các maneuver rời rạc (trái, phải, chếch, quay đầu, vòng xuyến exit N, đi thẳng…), độ tương phản cao, nền đen (AMOLED). Nếu cần góc liên tục (ví dụ hướng vòng xuyến/la bàn), dùng `<image>` + style binding `transform: rotate({{deg}}deg)` (cập nhật nhảy bậc, không có transition mượt; có thể dùng keyframes nếu cần). Không có canvas nên không vẽ polyline đường đi tự do — nếu muốn "lane/geometry" phải tạo ảnh phía điện thoại rồi gửi (rủi ro băng thông, xem mục 4). (suy luận, vừa)

### 3.3 Giữ màn hình sáng / độ sáng

`@system.brightness`: `getValue/setValue` (0–255), `getMode/setMode` (0 manual, 1 auto), **`setKeepScreenOn({keepScreenOn: Boolean})`**. https://iot.mi.com/vela/quickapp/en/features/system/brightness.html (cao). Trang không ghi danh sách thiết bị hỗ trợ, cũng không nói hành vi khi hạ cổ tay/AOD (không rõ). RW4 PoC xác nhận `@system.brightness` hoạt động (vừa).

### 3.4 Rung

`@system.vibrator` (https://iot.mi.com/vela/quickapp/en/features/system/vibrator.html, cao):
- `vibrate({mode: 'long'|'short'})` — hầu hết thiết bị, gồm S5.
- **`start({duration, interval, count})`** → trả task `id`; lỗi 205 (task đã tồn tại), 202 (tham số). **Chỉ Watch S5.**
- `stop(id)` — **chỉ S5**.
- `getSystemDefaultMode()` → 0 tắt / 1 chuẩn / 2 mạnh — **chỉ S5**.
- Không có pattern tùy ý (waveform), chỉ lặp đều. Với HUD: mã hóa cảnh báo bằng `count`/`duration` (vd. 2 xung ngắn = rẽ sắp tới 200 m, 3 xung dài = rẽ ngay). Rung mặc định có thể bị tắt bởi user → nên đọc `getSystemDefaultMode()`.

### 3.5 Lifecycle và chạy nền — **rủi ro lớn**

- App chỉ tiếp tục chạy khi xuống nền nếu khai `config.background.features` **và** đang chạy ít nhất một API nền; hiện chỉ hỗ trợ `system.audio`, `system.request` (upload/download), `system.geolocation`. Ngoài ra "the app stops". https://iot.mi.com/vela/quickapp/en/guide/framework/other/background-running.html (cao)
- `system.interconnect` **không** nằm trong danh sách background → khi app rời foreground, khả năng cao mất kênh nhận dữ liệu (suy luận, vừa).
- Precautions: `onShow` "fires again when the screen turns back on" → tắt màn hình kích hoạt chu kỳ hide/show. https://iot.mi.com/vela/quickapp/en/guide/other/tips.html (cao). Chưa rõ tắt màn hình = "background" (app bị dừng) hay chỉ `onHide`.
- Không có system event screen on/off / wrist raise: `@system.event` (API level 4, có S5) chỉ có BATTERY_CHANGED, CHARGING, DISCHARGING + event tự publish. https://iot.mi.com/vela/quickapp/en/features/system/event.html (cao)
- Hệ quả thiết kế: HUD phải là app **foreground + `setKeepScreenOn(true)`**; điện thoại cần `launchWearApp` (phía phone SDK, xem 4.2) để mở lại app khi bị đẩy ra. Hack khả dĩ: khai `system.geolocation` background + `subscribe` để giữ app sống (có lợi phụ: GPS đồng hồ làm dự phòng khoảng cách) — đổi lại tốn pin và có thể bị review từ chối (chỉ ảnh hưởng nếu phát hành). (suy luận, thấp–vừa; **cần spike**)

### 3.6 Bộ nhớ, gói, pin

- Tài liệu **không công bố** giới hạn RAM/JS heap/kích thước rpk. Trang Memory Optimization chỉ đưa ví dụ QQ Music (max memory 4.8M → 1.87M sau tối ưu, không đơn vị) và gợi ý: giảm observer, `global.runGC()`, clear timer ở `onDestroy`, ảnh độ phân giải thấp, ít trang. https://iot.mi.com/vela/quickapp/en/guide/best-practice/memory.html (cao về nội dung; số liệu không đủ)
- List ≤ 20 item/trang (tips, cao). Nhiều `<image>` cùng lúc làm chậm (rw4-poc, vừa).
- Cộng đồng: cài快应用 cần ~87 MB trống, tối đa ~20 app trên RW4; S5 không giới hạn số app (vừa/thấp).
- `device.getTotalStorage/getAvailableStorage` có; không có API đọc RAM (cao).
- Pin: S5 15 ngày không AOD, 10 ngày có AOD (Notebookcheck đo, vừa). Không có hướng dẫn chính thức về pin cho快应用 ngoài cảnh báo background "consumes significant resources". Ước lượng: giữ màn AMOLED sáng liên tục là chi phí chính → UI nền đen thuần, ít pixel sáng, độ sáng có điều chỉnh (suy luận).

### 3.7 API khác liên quan

- `@system.geolocation`: `getLocation`, `subscribe` (lon/lat/alt/speed m/s/accuracy), quyền `hapjs.permission.LOCATION`; **S5 được liệt kê hỗ trợ** (RW4 không). https://iot.mi.com/vela/quickapp/en/features/system/geolocation.html (cao). Không nói tần suất cập nhật / hệ tọa độ.
- `@system.fetch`: đồng hồ không có internet trực tiếp; cộng đồng proxy qua điện thoại (AstroBox FetchBridge) — "Wi-Fi isn't exposed to quick-apps" (selfhost-monitor, vừa). Với Mi Fitness chính thức, fetch đi qua điện thoại (không xác minh).
- `@system.sensor`, `@system.bluetooth` có trong danh sách API (chưa đọc chi tiết; RW4 báo fail — thấp cho S5).

---

## 4. Dữ liệu vào đồng hồ (watch-side)

### 4.1 `@system.interconnect` (kênh chính thức phone ↔ watch)

Nguồn: https://iot.mi.com/vela/quickapp/en/features/network/interconnect.html, bản ZH (cao).

```js
// manifest.json features: { "name": "system.interconnect" }
import interconnect from '@system.interconnect'
const conn = interconnect.instance()          // singleton; kết nối tự động, app không tự connect/close
conn.getReadyState({ success: ({status}) => {/* 1 connected, 2 disconnected */}, fail: (data, code) => {} })
conn.diagnosis({ timeout: 10000, success: ({status}) => {/* 0 OK, 204 timeout, 1001 phone app chưa cài, 1000 khác */} })
conn.send({ data: {/* Object */}, success() {}, fail: (data, code) => {/* 204 timeout, 1006 disconnected */} })
conn.onmessage = ({ data }) => {/* data: String từ app Android */}
conn.onopen    = ({ isReconnected }) => {}
conn.onclose   = ({ code, data }) => {}
conn.onerror   = ({ code, data }) => {/* 1000 unknown, 1001 phone app not installed, 1006 disconnected */}
```

- Điều kiện: cùng `package` + cùng chữ ký với APK Android.
- FAQ: vào trang ngay thường thấy DISCONNECTED → phải poll trạng thái (cao).
- **Không công bố** giới hạn kích thước message, tần suất, độ trễ, danh sách thiết bị (cao — "not stated").
- Watch nhận `String` (phía phone gửi `byte[]`) → giao thức nên là JSON nhỏ hoặc chuỗi nén gọn.

### 4.2 Phía điện thoại (tóm tắt, để agent transport đào sâu)

Tài liệu "小米穿戴第三方APP能力开放接口文档 v1.4" (PDF đính kèm trang interconnect: https://vela-docs.cnbj1.mi-fds.com/vela-docs/files/%E5%B0%8F%E7%B1%B3%E7%A9%BF%E6%88%B4%E7%AC%AC%E4%B8%89%E6%96%B9APP%E8%83%BD%E5%8A%9B%E5%BC%80%E6%94%BE%E6%8E%A5%E5%8F%A3%E6%96%87%E6%A1%A3_1.4.pdf). Đã trích phần mã (phần chữ Hán bị mã hóa font, không đọc được) (cao cho tên API):
- `Wearable.getNodeApi(ctx)`: `getConnectedNodes()`, `query(nodeId, DataItem.ITEM_CONNECTION)`, `isWearAppInstalled(nodeId)`, **`launchWearApp(nodeId, uri)`**, `subscribe/unsubscribe(nodeId, DataItem.ITEM_CONNECTION, listener)`.
- `Wearable.getAuthApi(ctx)`: `checkPermission(s)`, `requestPermission(nodeId, Permission.DEVICE_MANAGER, Permission.NOTIFY)`.
- `Wearable.getMessageApi(ctx)`: **`sendMessage(nodeId, byte[])`** (ví dụ dùng `new byte[1024]`), `addListener/removeListener(nodeId, OnMessageReceivedListener)`.
- `Wearable.getNotifyApi(ctx).sendNotify(nodeId, title, message)`; `Wearable.getServiceApi(ctx).registerServiceConnectionListener(...)` (kết nối tới service của app đồng hành Mi Fitness).
- **Rào cản:** MiWearBridge README: interconnect "officially requires the third-party app to be authorised by Xiaomi" ("bond"); nếu không, đồng hồ "silently refuses to route the app's messages" dù package/chữ ký khớp. https://github.com/batareya16/MiWearBridge (vừa — một nguồn cộng đồng, khớp với việc docs yêu cầu liên hệ business).
- Lối thay thế cộng đồng: Notify for Xiaomi làm app đồng hành + AAR riêng `xms-wearable-lib_1.4_release.aar` (http://www.mibandnotify.com/xiaomi-mi-band/notify-xms-app-instructions.php, vừa); hoặc AstroBox FetchBridge (watch `fetch` → phone HTTP) (vừa).

---

## Kết luận ngắn

1. **Premise đúng về kỹ thuật:** Watch S5 chạy HyperOS bản RTOS trên Xiaomi Vela, không phải Wear OS; app bên thứ ba = Vela JS `.rpk`, và S5 nằm trong danh sách thiết bị của tài liệu chính thức (cao).
2. **Premise sai về khả năng tiếp cận:** đường chính thức (debug thật, menu Debug của Mi Fitness, interconnect "bond", phát hành) đều **chỉ qua đối tác/business của Xiaomi**; real-device debug chính thức chỉ có S4. Bản global S5 "cannot install third-party apps" với người dùng thường (cao/vừa).
3. Với dự án cá nhân, đường khả thi là **sideload cộng đồng** (Notify for Xiaomi / AstroBox / bandbbs tool) và kênh dữ liệu **không chính thức** (Notify AAR, AstroBox FetchBridge, hoặc root + MiWearBridge) — khả thi nhưng mong manh.
4. Runtime **đủ cho HUD tối giản**: flex layout, `image` PNG + `transform: rotate`, `text`, `setKeepScreenOn`, và rung mẫu lặp `vibrator.start` (độc quyền S5). Không có canvas/SVG; chạy nền rất hạn chế → HUD phải là app foreground, màn hình luôn sáng.

## Rủi ro chặn

- **R1 — Kênh phone→watch cho app không được Xiaomi "bond":** interconnect chính thức có thể bị từ chối âm thầm. Nếu Notify/AstroBox/MiWearBridge không chạy trên S5 (firmware HyperOS 3/4, kiến trúc JS mới), dự án không có kênh dữ liệu. *Spike đầu tiên bắt buộc.*
- **R2 — Sideload trên S5 chưa được xác minh trực tiếp** bởi nguồn đọc được (chỉ có đoạn trích bandbbs và bảng hỗ trợ của tool); thay Mi Fitness bằng Notify/AstroBox làm mất tính năng app chính thức (một kết nối BLE tại một thời điểm).
- **R3 — Lifecycle:** tắt màn hình/hạ cổ tay có thể đưa app xuống nền và dừng nó (interconnect không phải background feature). Cần xác nhận `setKeepScreenOn` thực sự giữ app foreground suốt chuyến đi, và chi phí pin.
- **R4 — Render chữ tiếng Việt:** chưa có bằng chứng font hệ thống S5 có đủ dấu tiếng Việt cho `<text>`; trên RW4 `<text>` thậm chí không vẽ glyph. Nếu thiếu, tên đường phải gửi dưới dạng ảnh hoặc atlas glyph → tăng băng thông/RAM.
- **R5 — Nâng cấp firmware/Mi Fitness** có thể phá đường cộng đồng bất kỳ lúc nào (AuthKey, protobuf, hook).

## Câu hỏi còn mở

1. Đồng hồ của người dùng là S5 **46mm hay 41mm**, bản **TQ hay global**, firmware HyperOS mấy? (ảnh hưởng độ phân giải 41mm chưa rõ, kênh cài app, AOD).
2. Người dùng chấp nhận **thay Mi Fitness bằng Notify for Xiaomi/AstroBox** (mất một số tính năng chính thức) hoặc **root điện thoại** (MiWearBridge) không? Hay muốn thử xin Xiaomi hợp tác (email business)?
3. `setKeepScreenOn(true)` có giữ app sống khi hạ cổ tay / AOD trên S5 không? Tắt màn hình có kích hoạt "stop" của background-running không? (spike thực tế)
4. Font hệ thống S5 có đủ glyph tiếng Việt cho `<text>` không?
5. Giới hạn kích thước/tần suất message của interconnect (và của kênh Notify/FetchBridge) trên S5 — chưa có số liệu chính thức; cần đo.
6. `system.geolocation` trên S5 có tần suất cập nhật bao nhiêu và có thể dùng làm "giữ app sống" + dự phòng khoảng cách không?
7. Mâu thuẫn tài liệu `package` (manifest "khác native app" vs interconnect "trùng Android package") — cần xác minh bằng build thật.
