const test = require('node:test')
const assert = require('node:assert')
const fs = require('node:fs')
const h = require('../src/common/hud-logic.js')
const bleT = require('../src/common/transport-ble.js')
const i18n = require('../src/common/i18n.js')
const S = require('../src/common/status.js')

const base = { v: 1, p: 'MOTONAV1', z: 100, q: 1, i: 1, a: 0, k: 1, m: 3, x: 0, d: 500, s: 'Nguyễn Văn Linh', e: '12:45', r: 4200 }
const msg = (o) => h.parsePayload(Object.assign({}, base, o))
const SVC = '6e4f0001-b5a3-f393-e0a9-e50e24dcca9e'
const CH = '6e4f0002-b5a3-f393-e0a9-e50e24dcca9e'
const adv = () => {
  const u = SVC.replace(/-/g, '').match(/../g).reverse().map((x) => parseInt(x, 16))
  return new Uint8Array([2, 1, 6, 17, 7, ...u]).buffer
}
const enc = (o) => new TextEncoder().encode(JSON.stringify(o)).buffer

function mockBle() {
  const m = { dev: null }
  m.api = {
    createScanner() {
      return {
        subscribeBLEDeviceFind(o) { m.onFound = o.callback; return 1 },
        startBLEScan(o) { m.scanOpts = o },
        stopBLEScan() {}, unsubscribeBLEDeviceFind() {}, close() {}
      }
    },
    createGattClientDevice() {
      m.dev = {
        connect(o) { m.connectOpts = o },
        setBLEMtuSize(o) { m.mtuOpts = o },
        getServices(o) { m.svcOpts = o },
        setNotifyCharacteristicChanged(o) { m.notifyOpts = o },
        disconnect() {}, close() {}
      }
      return m.dev
    }
  }
  return m
}
function rig(t, onMessage) {
  try { t.mock.timers.enable({ apis: ['setTimeout'] }) } catch (e) {}
  const m = mockBle(); const ph = []
  const tr = bleT.create({ onMessage: onMessage || (() => true), onStatus() {}, onLost() {}, onPhase: (p, c) => ph.push(p + (c ? ':' + c : '')) }, m.api)
  return { m, ph, tr }
}
function toSubscribed(m) {
  m.onFound([{ deviceId: 'AA:BB', data: adv() }])
  m.dev.onBLEConnectionStateChange(2)
  m.mtuOpts.success({})
  m.svcOpts.success([{ serviceUuid: SVC, characteristics: [{ characteristicUuid: CH }] }])
}

test('parse y: tùy chọn, y=1 giữ, khác => 0; không loại tin cũ', () => {
  assert.strictEqual(msg({}).y, 0)
  assert.strictEqual(msg({ y: 1 }).y, 1)
  assert.strictEqual(msg({ y: 0 }).y, 0)
  assert.strictEqual(msg({ y: 'x' }).y, 0)
  assert.ok(msg({ y: 2 }))
})

test('view.phoneDemo: theo khung chấp nhận gần nhất, tắt khi stale/mất tin', () => {
  const st = h.createState()
  h.accept(st, msg({ q: 1, y: 1 }), 0)
  assert.strictEqual(h.view(st, 100).phoneDemo, true)
  assert.strictEqual(h.view(st, 3500).phoneDemo, false) // stale
  h.accept(st, msg({ q: 2 }), 4000)
  assert.strictEqual(h.view(st, 4100).phoneDemo, false)
  h.accept(st, msg({ q: 3, y: 1 }), 4200)
  assert.strictEqual(h.view(st, 4300).phoneDemo, true)
  h.invalidate(st)
  assert.strictEqual(h.view(st, 4400).phoneDemo, false)
})

test('recalc có vòng cung đầy (ring-recalc hiển thị được), không như idle', () => {
  const st = h.createState()
  h.accept(st, msg({ k: 2 }), 0)
  const v = h.view(st, 0)
  assert.strictEqual(v.mode, 'recalc')
  assert.strictEqual(v.arcPercent, 100)
})

test('preset "off": nhãn mới; xung mất dữ liệu vẫn chạy khi timeout và khi ngắt rõ ràng', () => {
  assert.strictEqual(i18n.getLang(), 'vi')
  assert.strictEqual(h.presetLabel('off'), 'Tắt rung báo rẽ')
  const mk = () => { const st = h.createState(); h.applyPreset(st, 'off'); return st }
  let st = mk()
  const r = h.accept(st, msg({ q: 1, d: 30 }), 0)
  assert.strictEqual(r.vibe, null) // không rung báo rẽ
  assert.strictEqual(h.tick(st, 3001), 'lost') // timeout
  assert.strictEqual(h.tick(st, 3500), null) // chỉ 1 lần
  st = mk()
  h.accept(st, msg({ q: 1, d: 30 }), 0)
  assert.strictEqual(h.invalidate(st), 'lost') // ngắt rõ ràng
})

test('canOpenMenu: chỉ khi không hướng dẫn, không mất tin giữa tuyến, không có xung rung', () => {
  const o = (c) => h.canOpenMenu(Object.assign({ mode: 'waiting', routeActive: false, pulseActive: false }, c))
  assert.strictEqual(o({}), true)
  assert.strictEqual(o({ mode: '' }), true)
  assert.strictEqual(o({ mode: 'nav' }), false)
  assert.strictEqual(o({ mode: 'recalc' }), false)
  assert.strictEqual(o({ mode: 'arrived' }), false)
  assert.strictEqual(o({ mode: 'stale', routeActive: true }), false)
  assert.strictEqual(o({ mode: 'stale', routeActive: false }), true)
  assert.strictEqual(o({ pulseActive: true }), false)
})

test('shouldCloseMenu: menu mở bị đóng khi hướng dẫn / stale giữa tuyến / rung; frame sau không giữ menu mở', () => {
  const c = (o) => h.shouldCloseMenu(Object.assign({ menuOpen: true, mode: 'waiting', routeActive: false, pulseActive: false, vibe: null }, o))
  assert.strictEqual(c({ menuOpen: false, mode: 'nav', vibe: 'far' }), false)
  assert.strictEqual(c({}), false)
  assert.strictEqual(c({ mode: 'nav' }), true)
  assert.strictEqual(c({ mode: 'nav' }), true) // frame kế tiếp, mode/urgency không đổi: vẫn đóng
  assert.strictEqual(c({ mode: 'recalc' }), true)
  assert.strictEqual(c({ mode: 'arrived' }), true)
  assert.strictEqual(c({ mode: 'stale', routeActive: true }), true)
  assert.strictEqual(c({ mode: 'stale' }), false)
  assert.strictEqual(c({ pulseActive: true }), true)
  assert.strictEqual(c({ vibe: 'lost' }), true)
})

test('nextRouteActive: chỉ hướng dẫn thật khóa menu khi mất tin; chạy thử (ĐT/cục bộ) không', () => {
  const n = h.nextRouteActive
  assert.strictEqual(n(false, 'nav', false, false), true)
  assert.strictEqual(n(false, 'nav', false, true), false)   // chạy thử từ ĐT
  assert.strictEqual(n(false, 'nav', true, false), false)   // chạy thử cục bộ
  assert.strictEqual(n(false, 'stale', false, false), false) // mất tin sau chạy thử ĐT: không khóa
  assert.strictEqual(n(true, 'stale', false, false), true)   // mất tin giữa tuyến thật: giữ khóa
  assert.strictEqual(n(true, 'waiting', false, false), false)
  assert.strictEqual(n(true, 'nav', true, false), true)      // chạy thử không xóa cờ cũ
  assert.ok(h.canOpenMenu({ mode: 'stale', routeActive: n(false, 'stale', false, false), pulseActive: false }))
})

test('haptics.active: start trễ, stop thất bại, cancel (đồng hồ giả)', (t) => {
  t.mock.timers.enable({ apis: ['Date', 'setTimeout'] })
  const haptics = require('../src/common/haptics.js')
  let startOpts = null; let stopOk = true
  const api = { start(o) { startOpts = o }, stop() { return stopOk }, vibrate() {} }
  const hp = haptics.create(api)
  assert.strictEqual(hp.active(), false)
  // start trễ: chưa có id vẫn active; hạn tính từ lúc success
  hp.play('near') // 500*3+400*2 = 2300 ms
  assert.strictEqual(hp.active(), true)
  t.mock.timers.tick(2000)
  assert.strictEqual(hp.active(), true) // vẫn chờ id
  startOpts.success({ id: 1 }) // về trễ 2000 ms
  t.mock.timers.tick(2500)
  assert.strictEqual(hp.active(), true) // hạn mới = success + 2300 + 250
  t.mock.timers.tick(100)
  assert.strictEqual(hp.active(), false)
  // stop thành công: hết active ngay
  hp.play('lost')
  startOpts.success({ id: 2 })
  assert.strictEqual(hp.active(), true)
  hp.cancel()
  assert.strictEqual(hp.active(), false)
  // stop thất bại: task còn active tới hạn
  hp.play('lost')
  startOpts.success({ id: 3 })
  stopOk = false
  hp.cancel()
  assert.strictEqual(hp.active(), true)
  t.mock.timers.tick(849)
  assert.strictEqual(hp.active(), true)
  t.mock.timers.tick(2)
  assert.strictEqual(hp.active(), false)
})

test('hasFreshGuidance: chặn chạy thử cục bộ khi có hướng dẫn thật còn tươi', () => {
  const st = h.createState()
  assert.strictEqual(h.hasFreshGuidance(st, 0), false)
  h.accept(st, msg({ k: 0 }), 0)
  assert.strictEqual(h.hasFreshGuidance(st, 100), false) // idle
  h.accept(st, msg({ q: 2, k: 1 }), 200)
  assert.strictEqual(h.hasFreshGuidance(st, 300), true)
  assert.strictEqual(h.hasFreshGuidance(st, 3300), false) // stale: được chạy thử
  h.accept(st, msg({ q: 3, k: 2 }), 4000)
  assert.strictEqual(h.hasFreshGuidance(st, 4100), true) // recalc cũng là hướng dẫn
  h.invalidate(st)
  assert.strictEqual(h.hasFreshGuidance(st, 4200), false)
})

test('phoneDemo không hiện ở màn chờ (khung idle y=1)', () => {
  const st = h.createState()
  h.accept(st, msg({ k: 0, y: 1 }), 0)
  const v = h.view(st, 10)
  assert.strictEqual(v.mode, 'waiting')
  assert.strictEqual(v.phoneDemo, false)
})

test('demo tổng hợp được địa phương hóa; tên đường thật giữ nguyên', () => {
  const find = (m) => { for (let i = 0; i < h.DEMO_LENGTH; i++) { const x = h.demoMessage(i, 1, 0, 1); if (x.m === m) return x } }
  assert.strictEqual(find(8).s, 'Quay đầu tại ngã tư')
  assert.strictEqual(find(13).s, 'Xuất phát')
  assert.strictEqual(find(1).s, 'Nguyễn Văn Linh')
  i18n.setLang('en')
  try {
    assert.strictEqual(find(8).s, 'U-turn at the junction')
    assert.strictEqual(find(13).s, 'Start')
    assert.strictEqual(find(12).s, 'Destination')
    assert.strictEqual(find(9).s, 'Dân Chủ roundabout')
    assert.strictEqual(find(1).s, 'Nguyễn Văn Linh')
  } finally { i18n.setLang('vi') }
})

test('i18n: vi mặc định, en đầy đủ khóa, detect an toàn khi lỗi', () => {
  assert.deepStrictEqual(Object.keys(i18n.TABLES.en).sort(), Object.keys(i18n.TABLES.vi).sort())
  assert.strictEqual(i18n.detect(), 'vi') // không có @system.configuration trong node
  assert.strictEqual(i18n.detect({ getLocale() { throw new Error('x') } }), 'vi')
  assert.strictEqual(i18n.detect({ getLocale: () => ({ language: 'en', countryOrRegion: 'US' }) }), 'en')
  assert.strictEqual(i18n.detect({ getLocale: () => ({ language: 'vi' }) }), 'vi')
  i18n.setLang('en')
  try {
    assert.strictEqual(S.present('BT_OFF').text, 'Turn on watch Bluetooth')
    assert.strictEqual(h.view(h.createState(), 0).banner, 'No route yet')
    assert.strictEqual(h.presetLabel('off'), 'Mute turn haptics')
    const st = h.createState(); h.accept(st, msg({ m: 9, x: 2 }), 0)
    assert.strictEqual(h.view(st, 0).exitBadge, 'EXIT 2')
  } finally { i18n.setLang('vi') }
  assert.strictEqual(h.view(h.createState(), 0).banner, 'Chờ chỉ đường')
})

test('status.present/preRide: văn bản, tông, nhịp; chỉ tin hợp lệ mới là "đã kết nối"', () => {
  assert.deepStrictEqual(S.present('SEARCHING'), { text: 'Đang tìm ĐT...', tone: 'warn', pulse: true })
  assert.deepStrictEqual(S.present('CONNECTING'), { text: 'Đang kết nối...', tone: 'warn', pulse: true })
  assert.deepStrictEqual(S.present('AWAIT_FRAME'), { text: 'Chờ dữ liệu ĐT', tone: 'warn', pulse: true })
  assert.deepStrictEqual(S.present('RETRYING'), { text: 'Đang nối lại...', tone: 'warn', pulse: true })
  assert.deepStrictEqual(S.present('CONNECTED_IDLE'), { text: 'Đã kết nối', tone: 'ok', pulse: false })
  assert.deepStrictEqual(S.present('BT_OFF'), { text: 'Bật Bluetooth trên đồng hồ', tone: 'err', pulse: false })
  assert.deepStrictEqual(S.present('ERROR'), { text: 'Lỗi kết nối', tone: 'err', pulse: false })
  assert.strictEqual(S.effectivePhase('', 'CONNECTED_IDLE'), 'AWAIT_FRAME')
  assert.strictEqual(S.effectivePhase('C', 'SEARCHING'), 'CONNECTED_IDLE')
  assert.strictEqual(S.effectivePhase('', 'bogus'), 'SEARCHING')
  assert.strictEqual(S.preRide('CONNECTED_IDLE').hint, 'Bắt đầu chỉ đường trong Maps')
  for (const p of ['SEARCHING', 'CONNECTING', 'AWAIT_FRAME', 'RETRYING', 'ERROR']) {
    assert.strictEqual(S.preRide(p).hint, 'Mở MotoNav trên ĐT và bấm Bắt đầu', p)
  }
  assert.strictEqual(S.preRide('BT_OFF').hint, 'Bật Bluetooth trên đồng hồ')
  assert.strictEqual(S.preRide('BT_OFF').bannerTone, 'err')
})

test('BLE phase: tìm -> nối -> chờ tin -> chỉ frame hợp lệ mới CONNECTED_IDLE -> ngắt = RETRYING', (t) => {
  let accept = false
  const { m, ph, tr } = rig(t, () => accept)
  tr.start()
  assert.deepStrictEqual(ph, ['SEARCHING'])
  toSubscribed(m)
  assert.strictEqual(ph[ph.length - 1], 'CONNECTING')
  m.notifyOpts.success()
  assert.strictEqual(ph[ph.length - 1], 'AWAIT_FRAME')
  m.dev.onBLECharacteristicChange({ characteristicUuid: CH, characteristicValue: enc({ bad: 1 }) })
  assert.strictEqual(ph[ph.length - 1], 'AWAIT_FRAME') // frame bị loại: chưa kết nối
  accept = true
  m.dev.onBLECharacteristicChange({ characteristicUuid: CH, characteristicValue: enc({ ok: 1 }) })
  assert.strictEqual(ph[ph.length - 1], 'CONNECTED_IDLE')
  m.dev.onBLEConnectionStateChange(0)
  assert.strictEqual(ph[ph.length - 1], 'RETRYING')
  t.mock.timers.tick(500) // begin() lại: giữ RETRYING, không nhấp nháy SEARCHING
  assert.strictEqual(ph[ph.length - 1], 'RETRYING')
  tr.stop()
})

test('BLE phase: 10001 = BT_OFF (scan fail, giữ nguyên nhân); lỗi khác/timeout = ERROR; không thấy máy = SEARCHING', (t) => {
  let r = rig(t)
  r.tr.start()
  r.m.scanOpts.fail({}, 10001)
  assert.strictEqual(r.ph[r.ph.length - 1], 'BT_OFF:10001')
  t.mock.timers.tick(500)
  assert.strictEqual(r.ph[r.ph.length - 1], 'BT_OFF:10001') // không nhấp nháy khi quét lại
  r.m.onFound([{ deviceId: 'AA:BB', data: adv() }]) // có kết quả: BT đã bật
  assert.strictEqual(r.ph.includes('SEARCHING'), true)
  assert.strictEqual(r.ph[r.ph.length - 1], 'CONNECTING')
  r.tr.stop()

  // 10001 ở lần fail cuối khi hết biến thể: không bị ghi đè bằng ERROR chung
  r = rig(t)
  r.tr.start()
  r.m.scanOpts.fail({}, 202)
  r.m.scanOpts.fail({}, 202)
  r.m.scanOpts.fail({}, 202)
  r.m.scanOpts.fail({}, 10001)
  assert.strictEqual(r.ph[r.ph.length - 1], 'BT_OFF:10001')
  r.tr.stop()

  // hết biến thể không có 10001 => ERROR kèm mã, không phải SEARCHING/CONNECTING
  r = rig(t)
  r.tr.start()
  for (let i = 0; i < 4; i++) r.m.scanOpts.fail({}, 202)
  assert.strictEqual(r.ph[r.ph.length - 1], 'ERROR:202')
  r.tr.stop()

  // connect timeout / connect fail / 10001 khi nối
  r = rig(t)
  r.tr.start()
  r.m.onFound([{ deviceId: 'AA:BB', data: adv() }])
  t.mock.timers.tick(6000)
  assert.strictEqual(r.ph[r.ph.length - 1], 'ERROR:timeout')
  r.tr.stop()
  r = rig(t)
  r.tr.start()
  r.m.onFound([{ deviceId: 'AA:BB', data: adv() }])
  r.m.connectOpts.fail({}, 10001)
  assert.strictEqual(r.ph[r.ph.length - 1], 'BT_OFF:10001')
  r.tr.stop()

  // quét khởi động thành công (BT bật, chưa có quảng bá) => bỏ BT_OFF
  r = rig(t)
  r.tr.start()
  r.m.scanOpts.fail({}, 10001)
  t.mock.timers.tick(500)
  assert.strictEqual(r.ph[r.ph.length - 1], 'BT_OFF:10001')
  r.m.scanOpts.success()
  assert.strictEqual(r.ph[r.ph.length - 1], 'SEARCHING')
  r.tr.stop()

  // hết hạn chờ frame đầu (đã đóng GATT, chờ backoff) => RETRYING, không AWAIT_FRAME
  r = rig(t)
  r.tr.start()
  toSubscribed(r.m)
  r.m.notifyOpts.success()
  assert.strictEqual(r.ph[r.ph.length - 1], 'AWAIT_FRAME')
  t.mock.timers.tick(8000)
  assert.strictEqual(r.ph[r.ph.length - 1], 'RETRYING')
  r.tr.stop()

  // quét hết hạn không thấy máy => SEARCHING (không báo lỗi)
  r = rig(t)
  r.tr.start()
  t.mock.timers.tick(8000)
  assert.strictEqual(r.ph[r.ph.length - 1], 'SEARCHING')
  r.tr.stop()
})

test('palette: không còn màu gần trùng #ff4d2d / #ff6b6b trong index.ux', () => {
  const ux = fs.readFileSync(__dirname + '/../src/pages/index/index.ux', 'utf8').toLowerCase()
  assert.ok(!ux.includes('#ff4d2d'))
  assert.ok(!ux.includes('#ff6b6b'))
})

test('giờ nhỏ góc trên: gắn trong index.ux, không đè dial/huy hiệu LỐI và nằm trong vòng cung', () => {
  const ux = fs.readFileSync(__dirname + '/../src/pages/index/index.ux', 'utf8')
  assert.ok(ux.includes('{{clockNow}}'))
  assert.ok(ux.includes("this.setField('clockNow', clockStr())"))
  const css = ux.match(/\.clock-top\s*\{([^}]*)\}/)[1]
  const px = (k) => Number(css.match(new RegExp(k + ':\\s*(\\d+)px'))[1])
  const [l, t, w, hgt] = [px('left'), px('top'), px('width'), px('height')]
  const r = l + w, b = t + hgt
  // mọi góc nằm trong mép trong vòng cung (r = 223px quanh tâm 240,240)
  for (const [x, y] of [[l, t], [r, t], [l, b], [r, b]]) {
    assert.ok(Math.hypot(x - 240, y - 240) < 223, `góc (${x},${y}) chạm vòng cung`)
  }
  assert.ok(l >= 306)   // không đè mép phải của dial (174..306)
  assert.ok(b <= 106)   // không đè huy hiệu LỐI (top 106)
})

test('dấu tin cũ: khóa quiet.mark (vi/en) và gắn trong index.ux', () => {
  assert.strictEqual(i18n.TABLES.vi['quiet.mark'], 'cũ {n}s')
  assert.strictEqual(i18n.TABLES.en['quiet.mark'], 'old {n}s')
  i18n.setLang('en')
  try { assert.strictEqual(i18n.t('quiet.mark', 24), 'old 24s') } finally { i18n.setLang('vi') }
  assert.strictEqual(i18n.t('quiet.mark', 24), 'cũ 24s')
  const ux = fs.readFileSync(__dirname + '/../src/pages/index/index.ux', 'utf8')
  assert.ok(ux.includes('{{quietMark}}'))
  assert.ok(ux.includes("i18n.t('quiet.mark', v.quietSec)"))
})
