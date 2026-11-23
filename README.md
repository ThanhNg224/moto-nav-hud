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

## Trạng thái (v0.3.0)

- **Thương hiệu & biểu tượng đồng bộ (`MotoNav`)**: bộ icon đồng nhất cho cả Android Adaptive Icon và Xiaomi Watch S5 (vòng cung tiến độ 270° ngọc lam + mũi tên trắng trên nền tối), câu chữ tiếng Việt thân thiện trên cả 2 thiết bị.
- **Watch app (v0.3.0, 53 test pass)**: giao diện mặt tròn 480×480 AMOLED lấy cảm hứng Beeline Moto / Wear OS — vòng cung tiến độ 270° đổi màu theo ngưỡng khẩn cấp (`>200 m` xanh ngọc → `≤200 m` vàng → `≤50 m` đỏ cam), 14 mũi tên `108×108 px` 1:1 đồng tâm trong vòng tròn nổi bật, huy hiệu lối ra vòng xuyến (`LỐI X`) tách gọn không đè layout, màn chờ có biểu tượng MotoNav + nút `Chạy thử` (1 Hz đếm ngược mượt) & `Cài đặt`, cài đặt nhanh lưu bền trên đồng hồ: 4 mức rung (`Phố / Chuẩn / Xa / Tắt rung`) và 3 chế độ độ sáng (`Tự động` giữ nguyên cảm biến hệ thống / `Ban ngày` / `Ban đêm`).
- **Phone app (v0.3.0, 51 test method pass, 0 lỗi lint)**: **Tự bật khi mở Google Maps** (`AutoStartGate` chống lặp khởi động/chống bật ngược khi người dùng chủ động dừng giữa chuyến), **Tự dừng khi chờ** (`Tắt / 5p / 10p / 15p`), giao diện thẻ tối với mặt đồng hồ thu nhỏ trực tiếp (`ArrowPreviewView`), thẻ nhắc cấp quyền 1 chạm, **Quick Settings Tile** (`MotoNav`), nút **Dừng** trên thông báo, hỗ trợ Google Maps 26.47 Live Updates (`ProgressStyle`, NBSP) và xác nhận notification còn sống mỗi 3 s.
- Đã kiểm chứng trên phần cứng thật (**OPPO CPH2873 Android 17 + Xiaomi Watch S5 46mm fw 3.222.007**), chạy song song ổn định với **Mi Fitness**.
- Còn lại: thử nghiệm chuyến đi xe máy thực tế dài hơi (15–30 phút) để đo pin, kiểm wrist-down dài và thu thêm mẫu Google Maps tiếng Việt (`vi-VN`).
