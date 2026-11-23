# S1 - Cài `.rpk` tự làm lên Xiaomi Watch S5 (bản VN/global, HyperOS)

> Ngày: 2026-11-23. Đối tượng: chủ đồng hồ, không cần là chuyên gia. Thuật ngữ kỹ thuật giữ tiếng Anh.
> File cần cài ở S1: `spikes/s0-hello-rpk/out/com.example.motonavhud.debug.0.0.1.rpk` (package `com.example.motonavhud`, ~9 KB). Sau này: HUD thật trong `watch-app/out/`.
> Ký hiệu bằng chứng: **[XM]** = đã xác minh từ URL trích dẫn; **[S5?]** = chưa có bằng chứng trực tiếp trên S5. Độ tin cậy: cao / vừa / thấp.

---

## 0. Đọc trước: chọn đường nào?

| Hạng | Route | Điểm mạnh | Điểm yếu | Bằng chứng trên S5 |
|---|---|---|---|---|
| **1 (khuyến nghị)** | **A. Mi Fitness gốc + trang debug ẩn `ThirdAppDebugFragment`, mở bằng `adb`** | Dùng đúng đường cài của Xiaomi; **không cần AuthKey**, không gỡ Mi Fitness, không đổi pairing; Mac đã có adb + Android SDK | Phụ thuộc tên class bị obfuscate trong Mi Fitness (bản mới có thể làm hỏng); phải chạy trên **đúng điện thoại đang ghép với đồng hồ** | Có 1 repo báo đã cài lên **Watch S5 + Xiaomi 15/HyperOS** bằng cách này (vừa) |
| 2 | B. Notify for Xiaomi (Google Play) → Device → Third party app | Giao diện bấm, có hướng dẫn chính thức; trang hãng ghi S5 "Full" | Phải **gỡ / vô hiệu Mi Fitness** (một kết nối BLE tại một thời điểm); có thể cần bản trả phí; app bên thứ ba giữ AuthKey | S5 có trong bảng tương thích; tính năng "Third party app" chưa ai xác nhận riêng trên S5 (vừa) |
| 3 | C. AstroBox (Android/desktop) | Miễn phí cho tính năng lõi, hỗ trợ "Xiaomi Watch S3 trở lên" | Giao diện/tài liệu tiếng Trung; đăng nhập tài khoản Xiaomi trong app bên thứ ba; cần cho đồng hồ "kết nối điện thoại mới" | Gián tiếp (S3 trở lên) (vừa–thấp) |
| Không khuyến nghị | Gadgetbridge | Mã nguồn mở | S5 "chưa test kỹ", dùng Bluetooth Classic; chưa ai báo cài rpk trên S5; lấy key phức tạp | thấp |
| Không khả thi cho bạn | Menu Debug chính thức của Mi Fitness (Tôi → Giới thiệu → Debug) | Chính thức | Bản Mi Fitness có menu này chỉ cấp qua kênh business của Xiaomi | — |

**Điều quyết định trước tiên:** đồng hồ đang ghép với điện thoại nào?
- Nếu là **một điện thoại Android có Mi Fitness** → cắm **chính điện thoại đó** vào Mac và đi Route A. Đừng chuyển đồng hồ sang chiếc Samsung SM-N770F chỉ để thử: ghép lại sang máy mới thường bắt buộc hủy liên kết, và hủy liên kết có thể xóa dữ liệu trên đồng hồ [S5?, vừa].
- Nếu đồng hồ đang ghép với **iPhone** → không cài được `.rpk` từ iOS (rw4-cmatrix ghi rõ "iOS cannot install `.rpk`", vừa). Khi đó cần một điện thoại Android. Chiếc Samsung (Android 13) dùng được cho Route B/C, hoặc Route A sau khi ghép bằng Mi Fitness. Hãy hỏi lại trước khi hủy liên kết.
- Nếu đồng hồ **chưa ghép với máy nào** → dùng Samsung: cài Mi Fitness từ Play Store, ghép, rồi đi Route A.

---

## 1. Việc chung cho mọi route

### 1.1 Ghi lại hiện trạng (bắt buộc, 2 phút)
1. Trên đồng hồ: Settings → About (Giới thiệu) → chụp ảnh **phiên bản firmware** và model.
2. Trên điện thoại: Mi Fitness → Profile/Tôi → About (Giới thiệu) → ghi **version Mi Fitness**. Hoặc từ Mac:
   `adb -s <SERIAL> shell dumpsys package com.xiaomi.wearable | grep versionName`
3. Ghi ra giấy hoặc note cá nhân. Không commit vào repo.

### 1.2 Đưa file `.rpk` lên điện thoại (từ Mac)
```bash
adb devices -l                       # lấy serial của điện thoại đang ghép đồng hồ
adb -s <SERIAL> push "/Users/thanhng224/Dev/Personal/Xiaomi watch apps/moto-nav-hud/spikes/s0-hello-rpk/out/com.example.motonavhud.debug.0.0.1.rpk" /sdcard/Download/
adb -s <SERIAL> shell ls -l /sdcard/Download/ | grep motonavhud   # phải thấy file ~9 KB
```
- Samsung hiện tại: `<SERIAL>` = `RF8NA2EZKZK`.
- Nếu file picker không hiện file `.rpk`, thử thêm một bản có tên ngắn: `adb -s <SERIAL> shell cp /sdcard/Download/com.example.motonavhud.debug.0.0.1.rpk /sdcard/Download/hello.rpk`.

### 1.3 Quy tắc AuthKey (Route B, C, Gadgetbridge)
- AuthKey là khóa toàn quyền điều khiển đồng hồ qua Bluetooth. bandbbs gọi nó là "敏感凭据" và dặn không chụp màn hình, không chia sẻ (https://wiki.bandbbs.cn/Guides/watchface_custom_tool/watchface_custom_tool-install.html, cao).
- **Không dán AuthKey vào chat với AI, không ghi vào bất kỳ file nào trong repo** (kể cả `.env`, note, log). Nếu cần lưu thì dùng password manager.
- Nếu nghi đã lộ: gỡ thiết bị khỏi công cụ bên thứ ba, rồi bind lại qua app chính thức để đổi key (bandbbs wiki, vừa).
- Route A không cần AuthKey. Đây là một lý do nó được xếp hạng 1.

---

## 2. Route A (khuyến nghị): Mi Fitness gốc + trang debug ẩn qua adb

> **Lưu ý:** Route A **không hoạt động** trên Mi Fitness global 3.60.0i (không có `ThirdAppDebugFragment`). Route thực tế đã dùng và xác minh là **Notify for Xiaomi** (`com.mc.xiaomi1`) trên điện thoại đang ghép với đồng hồ, key nhập từ log export của Mi Fitness, cài rpk qua **Device → Third party app** (xem mục 3).

### Bằng chứng
- Bản Mi Fitness quốc tế (`com.xiaomi.wearable`) có sẵn class `com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment` với 4 nút: "click to input package name", "install third app", "uninstall third app", "go third app list". Không có menu dẫn tới trang này, nên phải mở bằng `app_process` chạy dưới user shell qua adb. Nguồn: https://github.com/oryonatan/xiaomi-band-development/blob/develop/docs/deployment-real-device.md (MIT, cập nhật 2026-04). **[XM]** trên Mi Band 8 Pro quốc tế; script có xử lý riêng bàn phím và file picker Samsung (vừa).
- Repo https://github.com/CatWar2/xiaomi-watch-s5-notes-app (cập nhật 2026-11-18) ghi: "tested on real hardware (Xiaomi Watch S5 + Xiaomi 15 on HyperOS)" và cài bằng `ThirdAppDebugFragment`. **[XM, 1 repo, 1 sao, code do AI sinh]**: độ tin cậy **vừa** cho S5.
- Đường cài này khớp với quy trình chính thức trong FAQ Vela ("Mi Fitness → Me → About → Debug → third-party apps → nhập package → install → chọn rpk local"). https://iot.mi.com/vela/quickapp/en/guide/other/faq.html (cao). Route A chỉ là cách mở cùng màn hình đó mà không cần bản Mi Fitness business.

### Điều kiện
- Điện thoại Android **đang ghép và kết nối** với S5 qua **Mi Fitness bản quốc tế** `com.xiaomi.wearable`. Bản TQ `com.xiaomi.wearable.chn` không dùng được (theo tài liệu oryonatan, vừa). Region tài khoản VN/global: chưa có bằng chứng ảnh hưởng [S5?, thấp].
- USB debugging bật trên điện thoại đó, `adb devices` thấy máy.
- Mac: Android SDK có `build-tools` (d8) + `platforms`, và `javac`. **Đã kiểm tra: Mac này có** `~/Library/Android/sdk` (build-tools 34–37, platform 33/34) và openjdk 21.
- Không cần root, AuthKey hay gỡ app nào.

### Các bước
1. Hoàn thành mục 1.1 và 1.2 (rpk nằm ở `/sdcard/Download/`).
2. Mở Mi Fitness, xác nhận đồng hồ đang **Connected**. Để đồng hồ sát điện thoại, pin trên 30%.
3. Tạo launcher `.dex`. Lấy `LaunchFragment.java` từ `scripts/deploy.sh` của repo oryonatan (đoạn heredoc `JAVA`). **Đọc code trước khi chạy**: nó chỉ load class của Mi Fitness rồi gọi `startActivity` tới `CommonBaseActivity`. Biên dịch trong thư mục tạm, **không** đặt trong repo:
   ```bash
   SDK=~/Library/Android/sdk
   javac -source 8 -target 8 -bootclasspath $SDK/platforms/android-34/android.jar LaunchFragment.java -d classes
   $SDK/build-tools/36.0.0/d8 classes/LaunchFragment.class --output dex/
   adb -s <SERIAL> push dex/classes.dex /data/local/tmp/launch.dex
   ```
4. Mở trang debug ẩn:
   ```bash
   APK=$(adb -s <SERIAL> shell pm path com.xiaomi.wearable | head -1 | sed 's/package://' | tr -d '\r')
   adb -s <SERIAL> shell "CLASSPATH=/data/local/tmp/launch.dex app_process / LaunchFragment $APK"
   ```
   Kỳ vọng: in `SUCCESS`, và trên điện thoại hiện trang có 4 nút tiếng Anh nói trên.
5. Trên điện thoại, bấm tay (không cần tự động hóa):
   1. **click to input package name** → gõ `com.example.motonavhud` → OK. Bàn phím Samsung có thể tự chèn dấu cách sau dấu chấm đầu tiên, nên xóa nó trước khi bấm OK.
   2. **install third app** → file picker mở → vào **Downloads** → chọn `com.example.motonavhud.debug.0.0.1.rpk`.
   3. Chờ đồng hồ nhận file. Giữ màn hình điện thoại bật và Mi Fitness ở foreground.
6. Trên đồng hồ: mở danh sách app (bấm nút trên) → tìm **"Moto Nav HUD"** (icon mũi tên) → mở → làm theo **Checklist S1** (mục 6).
7. Gỡ: lặp lại bước 4 → nhập package → **uninstall third app**. Xem danh sách: **go third app list**.
8. Dọn dẹp: `adb -s <SERIAL> shell rm /data/local/tmp/launch.dex` (không bắt buộc).

### Ghép nối / Mi Fitness
- Không thay đổi gì: Mi Fitness vẫn là app giữ kết nối và chính nó đẩy rpk. Không cần force-stop, không cần khôi phục.

### Lỗi thường gặp
| Triệu chứng | Nguyên nhân khả dĩ | Xử lý |
|---|---|---|
| `ClassNotFoundException` / `NoSuchMethodException` (`FragmentParams$b`, method `e`/`b`) | Mi Fitness đã đổi tên class obfuscate | Ghi lại version Mi Fitness và báo lại. Cần dò lại tên class (Claude có thể làm bằng cách phân tích APK) hoặc chuyển sang Route B |
| `ERROR: ... not found` / `pm path` rỗng | Không có Mi Fitness bản quốc tế | Kiểm tra package `com.xiaomi.wearable` |
| `SecurityException` khi startActivity | Android/ROM chặn shell mở activity không export | Báo lại kèm log; thử Route B |
| Bấm install nhưng đồng hồ không nhận | Đồng hồ ngắt kết nối, hoặc Mi Fitness bị kill | Mở lại Mi Fitness, chờ Connected, thử lại |
| App cũ không cập nhật | Đồng hồ cache bản cũ | Tăng `versionCode` khi build lại, hoặc uninstall trước (oryonatan, vừa) |
| Báo lỗi chữ ký | S1 không dùng interconnect nên không nên xảy ra | Debug-signed rpk đủ dùng cho app không gọi interconnect (rw4-cmatrix, vừa) |

---

## 3. Route B: Notify for Xiaomi

### Bằng chứng
- Trang tương thích của hãng liệt kê **Xiaomi Watch S5: "Full"**, cột official app: **"Must Uninstall"**. Trang có mục cài app `.rpk` tự làm (VelaJS). http://www.mibandnotify.com/xiaomi-mi-band/ **[XM]** (vừa: trang tự công bố).
- Hướng dẫn chính thức: "Open Notify → Device → Third party app → upload .rpk". Hỗ trợ "tùy firmware", không nêu riêng S5. http://www.mibandnotify.com/xiaomi-mi-band/notify-xms-app-instructions.php **[XM]**, S5 [S5?] (vừa).
- Giá: bản Free $0, Pro $3 (mua một lần), không thấy trial. Không xác định được "Third party app" có cần Pro hay không. rw4-cmatrix cũng ghi là "không được tài liệu hóa" (thấp).
- Cài đặt dùng wizard lấy pairing key **qua tài khoản Xiaomi**, nên Mi Fitness không cần ở lại máy. https://github.com/hyperpostulate/rw4-cmatrix (vừa; test trên Redmi Watch 4).

### Điều kiện
- Điện thoại Android. Chiếc Samsung (Android 13) dùng được. App "Notify for Xiaomi/Mi Band 8-11" (`com.mc.xiaomi1`) từ Google Play.
- Tài khoản Xiaomi **mà đồng hồ đang bind**, đăng nhập trong wizard của Notify.
- Mi Fitness không được giữ BLE: phải gỡ, hoặc force-stop và thu quyền "Nearby devices".

### Các bước
1. Mục 1.1 và 1.2.
2. **Không bấm "Unpair/Hủy liên kết" trong Mi Fitness.** Hủy liên kết sẽ làm key mất hiệu lực (Gadgetbridge docs, cao; rw4-cmatrix: "do not un-pair... before uninstalling").
3. Trên điện thoại đang có Mi Fitness: Settings → Apps → Mi Fitness → **Force stop**, rồi Permissions → **Nearby devices → Don't allow**. Tắt Mi Fitness trong battery/autostart nếu có. Trang hãng ghi "Must Uninstall": nếu Notify vẫn báo xung đột thì gỡ Mi Fitness (không unpair).
4. Cài Notify từ Play Store → mở → chạy wizard → chọn đồng hồ S5 → đăng nhập tài khoản Xiaomi **ngay trong app**. Đừng chép key ra ngoài.
5. Chờ trạng thái connected. Đồng hồ có thể hỏi xác nhận ghép, hãy bấm đồng ý.
6. Notify → **Device** → **Third party app** → chọn file trong **Download** → làm theo hướng dẫn trên màn hình, giữ đồng hồ gần điện thoại.
7. Trên đồng hồ: tìm "Moto Nav HUD" → mở → Checklist S1.

### Khôi phục Mi Fitness sau khi xong
1. Trong Notify: ngắt kết nối hoặc force-stop Notify, thu quyền Nearby devices của Notify. Đừng xóa thiết bị khỏi tài khoản.
2. Bật lại quyền cho Mi Fitness (hoặc cài lại, đăng nhập cùng tài khoản) → mở → chờ đồng hồ tự kết nối lại.
3. Kiểm tra đồng hồ vẫn còn "Moto Nav HUD" và dữ liệu sức khỏe đồng bộ được. Việc kết nối lại không cần ghép lại là **suy luận** [S5?, vừa].

### Lỗi thường gặp
- Không kết nối được: Mi Fitness vẫn chạy ngầm và giữ BLE. Force-stop lại, kiểm tra Nearby devices.
- Không thấy mục Third party app: bản app cũ, hoặc tính năng cần Pro (chưa rõ). Cập nhật app rồi xem màn hình mua.
- Cài báo lỗi: firmware S5 có thể chặn. Ghi nguyên văn lỗi và báo lại.

---

## 4. Route C: AstroBox

### Bằng chứng
- Tài liệu chính thức: "Xiaomi Watch S3 及以后" thuộc nhóm hỗ trợ đầy đủ. Kết nối bằng **đồng bộ tài khoản Xiaomi** (khuyến nghị) hoặc nhập AuthKey thủ công. Tài liệu **không** nói cách lấy AuthKey. https://abox.run/docs/usage/connection **[XM]**, S5 suy ra từ "S3 trở lên" (vừa–thấp).
- Cài file local: thẻ loại tài nguyên trên trang thiết bị hoặc nút "+" trong hàng đợi (queue) → chọn file → nếu không tự cài thì bấm "开始". https://abox.run/docs/usage/resource **[XM]**. Trang này không nhắc riêng `.rpk`/快应用, nhưng trang chủ nói cài được "quick apps" (vừa).
- Android 10 trở lên (Samsung Android 13 đạt), cần quyền Location (precise), Bluetooth, Nearby devices; tải APK từ trang chính thức (V2.2.6). https://abox.run/docs/usage/install **[XM]**. Mã nguồn: https://github.com/AstralSightStudios/AstroBox-NG (AGPL-3.0). Tính năng lõi miễn phí (https://astrobox.online).

### Các bước (tóm tắt, giao diện tiếng Trung)
1. Mục 1.1 và 1.2. Force-stop Mi Fitness và thu quyền Nearby devices như Route B bước 3. Tài liệu AstroBox không nói phải làm vậy; đây là suy luận từ quy tắc một kết nối [S5?, vừa].
2. Cài AstroBox APK từ trang chính thức → cấp quyền → hoàn tất OOBE.
3. Mở thẻ thiết bị (viên nang ở đáy) → nút chuyển thiết bị → **导入** → đăng nhập tài khoản Xiaomi → bấm **登录** (có thể có xác minh 2 bước).
4. Trên đồng hồ: Settings → 我的设备/My device → **连接新手机** (Connect new phone) → dấu tick xanh. Trong AstroBox chọn S5 → đồng hồ rung → tick xanh lần nữa → trên Android bấm **配对与连接** ở thông báo ghép.
   - Cảnh báo: chưa rõ "连接新手机" có làm đồng hồ mất kết nối với Mi Fitness lâu dài hay không [S5?, thấp]. Làm sau cùng nếu Route A/B thất bại.
5. Mở queue → **+** → chọn loại 快应用/quick app → chọn file trong Download → bấm **开始** nếu cần.
6. Đồng hồ → "Moto Nav HUD" → Checklist S1.
7. Khôi phục Mi Fitness giống Route B. Có thể phải "连接新手机" lần nữa từ Mi Fitness [S5?, thấp].

---

## 5. Route không khuyến nghị (ghi để tham khảo)

- **Gadgetbridge**: Watch S5 được ghi "Mostly supported", nhưng kèm câu "As we don't have that device it is not known if it works correctly", "not heavily tested" (https://gadgetbridge.org/gadgets/wearables/xiaomi/, cao về nội dung trang). Issue #7197 cho thấy S5 kết nối bằng **Bluetooth Classic (RFCOMM)** và từng bị nhận nhầm là S4. Chưa có báo cáo cài rpk trên S5 (https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/7197, vừa). Tài liệu protobuf có liệt kê "Flashing (... rpk)" (https://gadgetbridge.org/basics/topics/xiaomi-protobuf/, cao). Để lấy key phải dò log Mi Fitness hoặc dùng "Xiaomi Cloud Tokens Extractor", tức là đưa mật khẩu Xiaomi cho script bên thứ ba (https://gadgetbridge.org/basics/pairing/huami-xiaomi-server/, cao). Maintainer cũng cảnh báo rủi ro brick khi cài tài nguyên không hỗ trợ. → Chỉ đáng thử nếu sau này muốn bỏ Mi Fitness hẳn.
- **表盘自定义工具 (bandbbs)**: liệt kê S5, "蓝牙一键安装" với AuthKey đọc từ Mi Fitness (Mi Fitness → 我的 → 关于 → bấm logo cam nhiều lần → dùng "AuthKey reader" trong tool). Tool chủ yếu phục vụ tài nguyên trong kho "探索", chưa rõ có cài file `.rpk` local không. Cũng dùng "连接新手机" (wiki bandbbs, vừa). Thao tác "bấm logo/phiên bản nhiều lần" trong bản Mi Fitness quốc tế **không tìm được nguồn xác nhận** (thấp).
- **Menu Debug chính thức** (Mi Fitness → Me → About → Debug → third-party apps): có thật, nhưng FAQ ghi bản Mi Fitness có menu này được cấp "through business group" (https://iot.mi.com/vela/quickapp/en/guide/other/faq.html, cao). Bấm vào số phiên bản trên Mi Fitness bản Play Store **không** được tài liệu nào xác nhận là mở ra menu này (thấp). Có thể thử 10 lần bấm, vô hại, nhưng đừng kỳ vọng.
- **MiWearBridge(-fixed)**: LSPosed, cần root. Không phù hợp.

---

## 6. Checklist S1: quan sát trên đồng hồ sau khi mở "Moto Nav HUD"

Mở app, quan sát **ít nhất 2 phút**, không chạm màn hình. Chụp 1–2 ảnh màn hình đồng hồ (giữ đồng thời nút trên + nút dưới, hoặc chụp bằng điện thoại).

| # | Quan sát | Kỳ vọng (theo `src/pages/index/index.ux`) | Ghi lại |
|---|---|---|---|
| 1 | App có trong danh sách app, tên + icon | "Moto Nav HUD", icon mũi tên trắng | có/không |
| 2 | Mở được, thời gian từ lúc bấm tới khi có hình | Nhanh (< 2 s) | số giây ước lượng |
| 3 | Nền | Đen tuyền | đúng/sai |
| 4 | Dòng "Hello S5" | Chữ trắng lớn, giữa màn hình | hiển thị/không |
| 5 | Dòng "Đường Nguyễn Văn Linh ơ ư ế ộ" | Dấu tiếng Việt đúng, không ô vuông □, không `?`, không mất dấu | chụp ảnh; ghi ký tự nào sai |
| 6 | Mũi tên | Mũi tên trắng 128 px, **xoay 45°** (chéo) | chéo / thẳng / không hiện |
| 7 | Bố cục | Cân giữa, không bị cắt ở mép tròn, kích thước hợp lý | Nếu mọi thứ **to gấp đôi / bị cắt** hoặc **nhỏ một nửa** thì ghi lại. Repo S5 khác dùng `designWidth: 240` trong khi spike dùng 480 (vừa) |
| 8 | Màn hình giữ sáng | **Không tự tắt/không về mặt đồng hồ** sau timeout bình thường | giây tới khi tắt (nếu tắt) |
| 9 | Thông báo lỗi | Không có | **chép nguyên văn** mọi chữ lỗi (cả tiếng Trung) |
| 10 | Thoát và mở lại (vuốt phải / nút) | Mở lại bình thường | ok/lỗi |

**Báo lại cho Claude (copy mẫu):**
```
Route đã dùng: A / B / C
Phone: <model>, Android <x>; Mi Fitness version: <x>; Watch S5 firmware: <x>
Cài: thành công / lỗi "<nguyên văn>"
1..10: <kết quả từng dòng>
Ảnh: <đính kèm>
```
Không gửi AuthKey, số serial đồng hồ, địa chỉ MAC hay ảnh có chứa key.

---

## 7. An toàn

- **Không factory-reset đồng hồ, không "Unpair/Hủy liên kết"** trong Mi Fitness để thử. Reset đổi Bluetooth MAC và vô hiệu key (Gadgetbridge docs, cao), xóa dữ liệu, có thể kéo theo phải ghép lại từ đầu.
- **Ghi firmware + version Mi Fitness trước** (mục 1.1). Tạm **tắt tự cập nhật** Mi Fitness (Play Store → Mi Fitness → ⋮ → bỏ "Enable auto update") trong giai đoạn dev: Route A phụ thuộc tên class nội bộ, nên bản mới có thể làm hỏng. Hoãn OTA firmware đồng hồ cho tới khi xong spike, vì firmware mới có thể chặn sideload (thấp–vừa, suy luận từ báo cáo Band 10 Pro global không trả lời dịch vụ third-party app; xem `docs/research/02-phone-watch-transport.md` 2.2).
- **Rủi ro công cụ cộng đồng**: Notify/AstroBox/Gadgetbridge không liên kết với Xiaomi. Đăng nhập tài khoản Xiaomi vào app bên thứ ba nghĩa là giao token cho họ. Sideload có thể trái ToS Xiaomi. Về lý thuyết Xiaomi có thể khóa tính năng hoặc tài khoản (chưa thấy báo cáo, thấp). Tài nguyên không đúng model có thể brick (cảnh báo của maintainer Gadgetbridge).
- **Chỉ cài rpk do chính mình build** (từ `spikes/` hoặc `watch-app/out/`), không cài rpk lạ.
- Route A chạy `app_process` với quyền shell: chỉ dùng code đã đọc. Không cần và không được bật root.
- Mọi thao tác ở đây do chủ đồng hồ tự làm. Claude chưa kết nối thiết bị và chưa cài gì.

---

## Kết luận ngắn
- Route khuyến nghị: **A, Mi Fitness gốc + `ThirdAppDebugFragment` mở bằng adb**. Không cần AuthKey, không đụng pairing, Mac đã đủ công cụ. Có một báo cáo cài thành công trên S5 (vừa).
- Dự phòng: **B, Notify for Xiaomi** (S5 "Full" theo hãng, nhưng phải gỡ/vô hiệu Mi Fitness), rồi **C, AstroBox**.

## Rủi ro chặn
- Tên class obfuscate (`FragmentParams$b`, method `e`/`b`) có thể khác ở bản Mi Fitness của bạn, làm Route A hỏng.
- Đồng hồ đang ghép với iPhone hoặc máy khác không có adb: phải đổi máy ghép, có nguy cơ mất dữ liệu.
- Firmware S5 global có thể từ chối cài rpk bên thứ ba (chưa thấy bằng chứng, thấp).

## Câu hỏi còn mở
- Đồng hồ đang ghép với điện thoại nào (Android có Mi Fitness? iPhone?).
- Version Mi Fitness và firmware S5 hiện tại.
- "Third party app" của Notify có cần Pro không.
- `designWidth` đúng cho S5 là 480 hay 240 (sẽ rõ ở Checklist mục 7).
