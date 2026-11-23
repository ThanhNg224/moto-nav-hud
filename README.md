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

## Trạng thái

- Đã hoàn thiện cả hai phía, vượt qua 4 vòng review Codex sol + 1 lượt xác nhận độc lập: watch-app 51 test pass (build debug/release `.rpk`), phone-app 40 test method pass + lint 0 lỗi (build APK).
- Đã kiểm chứng trên phần cứng thật (**OPPO CPH2873 Android 17 + Xiaomi Watch S5 46mm firmware 3.222.007**):
  - Cài `.rpk` qua Notify for Xiaomi (tên app + icon hiện đúng trong danh sách app đồng hồ).
  - BLE GATT (V3) tự nối ổn định, truyền liên tục >1.000 frame không lỗi/rớt, hiển thị tiếng Việt có dấu, 14 mũi tên (đã chỉnh `Gắt trái`/`Gắt phải`), rung đúng ngưỡng `≤ 200 m` / `≤ 50 m`.
  - Đã thu thông báo Google Maps 26.47 thật chế độ xe máy (`Notification$ProgressStyle` Live Updates, `80\u00a0m`, `Arrive HH:mm`, `progressMax - progress` mét còn lại) và thêm test hồi quy thật.
- Còn lại: kiểm chứng thực tế trên đường thật (nhiều mẫu thông báo tiếng Việt hơn) và dọn dẹp file tạm sau cùng.
