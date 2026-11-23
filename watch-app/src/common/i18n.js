// Chuỗi hiển thị trên đồng hồ: vi (mặc định) + en. t(key, n) thay {n}.
var T = {
  vi: {
    'st.searching': 'Đang tìm ĐT...',
    'st.connecting': 'Đang kết nối...',
    'st.awaitFrame': 'Chờ dữ liệu ĐT',
    'st.connected': 'Đã kết nối',
    'st.retrying': 'Đang nối lại...',
    'st.btOff': 'Bật Bluetooth trên đồng hồ',
    'st.error': 'Lỗi kết nối',
    'st.demoLocal': 'CHẠY THỬ · chạm dừng',
    'st.demoPhone': 'CHẠY THỬ (ĐT)',
    'pre.idle': 'Chờ chỉ đường',
    'pre.noPhone': 'Chưa kết nối',
    'pre.noData': 'Chờ dữ liệu',
    'pre.btOff': 'Bluetooth tắt',
    'pre.error': 'Lỗi kết nối',
    'hint.startMaps': 'Bắt đầu chỉ đường trong Maps',
    'hint.openApp': 'Mở MotoNav trên ĐT và bấm Bắt đầu',
    'hint.btOn': 'Bật Bluetooth trên đồng hồ',
    'hint.stale': 'Kiểm tra điện thoại / Maps',
    'hint.arrived': 'Đã đến điểm đến',
    'hint.recalc': 'Đang tìm tuyến đường mới...',
    'hint.screenOff': 'Màn hình có thể tự tắt',
    'banner.stale': 'MẤT DỮ LIỆU',
    'banner.recalc': 'Đang tính lại',
    'banner.arrived': 'Đã đến',
    'exit.badge': 'LỐI {n}',
    'exit.street': 'Lối {n}',
    'clock': 'Giờ {n}',
    'demo.uturn': 'Quay đầu tại ngã tư',
    'demo.roundabout': 'Vòng xuyến Dân Chủ',
    'demo.start': 'Xuất phát',
    'demo.dest': 'Điểm đến',
    'btn.demo': 'Chạy thử',
    'btn.demoStop': 'Dừng thử',
    'btn.settings': 'Cài đặt',
    'btn.close': 'Đóng',
    'menu.haptics': 'Rung: {n}',
    'preset.std': 'Chuẩn (200/50m)',
    'preset.city': 'Phố (150/40m)',
    'preset.fast': 'Xa (300/80m)',
    'preset.off': 'Tắt rung báo rẽ',
    'bright.auto': 'Độ sáng: Tự động',
    'bright.day': 'Độ sáng: Ban ngày',
    'bright.night': 'Độ sáng: Ban đêm'
  },
  en: {
    'st.searching': 'Searching phone...',
    'st.connecting': 'Connecting...',
    'st.awaitFrame': 'Waiting for phone',
    'st.connected': 'Connected',
    'st.retrying': 'Reconnecting...',
    'st.btOff': 'Turn on watch Bluetooth',
    'st.error': 'Connection error',
    'st.demoLocal': 'DEMO · tap to stop',
    'st.demoPhone': 'PHONE DEMO',
    'pre.idle': 'No route yet',
    'pre.noPhone': 'Not connected',
    'pre.noData': 'No data yet',
    'pre.btOff': 'Bluetooth off',
    'pre.error': 'Connection error',
    'hint.startMaps': 'Start navigation in Maps',
    'hint.openApp': 'Open MotoNav on the phone and tap Start',
    'hint.btOn': 'Turn on watch Bluetooth',
    'hint.stale': 'Check phone / Maps',
    'hint.arrived': 'You have arrived',
    'hint.recalc': 'Finding a new route...',
    'hint.screenOff': 'Screen may time out',
    'banner.stale': 'DATA LOST',
    'banner.recalc': 'Rerouting',
    'banner.arrived': 'Arrived',
    'exit.badge': 'EXIT {n}',
    'exit.street': 'Exit {n}',
    'clock': 'Time {n}',
    'demo.uturn': 'U-turn at the junction',
    'demo.roundabout': 'Dân Chủ roundabout',
    'demo.start': 'Start',
    'demo.dest': 'Destination',
    'btn.demo': 'Demo',
    'btn.demoStop': 'Stop demo',
    'btn.settings': 'Settings',
    'btn.close': 'Close',
    'menu.haptics': 'Haptics: {n}',
    'preset.std': 'Standard (200/50m)',
    'preset.city': 'City (150/40m)',
    'preset.fast': 'Far (300/80m)',
    'preset.off': 'Mute turn haptics',
    'bright.auto': 'Brightness: Auto',
    'bright.day': 'Brightness: Day',
    'bright.night': 'Brightness: Night'
  }
}

var lang = 'vi'

// Đọc ngôn ngữ hệ thống (@system.configuration getLocale). Lỗi/thiếu module/ngôn ngữ khác => 'vi'.
function detect(api) {
  try {
    var c = api || require('@system.configuration')
    var l = String((c.getLocale() || {}).language || '').toLowerCase()
    return l.indexOf('en') === 0 ? 'en' : 'vi'
  } catch (e) { return 'vi' }
}

function setLang(l) { lang = T[l] ? l : 'vi' }
function getLang() { return lang }
function init(api) { setLang(detect(api)); return lang }

function t(key, n) {
  var s = T[lang][key]
  if (s == null) s = T.vi[key]
  if (s == null) return key
  return n == null ? s : s.replace('{n}', n)
}

module.exports = { t: t, init: init, detect: detect, setLang: setLang, getLang: getLang, TABLES: T }
