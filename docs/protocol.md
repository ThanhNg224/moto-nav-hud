# Hợp đồng dữ liệu phone -> watch (v1.3)

Nguồn sự thật duy nhất cho `phone-app/` và `watch-app/`. Đổi hợp đồng phải sửa file này trước. v1.1 (2026-11-23) tiếp thu review thiết kế của Codex Astra (`docs/research/05-codex-protocol-review.md`). v1.2 tiếp thu review code của Codex sol: thêm mã ghép `p`, NAV chỉ Notify, tuổi hiệu dụng phía đồng hồ, ngân sách byte thực tế. v1.3 (2026-11-23): thêm khóa tùy chọn `y` đánh dấu khung demo của phone.

## Payload (JSON UTF-8, kích thước đo trên chuỗi đã serialize, tối đa `min(180, MTU-3)` byte)

```json
{"v":1,"p":"MOTONAV1","z":48211,"q":42,"i":7,"a":900,"k":1,"m":3,"x":0,"d":180,"s":"Nguyễn Văn Linh","e":"12:45","r":4200}
```

| Khóa | Ý nghĩa | Kiểu | Ghi chú |
| --- | --- | --- | --- |
| `v` | phiên bản hợp đồng | int | luôn 1 |
| `z` | session của phone | int | số ngẫu nhiên tạo mỗi lần service khởi động |
| `q` | số thứ tự trong session | int | tăng nghiêm ngặt mỗi tin gửi đi |
| `i` | định danh bước rẽ | int | phone tăng khi `(m, x, s)` đổi hoặc `d` tăng vọt khi qua bước mới; dùng để rung đúng một lần cho mỗi bước |
| `a` | tuổi của nguồn, ms | int | thời gian từ lần Maps đăng cập nhật thật gần nhất (`postTime`) tới lúc gửi; heartbeat và kiểm tra thông báo còn sống KHÔNG làm mới `a` |
| `k` | trạng thái | int | 0 rảnh/không có dẫn đường, 1 đang dẫn đường, 2 đang tính lại tuyến, 3 đã đến. **`k` có quyền cao hơn `m`** |
| `m` | maneuver | int | bảng dưới |
| `x` | lối ra vòng xuyến | int | 0 nếu không áp dụng |
| `d` | khoảng cách tới điểm rẽ, mét | int | -1 nếu không rõ |
| `s` | tên đường hoặc chỉ dẫn | string | cắt ở ranh giới ký tự (grapheme), đo lại byte của JSON sau khi cắt; rỗng nếu không có |
| `e` | giờ đến dự kiến `HH:mm` | string | rỗng nếu không rõ |
| `r` | quãng đường còn lại, mét | int | -1 nếu không rõ |
| `p` | mã ghép | string | tối đa 8 ký tự `[A-Za-z0-9]`, đặt giống nhau ở `phone-app` (Prefs) và `watch-app/src/common/config.js`. Đồng hồ bỏ mọi tin có `p` khác. Gửi dạng rõ nên chỉ chống nhận nhầm/giả mạo vô tình, không phải mã hóa |
| `y` | đánh dấu demo | int | TÙY CHỌN, chỉ có (giá trị 1) khi khung đến từ chế độ chạy thử của phone; vắng mặt với dẫn đường thật. Thuộc lõi: không bao giờ bị cắt. Đồng hồ hiện nhãn "demo" khi thấy `y`=1; đồng hồ cũ bỏ qua khóa lạ nên không bị ảnh hưởng (`v` vẫn là 1). Google Maps thật luôn thắng demo: khi Maps dẫn đường, phone tắt demo và gửi khung thật (không `y`) |

`t` (thời điểm phone) đã bỏ khỏi hợp đồng; đo độ trễ bằng RTT khi cần.

Thứ tự cắt khi vượt ngân sách byte: bỏ `e`, rồi rút `s` dần, rồi bỏ `s`, rồi bỏ `r` nếu lõi vẫn chưa vừa (thứ tự e, s, r). Lõi tối thiểu (`v,z,q,i,a,k,m,x,d,p`, cộng `y`=`,"y":1` +6 byte ở khung demo) với giá trị lớn nhất thực tế dài khoảng 100 byte, nên cần MTU đàm phán từ 107 trở lên (113 cho khung demo với giá trị lớn nhất). Nếu lõi không vừa `min(180, MTU-3)` thì phone KHÔNG gửi (trả kết quả "không vừa", hiện cảnh báo trên màn hình phone), không được gửi tin vượt ngân sách. Đồng hồ không biết MTU thật (API không trả giá trị đàm phán); nếu sau khi subscribe không nhận được tin hợp lệ nào trong 8 giây thì thử subscribe lại; rider thấy giai đoạn kết nối đã bản địa hóa của đồng hồ (chuỗi "Không có dữ liệu BLE (MTU?)" chỉ là chẩn đoán debug).

## Quy tắc phía phone

- Gửi khi có thay đổi (`k`, `m`, `x`, `s`, `d` lệch từ 10 m trở lên) và heartbeat mỗi 1000 ms. Heartbeat gửi lại trạng thái cuối cùng với `a` tăng đúng theo thời gian, `q` tăng.
- Khi thông báo Maps biến mất, listener mất kết nối, parse thất bại hoặc nguồn không có bản đăng mới quá 15 giây: gửi `k=0` (hoặc `k=2` nếu đang tính lại tuyến), tuyệt đối không giữ `k=1` với dữ liệu cũ. Phone định kỳ 3 s kiểm tra `getActiveNotifications` để phát hiện ngay thông báo biến mất hoặc đọc bù bản đăng có `postTime` mới hơn bị lỡ callback — nhưng KHÔNG làm mới `a` nếu thông báo không có cập nhật mới.
- `k=3` chỉ khi đã nhận ra rõ là đến nơi; hủy dẫn đường là `k=0`.
- `z` mới mỗi lần service khởi động; `q` bắt đầu từ 1.

## Quy tắc phía đồng hồ

- **Chấp nhận tin:** bỏ tin sai `v`, JSON hỏng, thiếu lõi, hoặc `q` không lớn hơn `q` đã nhận cùng `z` (trùng lặp cũng bị bỏ, không làm mới timer). Khi gặp `z` mới: chấp nhận ngay, đặt lại `q` và trạng thái rung, ẩn hướng dẫn cho tới khi có tin hợp lệ đầu tiên của session.
- **Staleness (đồng hồ của watch):** quá 3000 ms không có tin được chấp nhận, hoặc tuổi hiệu dụng `a + (now - thời điểm nhận)` > 10000 ms: hiện "MẤT DỮ LIỆU", ẩn mũi tên. Nếu đồng hồ lùi (now < thời điểm nhận) thì coi là mất dữ liệu cho tới khi có tin mới. Khi kênh ngắt, xóa hướng dẫn đang hiển thị ngay (giữ riêng `z`/`q` cuối để tin trùng không khôi phục lại), và ẩn cho tới khi nhận tin hợp lệ mới. Đồng hồ nhớ vài `z` đã bị thay thế và bỏ tin của chúng.
- **Kiểm tra miền giá trị trước khi đổi trạng thái:** `q` >= 1, `a` >= 0, `d` >= -1, `x` >= 0, mọi số nguyên trong miền an toàn của JS, `p` khớp; UTF-8 hỏng thì bỏ tin.
- **`k` ưu tiên:** `k` khác 1 thì xóa mũi tên và mọi cảnh báo rẽ ngay lập tức. `k=0` hiện màn chờ, `k=2` hiện "Đang tính lại", `k=3` hiện "Đã đến".
- **Rung:** chỉ khi `k=1`, `m` khác 0 và `d >= 0`. Mỗi bước (`i`) chỉ rung một lần, theo ngưỡng khẩn nhất đang áp dụng: `d <= 50` rung 3 xung dài, ngược lại `d <= 200` rung 2 xung ngắn. Lần quan sát đầu tiên đã nằm trong ngưỡng cũng được rung (một lần, đúng ngưỡng khẩn nhất). Khi ngưỡng khẩn hơn xuất hiện thì hủy rung đang chạy rồi mới rung mới. Mất dữ liệu rung 1 lần cho mỗi đợt mất; màn chờ, đã đến, và timeout lúc khởi động không rung.

## Maneuver (`m`)

0 không rõ, 1 đi thẳng, 2 chếch trái, 3 rẽ trái, 4 gắt trái, 5 chếch phải, 6 rẽ phải, 7 gắt phải, 8 quay đầu, 9 vòng xuyến (kèm `x`), 10 giữ trái, 11 giữ phải, 12 đã đến, 13 xuất phát.

## Kênh truyền

- **A, interconnect:** `connect.send` từ phone (XMS `MessageApi.sendMessage(nodeId, bytes)`), đồng hồ nhận `onmessage({data})` là chuỗi JSON ở trên. Chưa triển khai phía phone ở vòng này.
- **C, BLE GATT** (Android là peripheral, rpk là central):
  - Service UUID `6e4f0001-b5a3-f393-e0a9-e50e24dcca9e`, characteristic NAV `6e4f0002-b5a3-f393-e0a9-e50e24dcca9e` (chỉ Notify, kèm CCCD 0x2902; không có Read, để người lạ không đọc được snapshot). CCCD chỉ nhận giá trị 0x0001 (bật) hoặc 0x0000 (tắt) ở offset 0.
  - **Quảng bá:** gói chính chứa cờ và service UUID 128-bit (tổng ≤ 31 byte). Scan response chỉ có tên Bluetooth của máy khi vừa; Android không đặt được tên tùy ý. Đồng hồ KHÔNG phụ thuộc tên: mọi nhánh scan đều lọc theo service UUID. Nếu quảng bá lỗi `ADVERTISE_FAILED_DATA_TOO_LARGE` thì thử lại không kèm tên. Phải kiểm tra `BluetoothLeAdvertiser` khác null, quyền, và kết quả start.
  - **Thứ tự phía đồng hồ:** coi callback của `connect` chỉ là xác nhận đã gửi lệnh; chỉ bắt đầu MTU, khám phá service và subscribe khi nhận sự kiện trạng thái CONNECTED.
  - **MTU:** đồng hồ yêu cầu 185 sau khi kết nối; Android dùng MTU đã đàm phán của từng peer để tính `min(180, MTU-3)`. MTU mặc định 23 chỉ chứa 20 byte nên không đủ.
  - **Notify:** chỉ gửi cho peer đã ghi CCCD; gửi ngay một snapshot khi peer subscribe; mỗi thời điểm chỉ một notify đang bay, giữ lại duy nhất tin mới nhất cho tới khi `onNotificationSent`; đồng hồ tự subscribe lại sau reconnect.
  - **Chọn đúng peer:** đồng hồ lọc theo service UUID; có thể ghim địa chỉ thiết bị đã kết nối thành công lần đầu trong cấu hình (`config.js`) để tránh nối nhầm máy khác.
- Phone gửi cùng một JSON cho mọi kênh; đồng hồ chấp nhận tin từ kênh nào hợp lệ trước, mỗi tin đi qua cùng một bộ lọc `z`/`q`.
