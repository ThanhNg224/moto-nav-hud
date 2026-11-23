# 05 - Review protocol v1 bởi Codex (gpt-6.2-astra, chỉ đọc)

> Nguồn: phản hồi của Codex, 2026-11-23. Mọi điểm Codex gắn nhãn [verified]; chưa kiểm lại từng nguồn. Cách xử lý ghi ở `docs/protocol.md` v1.1.

CRITICAL
1. Heartbeat giữ hướng dẫn cũ tươi mãi. Phải vô hiệu khi mất listener, thông báo biến mất, parse lỗi; heartbeat giữ nguyên tuổi nguồn; đồng hồ tự hết hạn sau 3 giây monotonic; nối lại thì ẩn hướng dẫn.
2. `d=-1` thỏa `d<=30` nên rung gấp khi chưa biết khoảng cách; các bước rẽ liên tiếp giống nhau thiếu định danh; 210 -> 25 m vượt hai ngưỡng. Cần step ID, chỉ rung khi đang dẫn đường, có maneuver và d >= 0; một lần mỗi bước ở ngưỡng khẩn nhất; hủy rung bị thay thế.
3. Tính lại tuyến hoặc đến nơi giữ mũi tên cũ. `k` phải có quyền cao hơn `m`; phân biệt đến nơi với hủy.

HIGH
4. `q` bằng nhau vẫn được nhận nên tin trễ lặp có thể làm mới timer; phone khởi động lại làm `q` thụt lùi. Cần session, `q` tăng nghiêm ngặt, loại trùng và session cũ trước khi làm mới timer.
5. MTU 185 chứa 182 byte giá trị; MTU mặc định 23 chỉ 20 byte. Cần đàm phán MTU và dùng giá trị từng peer, hoặc lõi nén <= 20 byte.
6. 40 ký tự tiếng Việt hoặc dấu nháy thoát ký tự có thể vượt ngân sách; cắt ở ranh giới grapheme, serialize lại, đếm byte.
7. Quảng bá legacy: cờ 3 + UUID 18 + tên `MotoNavHUD` 12 = 33 > 31 byte. UUID ở gói chính, tên ở scan response; kiểm tra advertiser, quyền, kết quả.
8. Chỉ lọc theo UUID có thể nối nhầm điện thoại khác; kết nối không đồng nghĩa nhận notify (cần CCCD, snapshot ngay, một notify bay tại một thời điểm, giữ tin mới nhất tới `onNotificationSent`).
9. S2 thành công không được bỏ qua S3; chưa kiểm chứng BLE cùng lúc với Mi Fitness trên cùng điện thoại. Sau khi cài, khôi phục Mi Fitness và thử từng kênh riêng rồi cùng lúc; đưa thử nghiệm hạ cổ tay/AOD lên sớm. Geolocation nền không cho phép BLE nền.

MEDIUM
10. Echo bỏ qua parser, render, rung. Chạy S4 song song với setup; phát lại fixture Maps tiếng Việt qua cả hai bên (Live Updates, tính lại tuyến, đến nơi, trùng, ngắt kết nối, khởi động lại); cổng: đúng mũi tên, đúng số xung rung, hết hạn 3 giây, hồi phục trong 30 phút khóa máy. Tách lỗi cài đặt với lỗi glyph ở S1.

LOW
11. `t` nhầm múi giờ với đồng bộ đồng hồ. Epoch ms không phụ thuộc múi giờ; dùng RTT monotonic đo truyền, đồng bộ đồng hồ chỉ cho độ trễ một chiều.
