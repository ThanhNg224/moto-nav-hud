const test = require('node:test')
const assert = require('node:assert')
const h = require('../src/common/hud-logic.js')
const haptics = require('../src/common/haptics.js')
const bleT = require('../src/common/transport-ble.js')
const icT = require('../src/common/transport-interconnect.js')
const cfg = require('../src/common/config.js')
cfg.DEBUG_LOG = false

const base = { v: 1, p: cfg.PAIR_TOKEN, z: 100, q: 1, i: 1, a: 0, k: 1, m: 3, x: 0, d: 500, s: 'A', e: '12:45', r: 4200 }
const raw = (o) => Object.assign({}, base, o)
const msg = (o) => h.parsePayload(raw(o))

test('token sai hoặc thiếu bị bỏ', () => {
  assert.strictEqual(h.parsePayload(raw({ p: 'OTHER' })), null)
  const o = raw({}); delete o.p
  assert.strictEqual(h.parsePayload(o), null)
  assert.ok(h.parsePayload(raw({})))
})

test('validate miền giá trị: bad frame không đổi trạng thái', () => {
  const st = h.createState()
  h.accept(st, msg({ q: 5 }), 0)
  const before = JSON.stringify(st)
  const bads = [{ q: 0 }, { q: -1, z: 7 }, { a: -1 }, { d: -2 }, { x: -1 }, { q: 2 ** 60 }, { z: 1.5 }, { r: -5 }, { i: NaN }]
  for (const b of bads) {
    const m = h.parsePayload(raw(b))
    assert.strictEqual(m, null, JSON.stringify(b))
    if (m) h.accept(st, m, 1)
  }
  assert.strictEqual(JSON.stringify(st), before)
  assert.strictEqual(h.view(st, 1).mode, 'nav')
})

test('utf8 hỏng bị trả null', () => {
  assert.strictEqual(h.utf8Decode(new Uint8Array([0x41, 0xc3]).buffer), null)
  assert.strictEqual(h.utf8Decode(new Uint8Array([0xc0, 0x80]).buffer), null)
  assert.strictEqual(h.utf8Decode(new Uint8Array([0xed, 0xa0, 0x80]).buffer), null)
  assert.strictEqual(h.utf8Decode(new Uint8Array([0xff]).buffer), null)
})

test('tuổi hiệu dụng k=1: > 10000 yên lặng (giữ nav), > NAV_LOST_SOURCE_AGE_MS => stale', () => {
  const st = h.createState()
  h.accept(st, msg({ a: 9900 }), 1000)
  assert.strictEqual(h.view(st, 1000).mode, 'nav')
  assert.strictEqual(h.view(st, 1100).quiet, false)
  const q = h.view(st, 1101)
  assert.strictEqual(q.mode, 'nav'); assert.strictEqual(q.quiet, true); assert.strictEqual(q.banner, '')
  const s2 = h.createState()
  h.accept(s2, msg({ a: cfg.NAV_LOST_SOURCE_AGE_MS - 1000 }), 1000)
  assert.strictEqual(h.view(s2, 2000).mode, 'nav')
  assert.strictEqual(h.view(s2, 2001).mode, 'stale')
  assert.strictEqual(h.view(s2, 2001).arrow, null)
})

test('đồng hồ lùi => stale cho tới khi có tin mới, kể cả khi đồng hồ tiến lại', () => {
  const st = h.createState()
  h.accept(st, msg({}), 5000)
  assert.strictEqual(h.view(st, 4000).mode, 'stale')
  assert.strictEqual(h.view(st, 5500).mode, 'stale')
  h.accept(st, msg({ q: 2 }), 6000)
  assert.strictEqual(h.view(st, 6000).mode, 'nav')
})

test('session đã thay thế bị loại', () => {
  const st = h.createState()
  h.accept(st, msg({ z: 1, q: 3 }), 0)
  assert.ok(h.accept(st, msg({ z: 2, q: 1 }), 1).accepted)
  assert.ok(!h.accept(st, msg({ z: 1, q: 99 }), 2).accepted)
  assert.strictEqual(st.msg.z, 2)
  assert.ok(h.accept(st, msg({ z: 3, q: 1 }), 3).accepted)
  assert.ok(!h.accept(st, msg({ z: 2, q: 50 }), 4).accepted)
})

test('invalidate: xóa hướng dẫn ngay, tin trùng không khôi phục, rung lost một lần', () => {
  const st = h.createState()
  h.accept(st, msg({ q: 7 }), 0)
  assert.strictEqual(h.invalidate(st), 'lost')
  assert.strictEqual(h.view(st, 1).mode, 'stale')
  assert.strictEqual(h.view(st, 1).arrow, null)
  assert.strictEqual(h.invalidate(st), null)
  assert.ok(!h.accept(st, msg({ q: 7 }), 2).accepted)
  assert.strictEqual(h.view(st, 2).arrow, null)
  assert.ok(h.accept(st, msg({ q: 8 }), 3).accepted)
  assert.strictEqual(h.view(st, 3).mode, 'nav')
  const st2 = h.createState()
  assert.strictEqual(h.invalidate(st2), null)
})

test('cancel khi hướng dẫn bị vô hiệu hóa hoặc bước mới ngoài vùng cảnh báo', () => {
  const st = h.createState()
  assert.strictEqual(h.accept(st, msg({ q: 1, d: 100 }), 0).vibe, 'far')
  assert.strictEqual(h.accept(st, msg({ q: 2, d: 90 }), 1).cancel, false)
  assert.ok(h.accept(st, msg({ q: 3, k: 2 }), 2).cancel)
  assert.ok(h.accept(st, msg({ q: 4, k: 0 }), 3).cancel)
  assert.ok(h.accept(st, msg({ q: 5, k: 3 }), 4).cancel)
  assert.ok(h.accept(st, msg({ q: 6, m: 0 }), 5).cancel)
  assert.ok(h.accept(st, msg({ q: 7, i: 9, d: 800 }), 6).cancel)
})

test('haptics: start/stop task, hủy khi id về muộn, thay thế', () => {
  const calls = []
  let pending = null
  const api = {
    start(o) { calls.push(['start', o.count]); pending = o; },
    stop(id) { calls.push(['stop', id]); return true },
    vibrate(o) { calls.push(['vibrate', o.mode]) }
  }
  const hp = haptics.create(api)
  hp.play('far')
  pending.success({ id: 11 })
  hp.play('near') // thay thế: stop(11) trước khi start
  assert.deepStrictEqual(calls.slice(1), [['stop', 11], ['start', 3]])
  hp.cancel() // id chưa về
  pending.success({ id: 12 }) // về muộn => stop ngay
  assert.deepStrictEqual(calls[calls.length - 1], ['stop', 12])
  hp.play('far'); pending.success({ id: 13 }); hp.cancel()
  assert.deepStrictEqual(calls[calls.length - 1], ['stop', 13])
  // start lỗi => fallback vibrate
  hp.play('far'); pending.success({ id: 14 }); pending.complete && pending.complete(); hp.cancel()
  assert.deepStrictEqual(calls[calls.length - 1], ['stop', 14]) // complete không xóa id
  hp.play('far'); pending.fail({}, 205)
  assert.deepStrictEqual(calls[calls.length - 1], ['vibrate', 'short'])
})

function mockBle() {
  const log = []
  const m = { log, dev: null, scanner: null }
  m.api = {
    createScanner() {
      m.scanner = {
        subscribeBLEDeviceFind(o) { m.onFound = o.callback; return 1 },
        startBLEScan(o) { log.push(['scan', JSON.stringify(o.filters), !!o.options]); m.scanOpts = o },
        stopBLEScan() {}, unsubscribeBLEDeviceFind() {}, close() { log.push(['scanner.close']) }
      }
      return m.scanner
    },
    createGattClientDevice(id) {
      m.dev = {
        connect(o) { log.push(['connect', id]); m.connectOpts = o },
        setBLEMtuSize(o) { log.push(['mtu']); m.mtuOpts = o },
        getServices(o) { log.push(['services']); m.svcOpts = o },
        setNotifyCharacteristicChanged(o) { log.push(['notify']); m.notifyOpts = o },
        disconnect() {}, close() { log.push(['dev.close']) }
      }
      return m.dev
    }
  }
  return m
}
const SVC = '6e4f0001-b5a3-f393-e0a9-e50e24dcca9e'
const CH = '6e4f0002-b5a3-f393-e0a9-e50e24dcca9e'
const enc = (o) => new TextEncoder().encode(JSON.stringify(o)).buffer

test('BLE: connect success KHÔNG bắt đầu MTU/services; chỉ CONNECTED mới bắt đầu', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const m = mockBle(); const st = []
  const tr = bleT.create({ onMessage: () => false, onStatus: (s) => st.push(s), onLost() {} }, m.api)
  tr.start()
  assert.deepStrictEqual(m.log[0].slice(0, 2), ['scan', JSON.stringify([{}])]) // mặc định V3
  m.onFound([{ deviceId: 'AA:BB', addressType: 'PUBLIC', data: advWith([]) }])
  m.connectOpts.success()
  assert.ok(!m.log.some((l) => l[0] === 'mtu' || l[0] === 'services'))
  m.dev.onBLEConnectionStateChange(2)
  assert.strictEqual(m.log.filter((l) => l[0] === 'mtu').length, 1)
  assert.ok(!m.log.some((l) => l[0] === 'services')) // đợi MTU ack
  m.mtuOpts.success({})
  assert.ok(m.log.some((l) => l[0] === 'services'))
  assert.ok(!st.some((s) => /MTU 185/.test(s) && !/yêu cầu/.test(s)))
  m.svcOpts.success([{ serviceUuid: SVC, characteristics: [{ characteristicUuid: CH }] }])
  assert.ok(m.log.some((l) => l[0] === 'notify'))
  tr.stop()
})

test('BLE: pinned address vẫn lọc theo service UUID; không nhánh name-only', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const m = mockBle()
  const tr = bleT.create({ onMessage: () => false, onStatus() {}, onLost() {} }, m.api)
  tr.start()
  assert.ok(!/name/.test(m.log[0][1]))
  tr.stop()
})

test('BLE: không có frame trong 8 s => retry, backoff chỉ reset sau frame hợp lệ; callback cũ bị bỏ', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const m = mockBle(); const st = []; let lost = 0; let frames = 0
  const tr = bleT.create({ onMessage: () => { frames++; return true }, onStatus: (s) => st.push(s), onLost: () => lost++ }, m.api)
  tr.start()
  m.onFound([{ deviceId: 'AA:BB', data: advWith([]) }])
  m.dev.onBLEConnectionStateChange(2)
  m.mtuOpts.success({})
  m.svcOpts.success([{ serviceUuid: SVC, characteristics: [{ characteristicUuid: CH }] }])
  const oldDev = m.dev, oldNotify = m.notifyOpts
  oldNotify.success()
  t.mock.timers.tick(8000)
  assert.ok(st.some((s) => /Không có dữ liệu BLE \(MTU\?\)/.test(s)))
  assert.ok(st.some((s) => /thử lại 1s/.test(s)))
  // callback muộn của attempt cũ không được làm gì
  const n = m.log.length
  oldNotify.success(); m.svcOpts.success([])
  assert.strictEqual(m.log.length, n)
  t.mock.timers.tick(1000) // backoff 1s -> scan lại
  assert.ok(m.log.filter((l) => l[0] === 'scan').length === 2)
  const devs = m.log.filter((l) => l[0] === 'connect').length
  m.onFound([{ deviceId: 'AA:BB', data: advWith([]) }]) // máy vừa thất bại bị bỏ qua
  assert.strictEqual(m.log.filter((l) => l[0] === 'connect').length, devs)
  m.onFound([{ deviceId: 'AA:BB', data: advWith([]) }, { deviceId: 'CC:DD', data: advWith([]) }]) // xoay sang máy khác
  assert.strictEqual(m.log.filter((l) => l[0] === 'connect').length, devs + 1)
  m.dev.onBLEConnectionStateChange(2)
  m.mtuOpts.success({})
  m.svcOpts.success([{ serviceUuid: SVC, characteristics: [{ characteristicUuid: CH }] }])
  m.notifyOpts.success()
  m.dev.onBLECharacteristicChange({ characteristicUuid: CH, characteristicValue: enc(base) })
  assert.strictEqual(frames, 1)
  m.dev.onBLEConnectionStateChange(0) // ngắt => onLost, backoff reset nên thử lại 1s
  assert.strictEqual(lost, 1)
  assert.ok(st[st.length - 1].includes('thử lại 1s'))
  tr.stop()
})

test('interconnect: callback muộn sau stop() bị bỏ; mất nối báo onLost', (t) => {
  t.mock.timers.enable({ apis: ['setInterval'] })
  const conn = { getReadyState(o) { conn.rs = o } }
  const api = { instance: () => conn }
  let got = 0; let lost = 0
  const tr = icT.create({ onMessage: () => { got++; return true }, onStatus() {}, onLost: () => lost++ }, api)
  tr.start()
  conn.rs.success({ status: 1 })
  const onmsg = conn.onmessage
  onmsg({ data: 'x' })
  assert.strictEqual(got, 1)
  conn.rs.success({ status: 2 })
  assert.strictEqual(lost, 1)
  tr.stop()
  onmsg({ data: 'y' })
  conn.rs.success({ status: 1 })
  assert.strictEqual(got, 1)
})

const advWith = (extra) => {
  const u = SVC.replace(/-/g, '').match(/../g).reverse().map((h) => parseInt(h, 16))
  return new Uint8Array([2, 1, 6, 17, 7, ...u, ...extra]).buffer
}

test('BLE: khớp UUID 128-bit little-endian trong dữ liệu quảng bá', () => {
  assert.ok(bleT.advHasService(advWith([])))
  assert.ok(!bleT.advHasService(new Uint8Array([2, 1, 6, 3, 3, 0x0f, 0x18]).buffer))
  assert.ok(!bleT.advHasService(undefined))
  assert.ok(bleT.acceptResult({ data: advWith([]) }, 3))
  assert.ok(!bleT.acceptResult({ data: new Uint8Array([2, 1, 6]).buffer }, 0)) // có data mà không có UUID => loại
  assert.ok(!bleT.acceptResult({}, 1)) // không có data: loại ở mọi biến thể
  assert.ok(!bleT.acceptResult({}, 3))
  assert.ok(!bleT.acceptResult({}, 4))
})

test('BLE: mặc định V3; fail liền V3 -> V4 -> V0 -> V2, hết thì retry; diag ghi mã từng biến thể', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const m = mockBle(); const st = []; const diag = []
  const tr = bleT.create({ onMessage: () => false, onStatus: (s) => st.push(s), onDiag: (d) => diag.push(d), onLost() {} }, m.api)
  const scans = () => m.log.filter((l) => l[0] === 'scan').map((l) => l[1])
  tr.start()
  assert.deepStrictEqual(scans(), [JSON.stringify([{}])])
  assert.ok(st[st.length - 1].startsWith('C quét V3 '))
  m.scanOpts.fail({}, 202)
  m.scanOpts.fail({}, 202)
  m.scanOpts.fail({}, 202)
  assert.deepStrictEqual(scans().slice(1), ['[]', JSON.stringify([{ serviceUuid: SVC }]), JSON.stringify([{ serviceUuid: SVC.toUpperCase() }])])
  assert.ok(st[st.length - 1].startsWith('C quét V2 '))
  assert.ok(!m.log.some((l) => l[0] === 'scan' && l[2] === true)) // không còn options
  m.scanOpts.fail({}, 202)
  assert.ok(diag.some((d) => d.includes('V0:202 V2:202 V3:202 V4:202')))
  assert.ok(st[st.length - 1].includes('scan hết biến thể'))
  t.mock.timers.tick(1000)
  assert.strictEqual(scans().length, 5)
  assert.strictEqual(scans()[4], JSON.stringify([{}])) // quay về mặc định
  tr.stop()
})

test('BLE: V3 chỉ nối thiết bị có UUID trong dữ liệu quảng bá; diag hiện thiết bị đã khớp', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const m = mockBle(); const diag = []
  const tr = bleT.create({ onMessage: () => false, onStatus() {}, onDiag: (d) => diag.push(d), onLost() {} }, m.api)
  tr.start()
  m.onFound([{ deviceId: 'XX', rssi: -80 }, { deviceId: 'YY', rssi: -70, data: new Uint8Array([2, 1, 6]).buffer }])
  assert.ok(!m.log.some((l) => l[0] === 'connect'))
  m.onFound([{ deviceId: 'AA:BB:CC', rssi: -60, addressType: 'RANDOM', data: advWith([]) }])
  assert.ok(m.log.some((l) => l[0] === 'connect'))
  assert.ok(diag.some((d) => d.includes('BB:CC/-60/R')))
  tr.stop()
})

test('BLE: state 0 ban đầu (chưa thấy 1/2, <3 s) không retry; 0 sau khi đã thấy 1/2 thì retry; diag có st: lịch sử', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const go = () => {
    const m = mockBle(); const diag = []; const st = []
    const tr = bleT.create({ onMessage: () => false, onStatus: (s) => st.push(s), onDiag: (d) => diag.push(d), onLost() {} }, m.api)
    tr.start()
    m.onFound([{ deviceId: 'AA:BB', data: advWith([]) }])
    return { m, diag, st, tr }
  }
  let x = go()
  x.m.dev.onBLEConnectionStateChange(0)
  assert.ok(!x.st.some((s) => /thử lại/.test(s)))
  x.m.dev.onBLEConnectionStateChange(1)
  x.m.dev.onBLEConnectionStateChange(2)
  x.m.dev.onBLEConnectionStateChange(2) // lặp không setup lần hai
  assert.strictEqual(x.m.log.filter((l) => l[0] === 'mtu').length, 1)
  assert.ok(x.diag[x.diag.length - 1].startsWith('st:0,1,2,2@'))
  x.tr.stop()
  x = go()
  x.m.dev.onBLEConnectionStateChange(1)
  x.m.dev.onBLEConnectionStateChange(0) // 1 -> 0: nối thất bại thật
  assert.ok(x.st[x.st.length - 1].includes('ngắt thử lại'))
  x.tr.stop()
  x = go()
  x.m.dev.onBLEConnectionStateChange(0)
  x.m.connectOpts.fail({}, 123) // connect.fail ghi mã
  assert.ok(x.diag[x.diag.length - 1].includes('last:conn 123'))
  x.tr.stop()
})

test('hud view urgency: >200m normal, <=200m far, <=50m near', () => {
  const st = h.createState()
  h.accept(st, msg({ q: 1, d: 300 }), 0)
  assert.strictEqual(h.view(st, 0).urgency, 'normal')
  assert.strictEqual(h.view(st, 0).arcPercent, 40)
  h.accept(st, msg({ q: 2, d: 180 }), 100)
  assert.strictEqual(h.view(st, 100).urgency, 'far')
  assert.strictEqual(h.view(st, 100).arcPercent, 64)
  h.accept(st, msg({ q: 3, d: 45 }), 200)
  assert.strictEqual(h.view(st, 200).urgency, 'near')
  assert.strictEqual(h.view(st, 200).arcPercent, 91)
  h.accept(st, msg({ q: 4, m: 0, d: 30 }), 300)
  assert.strictEqual(h.view(st, 300).urgency, 'normal')
})

test('haptic presets: city/fast/off và exitBadge vòng xuyến', () => {
  const st = h.createState()
  h.applyPreset(st, 'city')
  assert.strictEqual(h.accept(st, msg({ q: 1, i: 1, d: 170 }), 0).vibe, null)
  assert.strictEqual(h.accept(st, msg({ q: 2, i: 1, d: 150 }), 10).vibe, 'far')
  assert.strictEqual(h.accept(st, msg({ q: 3, i: 1, d: 40 }), 20).vibe, 'near')
  h.applyPreset(st, 'off')
  assert.strictEqual(h.accept(st, msg({ q: 4, i: 2, d: 30 }), 30).vibe, null)
  assert.strictEqual(h.view(st, 30).urgency, 'near') // tắt rung vẫn hiện màu khẩn cấp
  h.accept(st, msg({ q: 5, i: 3, m: 9, x: 2, d: 120, s: 'Phạm Văn Đồng' }), 40)
  const v = h.view(st, 40)
  assert.strictEqual(v.exitBadge, 'LỐI 2')
  assert.strictEqual(v.road, 'Phạm Văn Đồng')
})

test('BLE: UUID chỉ khớp trong AD 0x06/0x07, không khớp trong manufacturer data; scan response nối sau được nhận', () => {
  const u = SVC.replace(/-/g, '').match(/../g).reverse().map((x) => parseInt(x, 16))
  const mfr = new Uint8Array([2, 1, 6, 17, 0xff, ...u]).buffer
  assert.ok(!bleT.advHasService(mfr))
  assert.ok(!bleT.advHasService(new Uint8Array([2, 1, 6, 17, 7, ...u.slice(0, 15)]).buffer)) // cắt cụt
  assert.ok(bleT.advHasService(new Uint8Array([17, 6, ...u]).buffer)) // 0x06 incomplete list
  // advertising + đệm 0 + scan response chứa UUID
  assert.ok(bleT.advHasService(new Uint8Array([2, 1, 6, 0, 0, 17, 7, ...u]).buffer))
})

test('BLE: mọi thất bại trước frame đầu (connect.fail, state 0 sau 1, svc fail) đưa máy vào skip; ưu tiên máy chưa thử trong batch', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'setInterval', 'Date'] })
  const m = mockBle()
  const tr = bleT.create({ onMessage: () => false, onStatus() {}, onLost() {} }, m.api)
  const peers = () => m.log.filter((l) => l[0] === 'connect').map((l) => l[1])
  tr.start()
  m.onFound([{ deviceId: 'AA:BB', data: advWith([]) }])
  m.connectOpts.fail({}, 5) // connect.fail -> skip=[aa:bb]
  t.mock.timers.tick(1000)
  // batch: AA:BB (đã thất bại) đứng trước CC:DD (mới) => chọn đúng CC:DD kể cả sau SKIP_WAIT
  t.mock.timers.tick(3500)
  m.onFound([{ deviceId: 'AA:BB', data: advWith([]) }, { deviceId: 'CC:DD', data: advWith([]) }])
  assert.deepStrictEqual(peers(), ['AA:BB', 'CC:DD'])
  m.dev.onBLEConnectionStateChange(1)
  m.dev.onBLEConnectionStateChange(0) // ngắt trước frame => skip=[aa:bb, cc:dd]
  t.mock.timers.tick(2000)
  t.mock.timers.tick(3500)
  m.onFound([{ deviceId: 'CC:DD', data: advWith([]) }, { deviceId: 'EE:FF', data: advWith([]) }])
  assert.deepStrictEqual(peers(), ['AA:BB', 'CC:DD', 'EE:FF'])
  m.dev.onBLEConnectionStateChange(2)
  m.mtuOpts.success({})
  m.svcOpts.fail({}, 7) // svc fail => skip=[aa:bb, cc:dd, ee:ff]
  t.mock.timers.tick(4000)
  t.mock.timers.tick(3500)
  // cả ba đều đã thất bại, không còn máy mới: sau SKIP_WAIT vẫn chọn lại máy cũ
  m.onFound([{ deviceId: 'EE:FF', data: advWith([]) }])
  assert.deepStrictEqual(peers(), ['AA:BB', 'CC:DD', 'EE:FF', 'EE:FF'])
  tr.stop()
})

test('BLE: xoay vòng các máy trong skip theo lần thử lâu nhất (LRU), không bỏ đói điện thoại đã hồi phục', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'setInterval', 'Date'] })
  const m = mockBle()
  const tr = bleT.create({ onMessage: () => false, onStatus() {}, onLost() {} }, m.api)
  const peers = () => m.log.filter((l) => l[0] === 'connect').map((l) => l[1])
  const batch = () => m.onFound([{ deviceId: 'BB:BB', data: advWith([]) }, { deviceId: 'AA:AA', data: advWith([]) }])
  tr.start()
  batch() // chưa thử ai -> chọn BB:BB
  m.connectOpts.fail({}, 1) // skip = [bb:bb]
  t.mock.timers.tick(1000)
  batch() // AA:AA chưa thử -> ưu tiên chọn AA:AA
  m.connectOpts.fail({}, 1) // skip = [bb:bb, aa:aa]
  t.mock.timers.tick(2000)
  t.mock.timers.tick(1500)
  batch() // cả hai đều trong skip -> chọn máy thử lâu nhất BB:BB
  m.connectOpts.fail({}, 1) // đẩy BB:BB xuống cuối -> skip = [aa:aa, bb:bb]
  t.mock.timers.tick(3000)
  t.mock.timers.tick(1500)
  batch() // dù BB:BB vẫn đứng trước trong batch, phải xoay sang AA:AA (thử lâu nhất)
  assert.deepStrictEqual(peers(), ['BB:BB', 'AA:AA', 'BB:BB', 'AA:AA'])
  tr.stop()
})

test('BLE: biến thể quét fail tạm thời hết hạn theo thời gian; sweep không khớp reset bảng', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'setInterval', 'Date'] })
  const m = mockBle(); const diag = []
  const tr = bleT.create({ onMessage: () => false, onStatus() {}, onDiag: (d) => diag.push(d), onLost() {} }, m.api)
  const scans = () => m.log.filter((l) => l[0] === 'scan').map((l) => l[1])
  tr.start()
  m.scanOpts.fail({}, 202) // V3 lỗi thoáng qua -> V4
  assert.strictEqual(scans()[1], '[]')
  t.mock.timers.tick(8000) // V4 không khớp => retry; V3 vẫn đang bị loại
  t.mock.timers.tick(1000)
  assert.notStrictEqual(scans()[2], JSON.stringify([{}]))
  t.mock.timers.tick(70000) // quá TTL: V3 đủ điều kiện trở lại
  for (let i = 0; i < 6; i++) t.mock.timers.tick(15000)
  assert.ok(scans().slice(2).includes(JSON.stringify([{}])))
  tr.stop()
})

test('BLE: kết quả khớp nhưng bị skip không hủy hạn quét; hết hạn thì retry', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'setInterval', 'Date'] })
  const m = mockBle(); const st = []
  const tr = bleT.create({ onMessage: () => false, onStatus: (s) => st.push(s), onLost() {} }, m.api)
  tr.start()
  m.onFound([{ deviceId: 'AA:BB', data: advWith([]) }])
  m.connectOpts.fail({}, 1)
  t.mock.timers.tick(1000)
  m.onFound([{ deviceId: 'AA:BB', data: advWith([]) }]) // matched nhưng skip (chưa đủ 3 s)
  assert.strictEqual(m.log.filter((l) => l[0] === 'connect').length, 1)
  t.mock.timers.tick(8000) // máy skip được giữ lại và thử ở hạn quét
  assert.strictEqual(m.log.filter((l) => l[0] === 'connect').length, 2)
  m.connectOpts.fail({}, 1)
  assert.ok(st[st.length - 1].includes('thử lại'))
  tr.stop()
})

test('BLE: callback quét muộn (scan fail/kết quả) không phá attempt GATT đang nối', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'setInterval', 'Date'] })
  const m = mockBle(); const st = []
  const tr = bleT.create({ onMessage: () => false, onStatus: (s) => st.push(s), onLost() {} }, m.api)
  tr.start()
  const lateFail = m.scanOpts.fail, lateFound = m.onFound
  m.onFound([{ deviceId: 'AA:BB', data: advWith([]) }])
  const n = m.log.length
  lateFail({}, 202) // scan fail xếp hàng đến sau khi scanner đã nhả
  lateFound([{ deviceId: 'CC:DD', data: advWith([]) }])
  assert.strictEqual(m.log.length, n)
  assert.ok(!st.some((s) => /thử lại|hết biến thể/.test(s)))
  m.dev.onBLEConnectionStateChange(2)
  assert.ok(m.log.some((l) => l[0] === 'mtu'))
  tr.stop()
})

test('haptics: play khi start trước chưa có id giữ mẫu mới nhất, stop task cũ rồi start thay thế; cancelTurn giữ lost', () => {
  const calls = []
  let pending = null
  const api = {
    start(o) { calls.push(['start', o.count]); pending = o },
    stop(id) { calls.push(['stop', id]); return true },
    vibrate() {}
  }
  const hp = haptics.create(api)
  hp.play('far')
  hp.play('near'); hp.play('lost') // id chưa về: chỉ giữ 'lost'
  assert.deepStrictEqual(calls, [['start', 2]])
  const first = pending
  first.success({ id: 1 })
  assert.deepStrictEqual(calls.slice(1), [['stop', 1], ['start', 1]])
  pending.success({ id: 2 })
  hp.cancelTurn() // lost không bị hủy
  assert.ok(!calls.some((c) => c[0] === 'stop' && c[1] === 2))
  hp.cancel()
  assert.deepStrictEqual(calls[calls.length - 1], ['stop', 2])
  // start trước fail: mẫu giữ lại vẫn được bắt đầu
  hp.play('far'); hp.play('near')
  const f = pending
  f.fail({}, 205)
  assert.deepStrictEqual(calls[calls.length - 1], ['start', 3])
})

test('BLE: AD 0x07 độ dài payload không chia hết 16 bị loại', () => {
  const u = SVC.replace(/-/g, '').match(/../g).reverse().map((x) => parseInt(x, 16))
  assert.ok(!bleT.advHasService(new Uint8Array([18, 7, ...u, 0]).buffer))
})

test('BLE: máy skip chỉ thấy một lần được thử lại sau cửa sổ ưu tiên', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'setInterval', 'Date'] })
  const m = mockBle()
  const tr = bleT.create({ onMessage: () => false, onStatus() {}, onLost() {} }, m.api)
  const conns = () => m.log.filter((l) => l[0] === 'connect').length
  tr.start()
  m.onFound([{ deviceId: 'AA:BB', data: advWith([]) }])
  m.connectOpts.fail({}, 5)
  t.mock.timers.tick(1000)
  m.onFound([{ deviceId: 'AA:BB', data: advWith([]) }]) // chỉ báo một lần, <3 s
  assert.strictEqual(conns(), 1)
  t.mock.timers.tick(3000)
  assert.strictEqual(conns(), 2)
  tr.stop()
})

test('BLE: máy skip giữ lại được thử ở hạn quét nếu chưa tới cửa sổ', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'setInterval', 'Date'] })
  const m = mockBle()
  const tr = bleT.create({ onMessage: () => false, onStatus() {}, onLost() {} }, m.api)
  tr.start()
  m.onFound([{ deviceId: 'AA:BB', data: advWith([]) }])
  m.connectOpts.fail({}, 5)
  t.mock.timers.tick(1000)
  m.onFound([{ deviceId: 'AA:BB', data: advWith([]) }])
  t.mock.timers.tick(8000)
  assert.strictEqual(m.log.filter((l) => l[0] === 'connect').length, 2)
  tr.stop()
})

test('BLE: pin loại không tính matched; seen > 0 giữ nguyên V3 (không nhảy sang bộ lọc mù), seen === 0 mới đổi biến thể', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'setInterval', 'Date'] })
  const m = mockBle()
  const tr = bleT.create({ onMessage: () => false, onStatus() {}, onLost() {} }, m.api)
  const scans = () => m.log.filter((l) => l[0] === 'scan').map((l) => l[1])
  const old = cfg.BLE_PINNED_ADDRESS
  cfg.BLE_PINNED_ADDRESS = 'CC:DD'
  try {
    tr.start()
    m.onFound([{ deviceId: 'AA:BB', data: advWith([]) }]) // seen=1, pin loại => matched=0
    t.mock.timers.tick(8000)
    t.mock.timers.tick(1000)
    assert.strictEqual(scans()[1], scans()[0]) // scanner vẫn thấy thiết bị => giữ V3 để nối ngay khi ĐT phát BLE
    // Nếu 1 lượt quét không thấy thiết bị nào (seen === 0) mới đổi sang biến thể kế tiếp (V4 = [])
    t.mock.timers.tick(8000)
    t.mock.timers.tick(1500)
    assert.strictEqual(scans()[2], '[]')
    tr.stop()
    // fallback sau 7.9 s vẫn được nguyên hạn quét
    cfg.BLE_PINNED_ADDRESS = ''
    tr.start()
    t.mock.timers.tick(7900)
    m.scanOpts.fail({}, 202)
    const n = scans().length
    t.mock.timers.tick(7000)
    assert.strictEqual(scans().length, n) // chưa hết hạn của fallback
    tr.stop()
  } finally { cfg.BLE_PINNED_ADDRESS = old }
})

test('BLE: mọi startBLEScan ném lỗi => không báo quét/watchdog', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'setInterval', 'Date'] })
  const m = mockBle(); const st = []
  const api = Object.assign({}, m.api, { createScanner() {
    const s = m.api.createScanner(); s.startBLEScan = () => { throw new Error('x') }; return s
  } })
  const tr = bleT.create({ onMessage: () => false, onStatus: (s) => st.push(s), onLost() {} }, api)
  tr.start()
  assert.ok(/thử lại/.test(st[st.length - 1]))
  assert.ok(!st.some((s) => /^C quét/.test(s)))
  tr.stop()
})

test('haptics: stop(id) trả false giữ id cũ và mẫu thay thế tới khi task hết', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const calls = []
  let pending = null; let stopOk = false
  const api = {
    start(o) { calls.push(['start', o.count]); pending = o },
    stop(id) { calls.push(['stop', id]); return stopOk },
    vibrate() {}
  }
  const hp = haptics.create(api)
  hp.play('far'); pending.success({ id: 1 })
  hp.play('near')
  assert.deepStrictEqual(calls, [['start', 2], ['stop', 1]]) // chưa start thay thế
  stopOk = true
  t.mock.timers.tick(150)
  assert.deepStrictEqual(calls.slice(2), [['stop', 1], ['start', 3]])
  // id về muộn + stop false => giữ id, thử lại
  stopOk = false
  hp.cancel(); pending.success({ id: 2 })
  stopOk = true
  t.mock.timers.tick(150)
  assert.deepStrictEqual(calls[calls.length - 1], ['stop', 2])
})

test('haptics: stop(id) trả false sau khi mẫu cũ đã tự kết thúc không làm trễ mẫu khẩn mới', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'Date'] })
  const calls = []
  let pending = null
  const api = {
    start(o) { calls.push(['start', o.count]); pending = o },
    stop(id) { calls.push(['stop', id]); return false },
    vibrate() {}
  }
  const hp = haptics.create(api)
  hp.play('far'); pending.success({ id: 1 })
  t.mock.timers.tick(10000) // mẫu far (550 ms) đã kết thúc từ lâu
  hp.play('near')
  // Bắt đầu ngay lập tức, không bị treo chờ 3 s
  assert.deepStrictEqual(calls, [['start', 2], ['stop', 1], ['start', 3]])
})

test('haptics: stop(id) ném lỗi vẫn giữ id tới hạn mẫu; hạn đo từ lúc start success', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'Date'] })
  const calls = []
  let pending = null
  let throwStop = true
  const api = {
    start(o) { calls.push(['start', o.count]); pending = o },
    stop(id) { calls.push(['stop', id]); if (throwStop) throw new Error('busy'); return true },
    vibrate() {}
  }
  const hp = haptics.create(api)
  hp.play('far')
  t.mock.timers.tick(2000) // native start trả muộn 2 s
  hp.play('near')
  pending.success({ id: 9 }) // task vừa mới bắt đầu: không được coi là đã hết hạn
  assert.deepStrictEqual(calls, [['start', 2], ['stop', 9]])
  throwStop = false
  t.mock.timers.tick(150)
  assert.deepStrictEqual(calls.slice(2), [['stop', 9], ['start', 3]])
})
