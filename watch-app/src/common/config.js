module.exports = {
  // true: hiện đủ dòng chẩn đoán A/C (cho debug S1); false: 1 dòng ngắn cho người lái
  DEBUG_STATUS: false,
  // true: ghi log console có tiền tố [MotoNav] (sự kiện quét/nối/frame/mất tin/rung)
  DEBUG_LOG: true,
  // Mã ghép: phải trùng Prefs của phone-app (tối đa 8 ký tự [A-Za-z0-9]); tin có p khác bị bỏ
  PAIR_TOKEN: 'MOTONAV1',
  // true: bỏ dấu tiếng Việt khỏi tên đường (S5 chưa xác nhận hiển thị được glyph tiếng Việt trong <text>)
  FOLD_DIACRITICS: false,
  // Quá ngần này ms kể từ tin hợp lệ gần nhất (đồng hồ tự đếm) thì báo MẤT DỮ LIỆU
  STALE_MS: 3000,
  // Giới hạn "tươi" của tuổi nguồn (trường a + thời gian đã trôi). Quá mức này, tin k=1 chuyển sang 'yên lặng'
  // (Maps chỉ đăng lại khi chữ đổi nên đoạn đường thẳng dài là bình thường); với k khác 1 đây là ngưỡng MẤT DỮ LIỆU.
  // Chỉ tin có a <= mức này mới được rung/nạp lại rung báo mất.
  MAX_SOURCE_AGE_MS: 10000,
  // k=1: tuổi nguồn vượt ngần này ms mới MẤT DỮ LIỆU thật (banner đỏ + rung 'lost').
  // Phone là bên quyết định: nó giữ k=1 tới 600000 ms khi thông báo Maps còn sống (đèn đỏ lâu) rồi gửi k=0,
  // nên ngưỡng này (11 phút) chỉ là lưới an toàn nằm trên mốc đó.
  NAV_LOST_SOURCE_AGE_MS: 660000,
  // Ngưỡng rung (mét)
  VIBE_FAR_M: 200,
  VIBE_NEAR_M: 50,
  // false: chỉ dùng kênh C (BLE); tắt hẳn vòng poll kênh A (interconnect) để tiết kiệm pin đồng hồ
  ENABLE_INTERCONNECT: false,
  // BLE
  BLE_MTU: 185,
  BLE_FIRST_FRAME_MS: 8000,
  BLE_CONNECT_TIMEOUT_MS: 6000,
  // Ghim địa chỉ BLE của điện thoại (vd 'AA:BB:CC:DD:EE:FF'); '' = nhận máy đầu tiên khớp service
  BLE_PINNED_ADDRESS: '',
  // Backoff ngắn (tối đa 3 s) để nối ngay khi điện thoại vừa bật Google Maps
  BLE_BACKOFF_MS: [500, 1000, 2000, 3000],
  // Mỗi bước demo kéo dài 4 s, phát nhịp 1 s (luôn nhỏ hơn STALE_MS = 3000 để không bao giờ báo MẤT DỮ LIỆU)
  DEMO_STEP_MS: 4000,
  DEMO_TICK_MS: 1000
}
