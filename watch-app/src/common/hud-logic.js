// Logic thuần của HUD (không import framework). Hợp đồng: docs/protocol.md
var DEFAULTS = require('./config.js')

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
    r: o.r == null ? -1 : o.r
  }
}

var RETIRED_MAX = 8

function createState(cfg) {
  return {
    cfg: cfg || DEFAULTS,
    msg: null,
    z: null,
    retired: [],
    lastQ: 0,
    lastRecv: 0,
    lost: false,      // đã mất dữ liệu/ngắt kênh, chưa có tin mới
    clockBack: false, // đồng hồ lùi: coi là mất dữ liệu tới khi có tin mới
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
  return elapsed > state.cfg.STALE_MS || state.msg.a + elapsed > state.cfg.MAX_SOURCE_AGE_MS
}

// Nhận 1 tin đã parse (đã qua parsePayload). now = đồng hồ đồng hồ (ms).
// Trả {accepted, vibe, cancel}: vibe 'far'|'near'|null; cancel = hủy rung đang chờ/chạy.
function accept(state, msg, now) {
  var no = { accepted: false, vibe: null, cancel: false }
  if (!msg) return no
  var cfg = state.cfg
  if (state.retired.indexOf(msg.z) >= 0) return no
  if (msg.z === state.z && msg.q <= state.lastQ) return no
  if (msg.z !== state.z) {
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
  state.lostVibed = false
  state.armed = msg.k === 1
  if (msg.k !== 1 || msg.m === 0 || msg.d < 0) return { accepted: true, vibe: null, cancel: true }
  var stepChanged = msg.i !== state.stepI
  if (stepChanged) { state.stepI = msg.i; state.fired = 0 }
  var level = msg.d <= cfg.VIBE_NEAR_M ? 2 : (msg.d <= cfg.VIBE_FAR_M ? 1 : 0)
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
  state.lost = true
  state.lostVibed = true
  return vibe
}

function emptyView(mode, banner) {
  return { mode: mode, urgency: 'normal', arrow: null, distance: '', banner: banner || '', street: '', eta: '', remain: '', bottom: '' }
}

// Dữ liệu hiển thị. mode: waiting | stale | recalc | nav | arrived
function view(state, now) {
  var cfg = state.cfg
  var m = state.msg
  if (!m && state.lost) return emptyView('stale', 'MẤT DỮ LIỆU')
  if (!m || m.k === 0) return emptyView('waiting', 'Chờ dữ liệu')
  if (stale(state, now)) return emptyView('stale', 'MẤT DỮ LIỆU')
  var street = m.s
  if (cfg.FOLD_DIACRITICS) street = foldDiacritics(street)
  if (m.m === 9 && m.x > 0) street = 'Lối ' + m.x + (street ? ' · ' + street : '')
  var bottom = [m.e, m.r >= 0 ? formatDistance(m.r) : ''].filter(function (v) { return v }).join(' · ')
  var v = emptyView('nav')
  v.street = street
  v.eta = m.e
  v.remain = m.r >= 0 ? formatDistance(m.r) : ''
  v.bottom = bottom
  if (m.k === 2) { v.mode = 'recalc'; v.banner = 'Đang tính lại'; v.street = ''; return v }
  if (m.k === 3) { v.mode = 'arrived'; v.arrow = null; v.street = ''; v.banner = 'Đã đến'; return v }
  v.arrow = arrowAsset(m.m)
  v.distance = formatDistance(m.d)
  if (m.m !== 0 && m.d >= 0) {
    if (m.d <= cfg.VIBE_NEAR_M) v.urgency = 'near'
    else if (m.d <= cfg.VIBE_FAR_M) v.urgency = 'far'
  }
  return v
}

// Chuỗi demo: [m, x, d, s]
var DEMO = [
  [1, 0, 850, 'Nguyễn Văn Linh'], [2, 0, 420, 'Nguyễn Hữu Thọ'], [3, 0, 200, 'Lê Văn Việt'],
  [4, 0, 120, 'Võ Văn Ngân'], [5, 0, 90, 'Phạm Văn Đồng'], [6, 0, 50, 'Trường Chinh'],
  [7, 0, 30, 'Cộng Hòa'], [8, 0, 1500, 'Quay đầu tại ngã tư'], [9, 2, 180, 'Vòng xuyến Dân Chủ'],
  [10, 0, 2300, 'Xa lộ Hà Nội'], [11, 0, 640, 'Mai Chí Thọ'], [13, 0, 0, 'Xuất phát'], [12, 0, 0, 'Điểm đến']
]
function demoMessage(i, q) {
  var a = DEMO[i % DEMO.length]
  return {
    v: 1, z: 1, q: q, i: i, a: 0, p: DEFAULTS.PAIR_TOKEN, k: a[0] === 12 ? 3 : 1, m: a[0], x: a[1], d: a[2], s: a[3],
    e: '12:45', r: 4200 - (i % DEMO.length) * 300
  }
}

module.exports = {
  NAMES: NAMES, arrowAsset: arrowAsset, formatDistance: formatDistance, foldDiacritics: foldDiacritics,
  utf8Decode: utf8Decode, parsePayload: parsePayload, createState: createState, accept: accept, invalidate: invalidate,
  isStale: isStale, tick: tick, view: view, demoMessage: demoMessage, DEMO_LENGTH: DEMO.length
}
