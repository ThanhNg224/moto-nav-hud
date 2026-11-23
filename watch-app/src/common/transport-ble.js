// Kênh C: BLE central (watch scan/connect tới Android peripheral). Theo docs system.bluetooth.ble.
// Giao diện chung: create(cb[, api]) -> { start(), stop() }
// cb = { onMessage(str) -> bool hợp lệ, onStatus(str), onLost() }
var cfg = require('./config.js')
var hud = require('./hud-logic.js')
var L = require('./log.js')

var SERVICE = '6e4f0001-b5a3-f393-e0a9-e50e24dcca9e'
var SKIP_WAIT_MS = 1200
var VFAIL_TTL_MS = 60000   // biến thể quét đã fail được thử lại sau ngần này
var NAV_CHAR = '6e4f0002-b5a3-f393-e0a9-e50e24dcca9e'

function norm(u) { return String(u || '').toLowerCase() }
function same(u, full) {
  var a = norm(u).replace(/^0x/, '')
  return a === full || (a.length >= 4 && a.length <= 8 && full.indexOf(a) === 0)
}
// 16 byte UUID little-endian (thứ tự trong gói quảng bá)
function uuidBytes(full) {
  var hex = full.replace(/-/g, '')
  var out = []
  for (var i = 30; i >= 0; i -= 2) out.push(parseInt(hex.substr(i, 2), 16))
  return out
}
var SVC_BYTES = uuidBytes(SERVICE)

// Biến thể gọi startBLEScan. Mọi biến thể đều tự khớp UUID trong dữ liệu quảng bá; id 0-2 còn có filter hệ thống
var VARIANTS = [
  { id: 0, filters: function () { return [{ serviceUuid: SERVICE }] } },
  { id: 2, filters: function () { return [{ serviceUuid: SERVICE.toUpperCase() }] } },
  { id: 3, filters: function () { return [{}] } },
  { id: 4, filters: function () { return [] } }
]
// V1 (dutyMode options) bị S5 từ chối 202 nên đã bỏ; mặc định V3
var DEFAULT_V = 2

// true nếu dữ liệu quảng bá (ArrayBuffer/Uint8Array; có thể gồm cả scan response nối sau) có cấu trúc AD loại
// 0x06/0x07 (danh sách service UUID 128-bit) chứa UUID của HUD. Duyệt từng trường độ dài-trước; bỏ qua manufacturer data...
function advHasService(data) {
  if (!data) return false
  var b = data instanceof Uint8Array ? data : new Uint8Array(data)
  var i = 0
  while (i < b.length) {
    var len = b[i]
    if (len === 0) { i++; continue }   // đệm 0 giữa advertising và scan response
    if (i + 1 + len > b.length) return false
    var type = b[i + 1]
    if ((type === 0x06 || type === 0x07) && (len - 1) % 16 === 0) {
      for (var p = i + 2; p + 16 <= i + 1 + len; p += 16) {
        var j = 0
        while (j < 16 && b[p + j] === SVC_BYTES[j]) j++
        if (j === 16) return true
      }
    }
    i += len + 1
  }
  return false
}

// Kết quả có được nhận không: bắt buộc có UUID trong cấu trúc AD (S5 không lọc hệ thống; kết quả không dữ liệu bị loại)
function acceptResult(r) {
  return !!(r && r.data && advHasService(r.data))
}

function code(data, c) { return String(c != null ? c : (data && data.code != null ? data.code : '?')) }

function create(cb, api) {
  var ble = api || require('@system.bluetooth.ble')
  var running = false
  var gen = 0 // đổi mỗi attempt; callback của attempt cũ bị bỏ
  var scanner = null
  var subId = null
  var device = null
  var retry = 0
  var retryTimer = null
  var watchdog = null
  var skipTimer = null
  var gotFrame = false
  var skip = []       // địa chỉ đã thất bại ở lần thử trước: ưu tiên máy khác
  var curAddr = ''
  var scanAt = 0
  var curV = DEFAULT_V       // biến thể scan đang dùng; giữ qua các lần thử (biến thể chạy được thành mặc định)
  var vFails = {}    // mã lỗi của biến thể đã fail (hết hạn sau VFAIL_TTL_MS)
  var vFailAt = {}
  var triedV = {}    // biến thể đã chạy hết hạn quét mà không khớp máy nào
  var scanTok = 0
  var seen = 0
  var beginAt = 0
  var attempts = 0
  var phase = ''     // 'scan' khi đang quét (ticker chỉ cập nhật dòng quét)
  var lastWhy = ''
  var matchInfo = ''   // địa chỉ/rssi/addressType của thiết bị ĐÃ KHỚP
  var stHist = []      // các giá trị onBLEConnectionStateChange theo thứ tự (tối đa 6)
  var stAt = 0         // ms từ connect() tới sự kiện cuối
  var ticker = null
  var matched = 0

  function status(s) { try { cb.onStatus('C ' + s) } catch (e) {} }
  function scanText() {
    return 'quét V' + VARIANTS[curV].id + ' ' + seen + '/' + matched + ' t' + Math.floor((Date.now() - beginAt) / 1000) + ' r' + attempts
  }
  function failsText() {
    var o = []
    for (var k = 0; k < VARIANTS.length; k++) if (vFails[VARIANTS[k].id] != null) o.push('V' + VARIANTS[k].id + ':' + vFails[VARIANTS[k].id])
    return o.join(' ')
  }
  // biến thể kế tiếp chưa fail/chưa thử vô ích (vòng); -1 nếu tất cả đã bị loại
  function nextV(cur) {
    for (var i = 1; i <= VARIANTS.length; i++) {
      var v = (cur + i) % VARIANTS.length
      var id = VARIANTS[v].id
      if (vFails[id] == null && !triedV[id]) return v
    }
    return -1
  }
  function resetVariants() { vFails = {}; vFailAt = {}; triedV = {}; curV = DEFAULT_V }
  function expireFails() {
    var now = Date.now()
    for (var k in vFailAt) if (now - vFailAt[k] > VFAIL_TTL_MS) { delete vFails[k]; delete vFailAt[k] }
  }
  function diag() {
    var parts = []
    if (stHist.length) parts.push('st:' + stHist.join(',') + '@' + stAt)
    if (matchInfo) parts.push(matchInfo)
    var f = failsText()
    if (f) parts.push(f)
    if (lastWhy) parts.push('last:' + lastWhy)
    try { if (cb.onDiag) cb.onDiag(parts.join(' ')) } catch (e) {}
  }
  function lostCb() { try { cb.onLost() } catch (e) {} }
  function guard(my, fn) {
    return function () {
      if (!running || my !== gen) return
      try { return fn.apply(null, arguments) } catch (e) { status('ex ' + e) }
    }
  }

  function stopTicker() {
    if (ticker) { clearInterval(ticker); ticker = null }
  }

  function startTicker() {
    stopTicker()
    if (!cfg.DEBUG_STATUS) return
    ticker = setInterval(function () {
      if (running && phase === 'scan') { try { status(scanText()) } catch (e) {} }
    }, 1000)
  }

  function releaseScanner() {
    var sc = scanner, id = subId
    scanner = null
    subId = null
    scanTok++   // vô hiệu hóa mọi callback quét còn treo
    stopTicker()
    if (skipTimer) { clearTimeout(skipTimer); skipTimer = null }
    if (sc) {
      try { if (id != null) sc.unsubscribeBLEDeviceFind(id) } catch (e) {}
      try { sc.stopBLEScan() } catch (e) {}
      try { sc.close() } catch (e) {}
    }
  }

  function releaseDevice() {
    var dev = device
    device = null
    if (dev) {
      try { dev.onBLECharacteristicChange = null; dev.onBLEConnectionStateChange = null } catch (e) {}
      try { dev.disconnect({}) } catch (e) {}
      try { dev.close() } catch (e) {}
    }
  }

  function clearWatchdog() { if (watchdog) { clearTimeout(watchdog); watchdog = null } }

  // Đóng attempt hiện tại: vô hiệu hóa generation TRƯỚC khi giải phóng tài nguyên
  function closeAttempt() {
    gen++
    clearWatchdog()
    releaseScanner()
    releaseDevice()
  }

  function scheduleRetry(why) {
    closeAttempt()
    // Mọi thất bại trước frame đầu tiên: đẩy địa chỉ xuống CUỐI skip (LRU: đầu mảng = thử lâu nhất)
    if (!gotFrame && curAddr) {
      var si = skip.indexOf(curAddr)
      if (si >= 0) skip.splice(si, 1)
      skip.push(curAddr)
      if (skip.length > 4) skip.shift()
    }
    if (gotFrame) { gotFrame = false; lostCb() }
    if (!running) return
    var list = cfg.BLE_BACKOFF_MS
    var wait = list[Math.min(retry, list.length - 1)]
    retry++
    lastWhy = why
    phase = ''
    L.log('retry', why, 'backoff=' + wait + 'ms n=' + retry)
    diag()
    status(why + ' thử lại ' + Math.round(wait / 1000) + 's')
    if (retryTimer) clearTimeout(retryTimer)
    retryTimer = setTimeout(begin, wait)
  }

  function arm(my, ms, why) {
    clearWatchdog()
    watchdog = setTimeout(guard(my, function () {
      scheduleRetry(why)
    }), ms)
  }

  function begin() {
    retryTimer = null
    curAddr = ''
    if (!running) return
    closeAttempt()
    expireFails()
    var my = gen
    try {
      var sc = ble.createScanner()
      scanner = sc
      // callback quét chỉ hợp lệ khi scanner này còn là scanner đang hoạt động (trước khi nhả cho GATT connect)
      var sguard = function (fn) {
        return guard(my, function () {
          if (scanner !== sc) return
          return fn.apply(null, arguments)
        })
      }
      var found = false
      var held = null   // máy đã skip thấy trong scan này: ưu tiên máy thử lâu nhất (chỉ số nhỏ nhất trong skip)
      var heldRank = Infinity
      var choose = function (r) {
        found = true
        curAddr = norm(r.deviceId)
        releaseScanner()
        connect(my, r.deviceId, r.addressType)
      }
      subId = sc.subscribeBLEDeviceFind({
        callback: sguard(function (results) {
          if (found || !results || !results.length) return
          L.count('scan.results', results.length)
          var fresh = null, again = null, againRank = Infinity
          for (var n = 0; n < results.length; n++) {
            seen++
            if (!acceptResult(results[n])) continue
            var id = norm(results[n].deviceId)
            if (cfg.BLE_PINNED_ADDRESS && id !== norm(cfg.BLE_PINNED_ADDRESS)) continue   // bị pin loại: không tính là khớp
            matched++
            if (!matchInfo) {
              matchInfo = String(results[n].deviceId).slice(-5) + '/' + results[n].rssi + '/' + String(results[n].addressType == null ? '?' : results[n].addressType).charAt(0)
              L.log('match', results[n].deviceId, 'rssi=' + results[n].rssi)
              diag()
            }
            if (cfg.BLE_PINNED_ADDRESS) {
              if (!fresh) fresh = results[n]
            } else {
              var rank = skip.indexOf(id)
              if (rank < 0) {
                if (!fresh) fresh = results[n]
              } else if (rank < againRank) {
                again = results[n]
                againRank = rank
              }
            }
          }
          if (cfg.DEBUG_STATUS) status(scanText())
          // ưu tiên máy chưa thử; trong các máy đã skip, xoay theo máy thử lâu nhất (rank nhỏ nhất)
          if (again && againRank < heldRank) { held = again; heldRank = againRank }
          var r = fresh || (held && Date.now() - scanAt >= SKIP_WAIT_MS ? held : null)
          if (r) choose(r)
        }),
        fail: sguard(function (data, c) { scheduleRetry('sub ' + code(data, c)) })
      })
      // Mọi chiến lược đều bắt buộc khớp service UUID trong dữ liệu quảng bá
      matchInfo = ''
      attempts++
      beginAt = Date.now()
      // startBLEScan fail/throw: thử NGAY biến thể kế tiếp (không backoff)
      var launch = function (v) {
        curV = v
        seen = 0
        matched = 0
        held = null
        heldRank = Infinity
        scanAt = Date.now()
        // mỗi biến thể có hạn quét riêng: không khớp máy nào thì đổi biến thể; chỉ thấy máy đã skip thì thử lại chúng
        if (skipTimer) clearTimeout(skipTimer)
        skipTimer = setTimeout(sguard(function () { if (!found && held) choose(held) }), SKIP_WAIT_MS)
        clearWatchdog()
        watchdog = setTimeout(sguard(function () {
          L.log('scan deadline', 'seen=' + seen, 'matched=' + matched)
          if (held) { choose(held); return }
          if (matched === 0) {
            // Chỉ đổi biến thể khi scanner KHÔNG trả về thiết bị nào (seen === 0);
            // nếu seen > 0 nghĩa là scanner hiện tại (V3) vẫn hoạt động tốt, chỉ là điện thoại chưa phát BLE.
            if (seen === 0) {
              triedV[VARIANTS[curV].id] = true
              var nv = nextV(curV)
              if (nv >= 0) curV = nv
              else resetVariants()
            }
            scheduleRetry('không thấy máy (' + seen + ' kq)')
          } else scheduleRetry('chỉ thấy máy đã thử')
        }), cfg.BLE_FIRST_FRAME_MS)
        var id = VARIANTS[v].id
        var tok = ++scanTok
        L.log('scan start', 'V' + id, 'attempt=' + attempts)
        var o = { filters: VARIANTS[v].filters() }
        var failed = function (why) {
          if (tok !== scanTok) return
          vFails[id] = why
          vFailAt[id] = Date.now()
          L.log('scan fail', 'V' + id, why)
          diag()
          var nv = nextV(v)
          if (nv < 0) { resetVariants(); scheduleRetry('scan hết biến thể'); return }
          launch(nv)
          if (my === gen) status(scanText())
        }
        o.fail = sguard(function (data, c) { failed(code(data, c)) })
        try { sc.startBLEScan(o) } catch (e) { failed('ex') }
      }
      launch(curV)
      if (my !== gen) return   // mọi startBLEScan đều lỗi: scanner đã đóng, không báo quét/watchdog
      phase = 'scan'
      startTicker()
      status(scanText())
    } catch (e) {
      scheduleRetry('scan ex ' + e)
    }
  }

  function connect(my, deviceId, addressType) {
    try {
      phase = ''
      status('thấy ' + String(deviceId).slice(-5) + '/' + String(addressType == null ? '?' : addressType).charAt(0))
      var dev = ble.createGattClientDevice(deviceId, addressType || 'UNKNOWN')
      device = dev
      // Thiết lập chỉ bắt đầu khi sự kiện CONNECTED; callback của connect chỉ xác nhận đã gửi lệnh
      var connectAt = Date.now()
      var sawUp = false   // đã thấy state 1 hoặc 2 trong attempt này
      var setupDone = false
      stHist = []
      stAt = 0
      dev.onBLEConnectionStateChange = guard(my, function (state) {
        stHist.push(state)
        if (stHist.length > 6) stHist.shift()
        stAt = Date.now() - connectAt
        L.log('conn state', state, '@' + stAt + 'ms')
        diag()
        if (state === 1) sawUp = true
        else if (state === 2) {
          sawUp = true
          if (!setupDone) { setupDone = true; status('đã nối'); setup(my, dev) }
        } else if (state === 0) {
          // state 0 ban đầu/trung gian (chưa thấy 1/2, <3 s sau connect) bỏ qua, chờ tới timeout nối
          if (!sawUp && stAt < 3000) return
          scheduleRetry('ngắt')
        }
      })
      arm(my, cfg.BLE_CONNECT_TIMEOUT_MS, 'nối quá lâu')
      L.log('connect', deviceId)
      dev.connect({
        success: guard(my, function () { status('đang nối') }),
        fail: guard(my, function (data, c) { scheduleRetry('conn ' + code(data, c)) })
      })
    } catch (e) {
      scheduleRetry('conn ex ' + e)
    }
  }

  function setup(my, dev) {
    arm(my, cfg.BLE_CONNECT_TIMEOUT_MS, 'thiết lập quá lâu')
    var discovered = false
    function discover() {
      if (discovered) return
      discovered = true
      L.log('setup', 'getServices')
      try {
        dev.getServices({
          success: guard(my, subscribe),
          fail: guard(my, function (data, c) { scheduleRetry('svc ' + code(data, c)) })
        })
      } catch (e) { scheduleRetry('svc ex ' + e) }
    }
    function subscribe(services) {
      var ch = null
      for (var i = 0; services && i < services.length && !ch; i++) {
        if (!same(services[i].serviceUuid, SERVICE)) continue
        var cs = services[i].characteristics || []
        for (var j = 0; j < cs.length; j++) if (same(cs[j].characteristicUuid, NAV_CHAR)) { ch = cs[j]; break }
      }
      if (!ch) { scheduleRetry('thiếu char'); return }
      L.log('setup', 'notify enable')
      dev.onBLECharacteristicChange = guard(my, function (c) {
        if (!c || !same(c.characteristicUuid, NAV_CHAR)) return
        var str = hud.utf8Decode(c.characteristicValue)
        if (str == null) { L.count('rx.utf8bad'); return }
        var ok = false
        try { ok = !!cb.onMessage(str) } catch (e) { status('rx ' + e) }
        L.count(ok ? 'rx.ok' : 'rx.drop')
        if (!ok) return
        if (!gotFrame) { gotFrame = true; retry = 0; skip = []; triedV = {}; L.log('first frame', curAddr); status('nối') }
        arm(my, cfg.BLE_FIRST_FRAME_MS, 'BLE im lặng')
      })
      dev.setNotifyCharacteristicChanged({
        characteristic: ch,
        enable: true,
        success: guard(my, function () {
          if (gotFrame) return
          status('chờ tin')
          arm(my, cfg.BLE_FIRST_FRAME_MS, 'Không có dữ liệu BLE (MTU?)')
        }),
        fail: guard(my, function (data, c) { scheduleRetry('notify ' + code(data, c)) })
      })
    }
    // MTU: callback chỉ là xác nhận, không mang giá trị đàm phán. Dù thành công hay lỗi vẫn tiếp tục khám phá.
    try {
      dev.setBLEMtuSize({
        mtu: cfg.BLE_MTU,
        success: guard(my, function () { L.log('setup', 'mtu ok'); status('MTU đã yêu cầu'); discover() }),
        fail: guard(my, function (data, c) { L.log('setup', 'mtu fail', code(data, c)); status('MTU lỗi ' + code(data, c)); discover() })
      })
    } catch (e) { status('MTU ex ' + e); discover() }
  }

  return {
    start: function () {
      if (running) return
      running = true
      retry = 0
      L.log('ble start')
      gotFrame = false
      begin()
    },
    stop: function () {
      L.log('ble stop')
      running = false
      phase = ''
      stopTicker()
      if (retryTimer) { clearTimeout(retryTimer); retryTimer = null }
      closeAttempt()
    }
  }
}

module.exports = { create: create, advHasService: advHasService, acceptResult: acceptResult }
