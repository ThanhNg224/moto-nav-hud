# Moto Nav HUD - watch-app (Xiaomi Watch S5, Vela QuickApp)

HUD chỉ đường cho xe máy trên đồng hồ tròn 480x480. Nhận dữ liệu từ điện thoại theo `docs/protocol.md` (v1.4), package `dev.motonavhud.app`.

## Cấu trúc
- `src/manifest.json`: features `system.router`, `system.brightness`, `system.vibrator`, `system.interconnect`, `system.bluetooth.ble` (tên feature BLE đúng theo docs là `system.bluetooth.ble`, không phải `system.bluetooth`).
- `src/common/hud-logic.js`: logic thuần (parse/validate, z/q, staleness, `k` ưu tiên, rung theo bước `i`, định dạng khoảng cách, mapping mũi tên, demo). Không import framework.
- `src/common/config.js`: `FOLD_DIACRITICS` (mặc định false), `STALE_MS` 3000, `MAX_SOURCE_AGE_MS` 10000, `NAV_LOST_SOURCE_AGE_MS` 65000, ngưỡng rung 200/50 m, `PAIR_TOKEN`, MTU 185, backoff BLE, `BLE_PINNED_ADDRESS`.
- `src/common/transport-interconnect.js` (kênh A) và `transport-ble.js` (kênh C): cùng giao diện `create({onMessage, onStatus}) -> {start, stop}`. Chạy song song, tin hợp lệ đến trước thắng (cùng bộ lọc z/q).
- `src/pages/index/index.ux`: màn hình HUD. `src/common/arrows/m00..m13.png`: mũi tên trắng 160x160 (m00 không dùng để hiển thị).
- `tools/gen_arrows.py`: sinh PNG bằng stdlib. `test/`: unit test `node --test`.
- `sign/debug`, `sign/release`: khóa tự ký (gitignore `*.pem`). Lưu ý: interconnect thật cần ký bằng đúng chứng chỉ của app Android; khóa này chỉ để build/sideload.

## Rebuild
```
cd watch-app
npm install
npm test
python3 tools/gen_arrows.py            # chỉ khi sửa mũi tên
npx aiot build   && cp dist/*.rpk out/ # debug
npx aiot release && cp dist/*.rpk out/ # release (mỗi lệnh xóa dist/)
```
Kết quả: `out/dev.motonavhud.app.{debug,release}.0.4.3.rpk` (~30 KB / ~27 KB).

## Chế độ DEMO
Ở màn "Chờ dữ liệu" chạm màn hình: chạy vòng qua các maneuver mỗi 2 s với khoảng cách giả (qua cùng đường parse/accept/rung như tin thật). Chạm lần nữa để dừng. Tin thật đến sẽ tự tắt demo.

## Hành vi chính (protocol v1.4)
- Tin chỉ được nhận khi: `v=1`, `p` == `PAIR_TOKEN` (config.js, phải trùng phone-app), miền giá trị hợp lệ (`q>=1`, `a>=0`, `d>=-1`, `x>=0`, số nguyên an toàn), UTF-8 đúng. Kiểm tra xong mới đụng trạng thái, nên tin xấu không bao giờ xóa hướng dẫn đang có.
- `q` phải lớn hơn `q` đã nhận cùng `z` (trùng không làm mới timer). `z` mới reset q/rung; vài `z` đã thay thế bị nhớ và bỏ tin.
- "MẤT DỮ LIỆU" khi >3000 ms không có tin, hoặc đồng hồ lùi (cho tới khi có tin mới), hoặc tuổi hiệu dụng `a + (now - lúc nhận)` vượt ngưỡng theo `k`: `k=1` > 65000 (`NAV_LOST_SOURCE_AGE_MS`); `k=2`/`k=3` > 10000 (`MAX_SOURCE_AGE_MS`).
- `k=1` với tuổi 10000–65000 là "cũ" (im lặng): giữ mũi tên, khoảng cách và tên đường, hiện dấu mờ "cũ Ns" (EN "old Ns") cạnh khoảng cách; không banner, không rung mất dữ liệu, không rung tiệm cận, không đổi màu tiếp cận/vòng cung. Tin mới đưa về tươi ngay.
- Rung mất dữ liệu 1 lần mỗi đợt mất (chỉ khi tin cuối là `k=1`).
- Kênh ngắt (BLE ngắt/im lặng, interconnect đóng/lỗi): xóa hướng dẫn ngay (hiện MẤT DỮ LIỆU), giữ z/q riêng để tin trùng không khôi phục, bỏ nhãn LIVE.
- `k=0` chờ, `k=2` "Đang tính lại", `k=3` "Đã đến": xóa mũi tên ngay.
- Rung (`common/haptics.js`, `vibrator.start` + `vibrator.stop(id)`; lỗi start thì fallback `vibrate`): chỉ khi `k=1`, `m!=0`, `d>=0`, nguồn tươi (không rung khi "cũ"); mỗi mức một lần cho mỗi bước `i`; `d<=50` 3 xung dài, `d<=200` 2 xung ngắn. Hủy ngay khi idle/tính lại/đến/m=0/bước mới ngoài vùng cảnh báo/mất kênh/rung khẩn hơn thay thế.
- Demo tách trạng thái với tin thật: heartbeat `k=0` không làm gián đoạn demo; tin thật `k!=0` tự dừng demo. Chạm để dừng.
- Dòng trạng thái: `LIVE <trạng thái kênh đang sống>` hoặc trạng thái cả hai kênh (A = interconnect, C = BLE) + lỗi sáng màn hình nếu có. `setKeepScreenOn(true)` xử lý `fail`, và tắt khi ẩn/hủy trang.
- BLE: scan chỉ theo service UUID đầy đủ (mọi nhánh, kể cả ghim `BLE_PINNED_ADDRESS`, được lọc khi nhận kết quả), connect, và chỉ khi nhận trạng thái CONNECTED mới gửi MTU 185 -> getServices -> subscribe notify (callback `connect` chỉ là xác nhận gửi lệnh). Callback MTU không mang giá trị đàm phán nên HUD không tự khẳng định MTU; "sẵn sàng" = tin hợp lệ đầu tiên. Không có tin trong 8 s sau subscribe: "Không có dữ liệu BLE (MTU?)" và nối lại (backoff 1/2/4/8/15 s, chỉ reset sau tin hợp lệ); im lặng 8 s sau đó cũng nối lại. Mọi callback gắn với generation, bị bỏ khi attempt đã đóng; generation bị vô hiệu hóa trước khi giải phóng scanner/gatt.

## Đã kiểm chứng
- `npm test`: 51 test pass (logic, token, miền giá trị, staleness/đồng hồ lùi, session cũ, rung/hủy rung/chống trễ khi `stop` trả `false`, màu khẩn cấp & trạng thái, thứ tự connect BLE, retry/backoff, interconnect sau stop). `npx aiot build` và `npx aiot release` exit 0.
- Phần cứng thật (**Xiaomi Watch S5 46mm fw 3.222.007 + OPPO CPH2873 Android 17**): cài `.rpk` qua Notify for Xiaomi (hiện đúng tên `Moto Nav HUD` + icon), BLE V3 (`filters:[{}]`) tự nối và nhận >1.000 frame ổn định, chữ Việt có dấu rõ, 14 mũi tên (đã chỉnh lại `Gắt trái`/`Gắt phải`), rung 2 ngắn (`≤ 200 m`) / 3 dài (`≤ 50 m`).

## CHƯA kiểm chứng
- Chạy đường thật dài hơi (30 phút+, đo pin và hành vi wrist-down/AOD dài).
- Kênh A (`@system.interconnect` qua Mi Fitness — chưa triển khai phía phone).

## Gợi ý debug cho S1
- Đọc dòng trạng thái dưới màn hình: `A chờ/nối/lỗi <mã>` (1001 = app điện thoại chưa cài, 1006 = mất nối), `C quét/thấy xxxxx/đã nối/MTU đã yêu cầu/chờ tin/nối/ngắt thử lại Ns/Không có dữ liệu BLE (MTU?)`, `scan|conn|svc|notify|MTU <mã>` (10001 BT tắt, 10012 lỗi nối, 10014 không thấy thiết bị, 205 trùng lệnh, 203 không hỗ trợ).
- Log: `console.log` bật (`logLevel: log`); lấy log qua Mi Fitness > Debug > kéo log firmware.
- Không thấy thiết bị BLE: kiểm tra phone đang quảng bá service UUID (tên nằm trong scan response); thử ghim địa chỉ.
- Cần dùng đúng package Android + cùng chứng chỉ khi thử interconnect.

## Ghi chú mã ghép

`PAIR_TOKEN` mặc định (`MOTONAV1`) dùng chung cho công cụ cá nhân một điện thoại. Nếu có hai điện thoại chạy app gần nhau, đổi token ở cả `config.js` và Prefs của phone-app.

## Log debug

`DEBUG_LOG` (config.js) bật log `console.log` tiền tố `[MotoNav]`: quét/biến thể, máy khớp, trạng thái nối, bước thiết lập, frame đầu, đếm frame (tối đa 1 dòng/giây), retry + backoff, mất tin, rung.
