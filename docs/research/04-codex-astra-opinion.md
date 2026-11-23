# 04 - Ý kiến độc lập của Codex (gpt-6.2-astra, chỉ đọc)

> Nguồn: phản hồi nguyên văn của Codex, ngày 2026-11-23. Nhãn Verified / Guess là của Codex; chưa được đối chiếu với 3 báo cáo Opus.

## Kết luận ngắn

[Guess, cao] Với Watch S5 46 mm bán tại Việt Nam, hãy thử chuyển tiếp thông báo Google Maps qua Mi Fitness trước. Chỉ đầu tư vào HUD tùy biến sau khi cài được `.rpk` và nhận được dữ liệu từ điện thoại trên đúng chiếc đồng hồ mục tiêu. Đổi nguồn chỉ đường không giải quyết được hai điều kiện này.

## Nền tảng

- [Verified, cao] Xiaomi Việt Nam ghi S5 46 mm chạy HyperOS 3 và nói rõ máy không hỗ trợ cài ứng dụng bên thứ ba. Xiaomi cũng liệt kê S5 trong bảng tương thích API của Vela JS. Tài liệu Vela mô tả cài `.rpk` qua bản Mi Fitness dành cho nhà phát triển, cấp qua đầu mối Xiaomi. Chưa xác minh được kênh phát hành công khai cho bản Việt Nam hay kernel NuttX của firmware cụ thể.
  - https://www.mi.com/vn/product/xiaomi-watch-s5-46mm/specs/
  - https://www.mi.com/vn/support/faq/details/KA-708141/
  - https://iot.mi.com/vela/quickapp/zh/features/network/request.html
  - https://iot.mi.com/vela/quickapp/en/guide/other/faq.html
- [Guess, cao] Loại phương án Wear OS/APK và Google Maps SDK chạy trên đồng hồ. Giả định yếu nhất trong brief là người dùng cuối cài được QuickApp riêng.

## Kiến trúc

- [Verified, cao] Cầu nối Xiaomi cho phép QuickApp nhận thông điệp từ app Android qua `interconnect`; hai app phải cùng package name và chữ ký. [Guess, vừa] Với A/B/C2, dùng app Android -> Xiaomi Wear SDK/Mi Fitness -> QuickApp. BLE trực tiếp chưa có bằng chứng cho kênh dữ liệu tùy ý trên S5. https://iot.mi.com/vela/quickapp/en/features/network/interconnect.html
- **A** (NotificationListener đọc thông báo Maps): [Verified, cao] Android cung cấp `NotificationListenerService`. [Guess, vừa] Nếu phải giữ phiên Google Maps hiện tại, đây là cách đáng thử cho HUD riêng. Lọc Maps ngay trên máy, chỉ chuyển trường đọc được. Chưa xác minh schema ổn định cho hướng rẽ, khoảng cách, vòng xuyến. Điều khoản Maps hạn chế sao chép và phân phối lại; quyền đọc notification chưa giải quyết quyền phát hành. https://developer.android.com/reference/android/service/notification/NotificationListenerService · https://www.google.com/help/terms_maps/
- **B** (tự định tuyến: Routes API hoặc Valhalla/OSRM + GPS): [Guess, cao] Điện thoại phải tự xác định vị trí trên tuyến, lúc chuyển bước, tính lại đường. Chỉ chọn nếu chấp nhận bỏ phiên Maps hiện tại và kiểm thử đường cấm xe máy tại địa phương.
- **C1** (chuyển tiếp thông báo Maps qua Mi Fitness): [Verified, cao] S5 nhận thông báo ứng dụng qua Mi Fitness. [Guess, vừa] Kiểm tra từng lần cập nhật cùng một thông báo có tới cổ tay hay không. Đây là phép thử rẻ nhất. https://www.mi.com/global/support/faq/details/KA-703529/
- **C2** (Google Navigation SDK): [Verified, cao] SDK cung cấp hướng rẽ, đường, khoảng cách khoảng mỗi giây cho màn hình phụ, từ phiên do app riêng khởi tạo; Google giới hạn SDK cho ứng dụng thương mại. [Guess, cao] Ưu tiên C2 hơn B nếu đủ điều kiện và cầu nối đã chạy. https://developers.google.com/maps/documentation/navigation/android-sdk/tbt-feed · https://developers.google.com/maps/documentation/navigation/android-sdk/policies

## Rủi ro chặn và spike đề xuất (chưa chạy)

1. Cài đặt, 1 giờ: ghi SKU, firmware, vùng tài khoản; thử mở RPK mẫu bằng kênh dev; thử riêng quy trình người dùng cuối trước khi cam kết phát hành.
2. Mất kết nối, 2 giờ: gửi số thứ tự mỗi giây; khóa điện thoại, tắt màn hình đồng hồ, ngắt/nối Bluetooth; đo độ trễ, kiểm tra mũi tên cũ biến mất.
3. Thiếu dữ liệu, 1 giờ: so thông báo điện thoại với cổ tay qua hai ngã rẽ sát nhau, vòng xuyến, lệch tuyến, kết thúc chuyến.
4. Quyền sử dụng, 1 giờ: đối chiếu mục đích cá nhân/thương mại với điều khoản đúng nguồn.
5. Khó dùng, 1 giờ: ngoài nắng, đeo găng, nổ máy; đo pin, khả năng cảm nhận rung, đọc hướng khi hai tay giữ lái.

## Câu hỏi còn mở

SKU/vùng nào? Dùng riêng hay phát hành? Bắt buộc giữ phiên Google Maps hiện tại?
