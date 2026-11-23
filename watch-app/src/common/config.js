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
  // Tuổi nguồn (trường a) vượt ngần này ms cũng coi là mất dữ liệu
  MAX_SOURCE_AGE_MS: 10000,
  // Ngưỡng rung (mét)
  VIBE_FAR_M: 200,
  VIBE_NEAR_M: 50,
  // BLE
  BLE_MTU: 185,
  BLE_FIRST_FRAME_MS: 8000,
  BLE_CONNECT_TIMEOUT_MS: 10000,
  // Ghim địa chỉ BLE của điện thoại (vd 'AA:BB:CC:DD:EE:FF'); '' = nhận máy đầu tiên khớp service
  BLE_PINNED_ADDRESS: '',
  BLE_BACKOFF_MS: [1000, 2000, 4000, 8000, 15000],
  DEMO_STEP_MS: 4000
}
