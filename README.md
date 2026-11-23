# moto-nav-hud

HUD chỉ đường xe máy trên Xiaomi Watch S5 (Vela QuickApp `.rpk`), lấy dữ liệu từ Google Maps trên điện thoại Android qua BLE.

| Thư mục | Mục đích |
| --- | --- |
| `docs/protocol.md` | Hợp đồng dữ liệu phone -> watch (v1.2), nguồn sự thật duy nhất |
| `docs/research/` | Báo cáo nghiên cứu và review của Codex (bắt đầu từ `00-brief.md`) |
| `docs/guides/` | Hướng dẫn thao tác tay (S1: cài `.rpk` lên S5) |
| `docs/plans/` | Kế hoạch triển khai (không commit, xoá khi xong) |
| `spikes/` | Thử nghiệm ngắn kiểm chứng rủi ro kỹ thuật |
| [`watch-app/`](watch-app/README.md) | Mini-app `.rpk` trên đồng hồ: build, test, sideload |
| [`phone-app/`](phone-app/README.md) | App Android: đọc thông báo Google Maps, phát BLE GATT, chế độ Demo |

## Trạng thái (v0.2.0)

- **Watch app (v0.2.0, 52 test pass)**: giao diện đồng hồ tròn lấy cảm hứng Beeline Moto / Wear OS — vòng cung tiến độ 270° ôm mép màn hình đổi màu theo mức khẩn cấp (`>200 m` xanh ngọc → `≤200 m` vàng → `≤50 m` đỏ cam), khung tròn nổi bật cho mũi tên, huy hiệu lối ra vòng xuyến (`LỐI X`), viên thuốc ETA/km (chạm để xem giờ), thanh cài đặt nhanh (vuốt lên / nút `Cài đặt`) lưu bền trên đồng hồ: 4 mức rung (`Phố 150/40m`, `Chuẩn 200/50m`, `Xa 300/80m`, `Tắt rung`) và chế độ màn hình `Sáng` / `Đêm (dịu)`.
- **Phone app (v0.2.0, 46 test method pass, 0 lỗi lint)**: giao diện thẻ tối với mặt đồng hồ thu nhỏ vẽ mũi tên trực tiếp (`ArrowPreviewView`), 2 thẻ telemetry (Đồng hồ S5 / Google Maps), **Quick Settings Tile** bật/tắt 1 chạm từ thanh trạng thái, nút **Dừng HUD** trên thông báo, **tự dừng khi chờ** (`Tắt / 5p / 10p / 15p`), phân loại mũi tên dự phòng bảo thủ từ `largeIcon`, hỗ trợ Google Maps 26.47 Live Updates (`ProgressStyle`, NBSP, `progressMax - progress`), xác nhận notification ongoing còn sống mỗi 3 s (hết báo "MẤT DỮ LIỆU" giả khi dừng đèn đỏ).
- Đã kiểm chứng trên phần cứng thật (**OPPO CPH2873 Android 17 + Xiaomi Watch S5 46mm fw 3.222.007**), chạy song song được với **Mi Fitness**.
- Còn lại: thử nghiệm chuyến đi xe máy thực tế dài hơi (15–30 phút) để đo pin, kiểm wrist-down/AOD dài và thu thêm mẫu Google Maps tiếng Việt (`vi-VN`).
