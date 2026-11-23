// Dòng trạng thái ngắn cho người lái (DEBUG_STATUS=false). Chỉ nói về kênh BLE (kênh A chưa được phone triển khai).
function shortStatus(channel, statusC) {
  if (channel) return 'BLE nối'
  var s = statusC || ''
  if (s.indexOf('Không có dữ liệu') >= 0 || s.indexOf('im lặng') >= 0) return 'BLE chưa có dữ liệu'
  if (s.indexOf('không thấy máy') >= 0 || s.indexOf('quét') >= 0 || s === '') return 'Đang tìm điện thoại'
  if (s.indexOf('thử lại') >= 0) return 'BLE lỗi, đang thử lại'
  return 'Đang nối BLE'
}

// Màu gợi ý cho dòng trạng thái: 'ok' (xanh) | 'warn' (vàng) | 'err' (đỏ cam)
function statusTone(channel, statusC, isDemo) {
  if (isDemo) return 'warn'
  if (channel) return 'ok'
  var s = statusC || ''
  if (s.indexOf('Không có dữ liệu') >= 0 || s.indexOf('im lặng') >= 0 || s.indexOf('thử lại') >= 0) return 'err'
  return 'warn'
}

module.exports = { shortStatus: shortStatus, statusTone: statusTone }
