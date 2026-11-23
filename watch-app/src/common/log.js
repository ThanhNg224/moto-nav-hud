// Logger gọn: console.log với tiền tố ổn định. Bật/tắt bằng cfg.DEBUG_LOG. Chỉ ghi địa chỉ BLE, không dữ liệu nhạy cảm.
var cfg = require('./config.js')
var PREFIX = '[MotoNav] '
var counts = {}
var lastFlush = 0

function log() {
  if (!cfg.DEBUG_LOG) return
  try { console.log(PREFIX + Array.prototype.join.call(arguments, ' ')) } catch (e) {}
}

// Đếm sự kiện lặp (frame...), gộp thành tối đa 1 dòng mỗi 10 giây để tránh spam log trên đồng hồ
function count(name, n) {
  if (!cfg.DEBUG_LOG) return
  counts[name] = (counts[name] || 0) + (n == null ? 1 : n)
  var now = Date.now()
  if (now - lastFlush < 10000) return
  lastFlush = now
  var o = []
  for (var k in counts) o.push(k + '=' + counts[k])
  counts = {}
  log('counts', o.join(' '))
}

module.exports = { log: log, count: count }
