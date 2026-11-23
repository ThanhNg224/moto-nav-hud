# Brief: Moto Nav HUD cho Xiaomi Watch S5

> Nguồn yêu cầu gốc (người dùng cung cấp). Mọi nghiên cứu phải bám theo file này. Ngày: 2026-11-23.

## Bối cảnh & giới hạn nền tảng (theo người dùng, CẦN KIỂM CHỨNG)

- Thiết bị đích: Xiaomi Watch S5 chạy HyperOS bản tiết kiệm pin (nhân NuttX RTOS + nền tảng Xiaomi Vela), không phải Wear OS.
- Hệ quả (giả định): không cài được `.apk`, không chạy Google Maps SDK trên đồng hồ. Chỉ chạy Mini-app `.rpk` (Xiaomi Vela QuickApp) viết bằng JavaScript / CSS / XML, giới hạn RAM và băng thông BLE.

## Mục tiêu sản phẩm

Biến Watch S5 thành màn hình phụ hiển thị chỉ đường xe máy dạng HUD tương phản cao: mũi tên hướng rẽ, tên đường, khoảng cách đếm ngược, cảnh báo rung. Dữ liệu thời gian thực lấy từ Google Maps trên điện thoại Android.

## Câu hỏi lớn của giai đoạn nghiên cứu

1. Premise có đúng không? (Watch S5 thật sự chạy gì; QuickApp/.rpk có được hỗ trợ cho bên thứ ba; cách dev/sideload/phát hành.)
2. Điện thoại -> đồng hồ: kênh truyền nào khả thi cho app bên thứ ba (Xiaomi Wear / Mi Fitness bridge, interconnect API, BLE trực tiếp...)? Băng thông, độ trễ, độ ổn định nền.
3. Lấy dữ liệu chỉ đường realtime từ Google Maps trên Android bằng cách nào hợp lệ và bền vững (NotificationListener, Navigation SDK, intent, tự định tuyến...)? Rủi ro ToS và độ vỡ khi Google đổi giao diện.
4. Khả năng hiển thị và pin trên đồng hồ: component UI, canvas/SVG, always-on, rung, giữ màn hình sáng, giới hạn RAM.
5. Kiến trúc đề xuất, các spike cần làm trước, rủi ro chặn dự án, câu hỏi cần người dùng quyết định.

## Quy tắc nghiên cứu

- Bằng chứng trước, suy đoán sau. Mỗi khẳng định quan trọng có URL nguồn và độ tin cậy (cao / vừa / thấp). Phân biệt rõ "đã xác minh từ tài liệu chính thức" với "suy luận / tin đồn / diễn đàn".
- Kiểm chứng premise của người dùng, không mặc định đúng. Nếu sai hoặc chỉ đúng một phần, nói thẳng.
- Viết bằng tiếng Việt, giữ nguyên thuật ngữ kỹ thuật tiếng Anh.
- Không chỉnh sửa file ngoài file đầu ra được giao. Không tạo code trong dự án ở giai đoạn này.
- Cuối mỗi báo cáo: "Kết luận ngắn", "Rủi ro chặn", "Câu hỏi còn mở".
