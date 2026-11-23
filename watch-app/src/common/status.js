// Dòng trạng thái ngắn, thân thiện cho người lái (DEBUG_STATUS=false).
function shortStatus(channel, statusC) {
  if (channel) return 'Đã kết nối'
  var s = statusC || ''
  if (s.indexOf('Không có dữ liệu') >= 0 || s.indexOf('im lặng') >= 0) return 'Chờ dữ liệu ĐT'
  if (s.indexOf('không thấy máy') >= 0 || s.indexOf('quét') >= 0 || s === '') return 'Đang tìm ĐT...'
  if (s.indexOf('thử lại') >= 0) return 'Đang nối lại...'
  return 'Đang kết nối...'
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
