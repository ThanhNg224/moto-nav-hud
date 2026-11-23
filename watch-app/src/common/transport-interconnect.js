// Kênh A: interconnect (phone -> watch qua Mi Fitness). Theo docs: instance(), onopen/onclose/onmessage/onerror, getReadyState.
// Giao diện chung: create(cb[, api]) -> { start(), stop() }
// cb = { onMessage(str) -> bool hợp lệ, onStatus(str), onLost() }
var POLL_MS = 2000

function errText(data, code) {
  var c = code
  if (c == null && data) c = data.code
  return String(c == null ? '?' : c)
}

function create(cb, api) {
  var conn = null
  var timer = null
  var running = false
  var gen = 0
  var up = false // đã từng báo nối

  function status(s) { try { cb.onStatus('A ' + s) } catch (e) {} }
  function lost() { if (up) { up = false; try { cb.onLost() } catch (e) {} } }
  // bọc callback: bỏ nếu instance đã stop/start lại, và nuốt ngoại lệ
  function guard(my, fn) {
    return function () {
      if (!running || my !== gen) return
      try { return fn.apply(null, arguments) } catch (e) { status('ex ' + e) }
    }
  }

  function poll(my) {
    try {
      conn.getReadyState({
        success: guard(my, function (r) {
          if (r && r.status === 1) { up = true; status('nối') } else { lost(); status('chưa nối') }
        }),
        fail: guard(my, function (data, code) { lost(); status('lỗi ' + errText(data, code)) })
      })
    } catch (e) { status('poll ' + e) }
  }

  return {
    start: function () {
      if (running) return
      running = true
      var my = ++gen
      try {
        conn = (api || require('@system.interconnect')).instance()
        conn.onopen = guard(my, function (d) { up = true; status(d && d.isReconnected ? 'nối lại' : 'nối') })
        conn.onclose = guard(my, function (d) { lost(); status('đóng ' + errText(d)) })
        conn.onerror = guard(my, function (d) { lost(); status('lỗi ' + errText(d)) })
        conn.onmessage = guard(my, function (d) { cb.onMessage(d && d.data) })
        status('chờ')
        poll(my)
        timer = setInterval(function () { if (running && my === gen) poll(my) }, POLL_MS)
      } catch (e) {
        status('init ' + e)
      }
    },
    stop: function () {
      running = false
      gen++
      up = false
      if (timer) { clearInterval(timer); timer = null }
      try {
        if (conn) { conn.onopen = null; conn.onclose = null; conn.onerror = null; conn.onmessage = null }
      } catch (e) {}
      conn = null
    }
  }
}

module.exports = { create: create }
