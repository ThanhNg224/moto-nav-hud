// Logic thuần của HUD (không import framework). Hợp đồng: docs/protocol.md
var DEFAULTS = require('./config.js')
var i18n = require('./i18n.js')

var NAMES = [
  'Không rõ', 'Đi thẳng', 'Chếch trái', 'Rẽ trái', 'Gắt trái', 'Chếch phải', 'Rẽ phải',
  'Gắt phải', 'Quay đầu', 'Vòng xuyến', 'Giữ trái', 'Giữ phải', 'Đã đến', 'Xuất phát'
]

function arrowAsset(m) {
  if (m === 0 || m == null || m < 0 || m > 13) return null
  return '/common/arrows/m' + (m < 10 ? '0' : '') + m + '.png'
}

function formatDistance(d) {
  if (typeof d !== 'number' || d < 0) return '--'
  if (d < 1000) return Math.round(d) + ' m'
  return (d / 1000).toFixed(1) + ' km'
}

var FROM = 'àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ'
var TO = 'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd'
function foldDiacritics(s) {
  var out = ''
  for (var i = 0; i < s.length; i++) {
    var ch = s.charAt(i)
    var lower = ch.toLowerCase()
    var idx = FROM.indexOf(lower)
    if (idx < 0) { out += ch; continue }
    var r = TO.charAt(idx)
    out += ch === lower ? r : r.toUpperCase()
  }
  return out
}

// Giải mã UTF-8 nghiêm ngặt (không phụ thuộc TextDecoder). Trả null nếu byte sai.
function utf8Decode(buf) {
  var b = buf instanceof Uint8Array ? buf : new Uint8Array(buf)
  var s = ''
  var n = b.length
  function cont(j) { return j < n && b[j] >= 0x80 && b[j] <= 0xbf }
  for (var i = 0; i < n;) {
    var c = b[i++]
    var cp
    if (c < 0x80) cp = c
    else if (c >= 0xc2 && c <= 0xdf) {
      if (!cont(i)) return null
      cp = ((c & 0x1f) << 6) | (b[i++] & 0x3f)
    } else if (c >= 0xe0 && c <= 0xef) {
      if (!cont(i) || !cont(i + 1)) return null
      if (c === 0xe0 && b[i] < 0xa0) return null
      if (c === 0xed && b[i] > 0x9f) return null
      cp = ((c & 0x0f) << 12) | ((b[i] & 0x3f) << 6) | (b[i + 1] & 0x3f); i += 2
    } else if (c >= 0xf0 && c <= 0xf4) {
      if (!cont(i) || !cont(i + 1) || !cont(i + 2)) return null
      if (c === 0xf0 && b[i] < 0x90) return null
      if (c === 0xf4 && b[i] > 0x8f) return null
      cp = ((c & 0x07) << 18) | ((b[i] & 0x3f) << 12) | ((b[i + 1] & 0x3f) << 6) | (b[i + 2] & 0x3f); i += 3
    } else return null
    if (cp > 0xffff) {
      cp -= 0x10000
      s += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff))
    } else s += String.fromCharCode(cp)
  }
  return s
}

function isInt(v) { return typeof v === 'number' && isFinite(v) && Math.floor(v) === v && Math.abs(v) <= 9007199254740991 }

// Trả về object chuẩn hóa hoặc null nếu sai hợp đồng/miền giá trị/mã ghép. Không đổi trạng thái nào.
function parsePayload(input, cfg) {
  var token = (cfg || DEFAULTS).PAIR_TOKEN
  var o = input
  if (typeof input === 'string') {
    try { o = JSON.parse(input) } catch (e) { return null }
  }
  if (!o || typeof o !== 'object') return null
  if (o.v !== 1) return null
  if (o.p !== token) return null
  if (!isInt(o.z) || !isInt(o.q) || o.q < 1 || !isInt(o.i) || !isInt(o.a) || o.a < 0) return null
  if (!isInt(o.k) || o.k < 0 || o.k > 3) return null
  if (!isInt(o.m) || o.m < 0 || o.m > 13) return null
  if (!isInt(o.x) || o.x < 0 || !isInt(o.d) || o.d < -1) return null
  if (o.r != null && (!isInt(o.r) || o.r < -1)) return null
  return {
    z: o.z, q: o.q, i: o.i, a: o.a, k: o.k, m: o.m, x: o.x, d: o.d,
    s: typeof o.s === 'string' ? o.s : '',
    e: typeof o.e === 'string' ? o.e : '',
    r: o.r == null ? -1 : o.r,
    y: o.y === 1 ? 1 : 0   // 1 = khung chạy thử do điện thoại phát (tùy chọn; vắng = 0)
  }
}

var HAPTIC_PRESETS = {
  std: { far: 200, near: 50 },
  city: { far: 150, near: 40 },
  fast: { far: 300, near: 80 },
  off: { far: -1, near: -1 }   // chỉ tắt rung báo rẽ; xung mất dữ liệu ('lost') vẫn chạy
}
function presetLabel(key) { return i18n.t('preset.' + (HAPTIC_PRESETS[key] ? key : 'std')) }
var PRESET_ORDER = ['std', 'city', 'fast', 'off']

function applyPreset(state, presetKey) {
  var p = HAPTIC_PRESETS[presetKey] || HAPTIC_PRESETS.std
  var next = Object.assign({}, state.cfg, { VIBE_FAR_M: p.far, VIBE_NEAR_M: p.near })
  state.cfg = next
  return presetKey in HAPTIC_PRESETS ? presetKey : 'std'
}

var RETIRED_MAX = 8

function createState(cfg) {
  return {
    cfg: Object.assign({}, cfg || DEFAULTS),
    msg: null,
    z: null,
    retired: [],
    lastQ: 0,
    lastRecv: 0,
    lost: false,      // đã mất dữ liệu/ngắt kênh, chưa có tin mới
    clockBack: false, // đồng hồ lùi: coi là mất dữ liệu tới khi có tin mới
    srcLost: false,   // tuổi nguồn đã vượt ngưỡng mất: giữ tới khi có tin TƯƠI (a <= MAX_SOURCE_AGE_MS), đổi phiên hoặc ngắt kênh
    lostVibed: false,
    armed: false,     // chỉ rung mất dữ liệu sau khi đã nhận 1 tin dẫn đường còn tươi
    high: 0,          // mốc đồng hồ cao nhất từ tin chấp nhận gần nhất
    stepI: null,
    fired: 0 // 0 chưa rung, 1 đã rung 'far', 2 đã rung 'near'
  }
}

function stale(state, now) {
  if (!state.msg) return false
  if (now < state.high) state.clockBack = true
  else state.high = now
  if (state.clockBack) return true
  var elapsed = now - state.lastRecv
  var limit = state.msg.k === 1 ? state.cfg.NAV_LOST_SOURCE_AGE_MS : state.cfg.MAX_SOURCE_AGE_MS
  // Chỉ chốt khi liên kết còn khỏe (elapsed <= STALE_MS): nếu không, ước lượng không đáng tin (vd đồng hồ nhảy) và BLE im lặng đã báo stale (không chốt)
  if (state.msg.a + elapsed > limit && elapsed <= state.cfg.STALE_MS) state.srcLost = true
  return state.srcLost || elapsed > state.cfg.STALE_MS
}

// k=1 còn kết nối nhưng nguồn Maps đã >MAX_SOURCE_AGE_MS chưa đăng lại: giữ hướng dẫn, tắt cảnh báo/rung
function quiet(state, now) {
  var m = state.msg
  return !!m && m.k === 1 && !stale(state, now) && m.a + (now - state.lastRecv) > state.cfg.MAX_SOURCE_AGE_MS
}

// Nhận 1 tin đã parse (đã qua parsePayload). now = đồng hồ đồng hồ (ms).
// Trả {accepted, vibe, cancel}: vibe 'far'|'near'|null; cancel = hủy rung đang chờ/chạy.
function accept(state, msg, now) {
  var no = { accepted: false, vibe: null, cancel: false }
  if (!msg) return no
  var cfg = state.cfg
  if (state.retired.indexOf(msg.z) >= 0) return no
  if (msg.z === state.z && msg.q <= state.lastQ) return no
  if (state.msg) {
    stale(state, now)   // chốt mất do tuổi nguồn của tin cũ trước khi ghi đè (heartbeat trễ không được khôi phục)
    if (state.msg.k !== msg.k) state.srcLost = false   // đổi k = chuyển trạng thái thật: tin mới xét theo ngưỡng tuổi riêng
  }
  if (msg.z !== state.z) {
    state.srcLost = false
    if (state.z !== null) {
      state.retired.push(state.z)
      if (state.retired.length > RETIRED_MAX) state.retired.shift()
    }
    state.z = msg.z
    state.stepI = null
    state.fired = 0
  }
  state.lastQ = msg.q
  state.lastRecv = now
  state.high = now
  state.msg = msg
  state.lost = false
  state.clockBack = false
  if (msg.a > cfg.MAX_SOURCE_AGE_MS) return { accepted: true, vibe: null, cancel: true }
  state.srcLost = false
  state.lostVibed = false
  state.armed = msg.k === 1
  if (msg.k !== 1 || msg.m === 0 || msg.d < 0) return { accepted: true, vibe: null, cancel: true }
  var stepChanged = msg.i !== state.stepI
  if (stepChanged) { state.stepI = msg.i; state.fired = 0 }
  if (cfg.VIBE_FAR_M < 0 && cfg.VIBE_NEAR_M < 0) return { accepted: true, vibe: null, cancel: stepChanged }
  var level = (cfg.VIBE_NEAR_M >= 0 && msg.d <= cfg.VIBE_NEAR_M) ? 2 : ((cfg.VIBE_FAR_M >= 0 && msg.d <= cfg.VIBE_FAR_M) ? 1 : 0)
  if (level > state.fired) {
    state.fired = level
    return { accepted: true, vibe: level === 2 ? 'near' : 'far', cancel: false }
  }
  return { accepted: true, vibe: null, cancel: stepChanged }
}

function isStale(state, now) { return stale(state, now) }

// Gọi định kỳ; trả 'lost' đúng 1 lần mỗi đợt mất dữ liệu (chỉ khi tin cuối là k=1)
function tick(state, now) {
  if (stale(state, now) && !state.lostVibed) {
    state.lostVibed = true
    var armed = state.armed && state.msg.k === 1
    state.armed = false
    return armed ? 'lost' : null
  }
  return null
}

// Kênh ngắt: xóa hướng dẫn ngay, giữ z/q để tin trùng không khôi phục. Trả 'lost' nếu cần rung.
function invalidate(state) {
  if (!state.msg) return null
  var vibe = state.armed && state.msg.k === 1 && !state.lostVibed ? 'lost' : null
  state.armed = false
  state.msg = null
  state.srcLost = false
  state.lost = true
  state.lostVibed = true
  return vibe
}

function emptyView(mode, banner) {
  return {
    mode: mode, urgency: 'normal', arcPercent: 0,
    arrow: null, exitBadge: '', distance: '', banner: banner || '', quiet: false, quietSec: 0,
    street: '', road: '', eta: '', remain: '', bottom: ''
  }
}

// Dữ liệu hiển thị. mode: waiting | stale | recalc | nav | arrived
function viewCore(state, now) {
  var cfg = state.cfg
  var m = state.msg
  if (!m && state.lost) { var s0 = emptyView('stale', i18n.t('banner.stale')); s0.arcPercent = 100; return s0 }
  if (!m) return emptyView('waiting', i18n.t('pre.idle'))
  if (stale(state, now)) { var s1 = emptyView('stale', i18n.t('banner.stale')); s1.arcPercent = 100; return s1 }
  if (m.k === 0) return emptyView('waiting', i18n.t('pre.idle'))
  var road = m.s
  if (cfg.FOLD_DIACRITICS) road = foldDiacritics(road)
  var street = road
  var exitBadge = ''
  if (m.m === 9 && m.x > 0) {
    exitBadge = i18n.t('exit.badge', m.x)
    street = i18n.t('exit.street', m.x) + (road ? ' · ' + road : '')
  }
  var bottom = [m.e, m.r >= 0 ? formatDistance(m.r) : ''].filter(function (v) { return v }).join(' · ')
  var v = emptyView('nav')
  v.road = road
  v.street = street
  v.exitBadge = exitBadge
  v.eta = m.e
  v.remain = m.r >= 0 ? formatDistance(m.r) : ''
  v.bottom = bottom
  if (m.k === 2) { v.mode = 'recalc'; v.banner = i18n.t('banner.recalc'); v.arcPercent = 100; v.street = ''; v.road = ''; v.exitBadge = ''; return v }
  if (m.k === 3) { v.mode = 'arrived'; v.arrow = null; v.street = ''; v.road = ''; v.exitBadge = ''; v.banner = i18n.t('banner.arrived'); v.arcPercent = 100; return v }
  v.arrow = arrowAsset(m.m)
  v.distance = formatDistance(m.d)
  if (quiet(state, now)) {
    v.quiet = true
    v.quietSec = Math.floor((m.a + now - state.lastRecv) / 1000)
    return v
  }
  if (m.d >= 0) {
    // Vòng cung tiến gần điểm rẽ: 500 m -> 6%, 0 m -> 100%
    v.arcPercent = Math.round(Math.max(6, Math.min(100, (1 - m.d / 500) * 100)))
  }
  if (m.m !== 0 && m.d >= 0) {
    var nearM = cfg.VIBE_NEAR_M >= 0 ? cfg.VIBE_NEAR_M : DEFAULTS.VIBE_NEAR_M
    var farM = cfg.VIBE_FAR_M >= 0 ? cfg.VIBE_FAR_M : DEFAULTS.VIBE_FAR_M
    if (m.d <= nearM) v.urgency = 'near'
    else if (m.d <= farM) v.urgency = 'far'
  }
  return v
}

function view(state, now) {
  var v = viewCore(state, now)
  v.phoneDemo = !!(state.msg && state.msg.y === 1 && v.mode !== 'stale' && v.mode !== 'waiting')   // khung gần nhất do ĐT chạy thử
  return v
}

// Lớp phủ Cài đặt không được che hướng dẫn trực tiếp.
// ctx: {mode, routeActive (đã có hướng dẫn từ lần chờ gần nhất), pulseActive, vibe, menuOpen}
function isGuiding(mode) { return mode === 'nav' || mode === 'recalc' || mode === 'arrived' }
// Chỉ cho mở khi không hướng dẫn trực tiếp, không mất dữ liệu giữa tuyến và không có xung rung đang chạy
function canOpenMenu(c) {
  return !isGuiding(c.mode) && !(c.mode === 'stale' && c.routeActive) && !c.pulseActive
}
// Cờ "đã có tuyến thật": chỉ hướng dẫn thật (không chạy thử cục bộ/ĐT) bật; màn chờ tắt; stale giữ nguyên
function nextRouteActive(prev, mode, demo, phoneDemo) {
  if (isGuiding(mode)) return (!demo && !phoneDemo) ? true : prev
  return mode === 'waiting' ? false : prev
}
function shouldCloseMenu(c) {
  return !!c.menuOpen && (!!c.vibe || !canOpenMenu(c))
}
// Có hướng dẫn thật còn tươi (k 1..3, chưa stale): không cho chạy thử cục bộ đè lên
function hasFreshGuidance(state, now) {
  return !!(state.msg && state.msg.k >= 1 && !stale(state, now))
}

// Chuỗi demo: [m, x, d, s]
var DEMO = [
  [1, 0, 850, 'Nguyễn Văn Linh'], [2, 0, 420, 'Nguyễn Hữu Thọ'], [3, 0, 200, 'Lê Văn Việt'],
  [4, 0, 120, 'Võ Văn Ngân'], [5, 0, 90, 'Phạm Văn Đồng'], [6, 0, 50, 'Trường Chinh'],
  [7, 0, 30, 'Cộng Hòa'], [8, 0, 1500, 'demo.uturn'], [9, 2, 180, 'demo.roundabout'],
  [10, 0, 2300, 'Xa lộ Hà Nội'], [11, 0, 640, 'Mai Chí Thọ'], [13, 0, 250, 'demo.start'], [12, 0, 0, 'demo.dest']
]
function demoMessage(i, q, subTick, ticksPerStep) {
  var a = DEMO[i % DEMO.length]
  var isArrived = a[0] === 12
  var d = a[2]
  if (!isArrived && ticksPerStep > 1) {
    var frac = Math.max(0, Math.min(1, (subTick || 0) / (ticksPerStep - 1)))
    // Với bước rẽ gần (<= 300 m), đếm ngược mượt từ ~260 m xuống ~30 m để vòng cung tiến độ & ngưỡng rung hoạt động đầy đủ
    var startD = a[2] <= 300 ? Math.max(a[2], 260) : a[2]
    var endD = a[2] <= 300 ? Math.min(a[2], 30) : Math.round(a[2] * 0.7)
    d = Math.round(startD + (endD - startD) * frac)
  }
  return {
    v: 1, z: 1, q: q, i: i, a: 0, p: DEFAULTS.PAIR_TOKEN, k: isArrived ? 3 : 1, m: a[0], x: a[1], d: d, s: a[3].indexOf('demo.') === 0 ? i18n.t(a[3]) : a[3],
    e: '12:45', r: Math.max(0, 4200 - (i % DEMO.length) * 300)
  }
}

module.exports = {
  NAMES: NAMES, HAPTIC_PRESETS: HAPTIC_PRESETS, presetLabel: presetLabel, shouldCloseMenu: shouldCloseMenu, canOpenMenu: canOpenMenu, nextRouteActive: nextRouteActive, hasFreshGuidance: hasFreshGuidance, isGuiding: isGuiding, PRESET_ORDER: PRESET_ORDER, applyPreset: applyPreset,
  arrowAsset: arrowAsset, formatDistance: formatDistance, foldDiacritics: foldDiacritics,
  utf8Decode: utf8Decode, parsePayload: parsePayload, createState: createState, accept: accept, invalidate: invalidate,
  isStale: isStale, quiet: quiet, tick: tick, view: view, demoMessage: demoMessage, DEMO_LENGTH: DEMO.length
}
