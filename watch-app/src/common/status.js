// Pha kết nối (transport-ble phát) -> trình bày cho người lái. tone: 'ok' | 'warn' | 'err'
var i18n = require('./i18n.js')

var PHASE = {
  SEARCHING: 'SEARCHING',
  CONNECTING: 'CONNECTING',
  AWAIT_FRAME: 'AWAIT_FRAME',
  CONNECTED_IDLE: 'CONNECTED_IDLE',
  RETRYING: 'RETRYING',
  BT_OFF: 'BT_OFF',
  ERROR: 'ERROR'
}
// Mã lỗi Vela: 10001 = Bluetooth đang tắt trên hệ thống (iot.mi.com/vela/quickapp/en/features/system/bluetooth.html)
var BT_OFF_CODE = '10001'

// pre: banner/hint màn chờ; bannerTone 'wait' (trắng) | 'err' (đỏ)
var TABLE = {
  SEARCHING: { text: 'st.searching', tone: 'warn', pulse: true, banner: 'pre.noPhone', hint: 'hint.openApp', bannerTone: 'wait' },
  CONNECTING: { text: 'st.connecting', tone: 'warn', pulse: true, banner: 'pre.noPhone', hint: 'hint.openApp', bannerTone: 'wait' },
  AWAIT_FRAME: { text: 'st.awaitFrame', tone: 'warn', pulse: true, banner: 'pre.noData', hint: 'hint.openApp', bannerTone: 'wait' },
  CONNECTED_IDLE: { text: 'st.connected', tone: 'ok', pulse: false, banner: 'pre.idle', hint: 'hint.startMaps', bannerTone: 'wait' },
  RETRYING: { text: 'st.retrying', tone: 'warn', pulse: true, banner: 'pre.noPhone', hint: 'hint.openApp', bannerTone: 'wait' },
  BT_OFF: { text: 'st.btOff', tone: 'err', pulse: false, banner: 'pre.btOff', hint: 'hint.btOn', bannerTone: 'err' },
  ERROR: { text: 'st.error', tone: 'err', pulse: false, banner: 'pre.error', hint: 'hint.openApp', bannerTone: 'err' }
}

function row(phase) { return TABLE[phase] || TABLE.SEARCHING }

// Chỉ tin hợp lệ (channel) mới thiết lập "đã kết nối"; transport báo CONNECTED_IDLE mà chưa có channel coi như chờ dữ liệu
function effectivePhase(channel, phase) {
  if (channel) return PHASE.CONNECTED_IDLE
  return phase === PHASE.CONNECTED_IDLE ? PHASE.AWAIT_FRAME : (TABLE[phase] ? phase : PHASE.SEARCHING)
}

function present(phase) {
  var r = row(phase)
  return { text: i18n.t(r.text), tone: r.tone, pulse: r.pulse }
}

function preRide(phase) {
  var r = row(phase)
  return { banner: i18n.t(r.banner), hint: i18n.t(r.hint), bannerTone: r.bannerTone }
}

module.exports = { PHASE: PHASE, BT_OFF_CODE: BT_OFF_CODE, effectivePhase: effectivePhase, present: present, preRide: preRide }
