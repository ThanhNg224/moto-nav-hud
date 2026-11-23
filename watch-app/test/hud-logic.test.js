const test = require('node:test')
const assert = require('node:assert')
const h = require('../src/common/hud-logic.js')

const base = { v: 1, p: 'MOTONAV1', z: 100, q: 1, i: 1, a: 0, k: 1, m: 3, x: 0, d: 500, s: 'Nguyễn Văn Linh', e: '12:45', r: 4200 }
const msg = (o) => h.parsePayload(Object.assign({}, base, o))

test('parse: hợp lệ, sai v, thiếu lõi, JSON hỏng', () => {
  assert.strictEqual(h.parsePayload(JSON.stringify(base)).d, 500)
  assert.strictEqual(h.parsePayload(Object.assign({}, base, { v: 2 })), null)
  assert.strictEqual(h.parsePayload(Object.assign({}, base, { m: 14 })), null)
  assert.strictEqual(h.parsePayload(Object.assign({}, base, { k: 4 })), null)
  for (const key of ['z', 'q', 'i', 'a', 'k', 'm', 'x', 'd']) {
    const o = Object.assign({}, base); delete o[key]
    assert.strictEqual(h.parsePayload(o), null, key)
  }
  assert.strictEqual(h.parsePayload('{oops'), null)
  assert.strictEqual(h.parsePayload('null'), null)
  const o = Object.assign({}, base); delete o.s; delete o.e; delete o.r
  assert.strictEqual(h.parsePayload(o).r, -1)
})

test('arrow mapping', () => {
  assert.strictEqual(h.arrowAsset(0), null)
  assert.strictEqual(h.arrowAsset(1), '/common/arrows/m01.png')
  assert.strictEqual(h.arrowAsset(13), '/common/arrows/m13.png')
  assert.strictEqual(h.arrowAsset(14), null)
})

test('distance format', () => {
  assert.strictEqual(h.formatDistance(0), '0 m')
  assert.strictEqual(h.formatDistance(999), '999 m')
  assert.strictEqual(h.formatDistance(1000), '1.0 km')
  assert.strictEqual(h.formatDistance(1250), '1.3 km')
  assert.strictEqual(h.formatDistance(-1), '--')
})

test('fold diacritics và utf8', () => {
  assert.strictEqual(h.foldDiacritics('Nguyễn Văn Linh, Đường Ơ ư ế ộ'), 'Nguyen Van Linh, Duong O u e o')
  const bytes = new TextEncoder().encode('Nguyễn Văn 😀')
  assert.strictEqual(h.utf8Decode(bytes.buffer), 'Nguyễn Văn 😀')
})

test('q: trùng/cũ bị bỏ và không làm mới timer', () => {
  const st = h.createState()
  assert.ok(h.accept(st, msg({ q: 5 }), 1000).accepted)
  assert.ok(!h.accept(st, msg({ q: 5, d: 10 }), 1100).accepted)
  assert.ok(!h.accept(st, msg({ q: 4, d: 10 }), 1200).accepted)
  assert.strictEqual(st.msg.d, 500)
  assert.strictEqual(st.lastRecv, 1000)
  assert.ok(h.accept(st, msg({ q: 6 }), 1300).accepted)
})

test('z mới: reset q và rung, nhận q nhỏ hơn', () => {
  const st = h.createState()
  h.accept(st, msg({ z: 1, q: 50, d: 100, i: 3 }), 0)
  assert.ok(!h.accept(st, msg({ z: 1, q: 49 }), 1).accepted)
  const r = h.accept(st, msg({ z: 2, q: 1, d: 100, i: 3 }), 2)
  assert.ok(r.accepted)
  assert.strictEqual(r.vibe, 'far')
  assert.strictEqual(st.msg.z, 2)
})

test('waiting / stale', () => {
  const st = h.createState()
  assert.strictEqual(h.view(st, 0).mode, 'waiting')
  h.accept(st, msg({ k: 0 }), 0)
  assert.strictEqual(h.view(st, 3000).mode, 'waiting')
  // Sau khi đã nhận khung idle rồi im lặng quá 3 s: báo stale nhất quán với isStale, nhưng vẫn im lặng (không rung)
  assert.strictEqual(h.isStale(st, 3001), true)
  assert.strictEqual(h.view(st, 3001).mode, 'stale')
  assert.strictEqual(h.tick(st, 3001), null)
  h.accept(st, msg({ q: 2 }), 1000)
  const v = h.view(st, 1500)
  assert.strictEqual(v.mode, 'nav')
  assert.strictEqual(v.arrow, '/common/arrows/m03.png')
  assert.strictEqual(v.distance, '500 m')
  assert.strictEqual(v.bottom, '12:45 · 4.2 km')
  assert.strictEqual(h.view(st, 4000).mode, 'nav')
  const s = h.view(st, 4001)
  assert.strictEqual(s.mode, 'stale')
  assert.strictEqual(s.arrow, null)
  assert.strictEqual(s.banner, 'MẤT DỮ LIỆU')
})

test('k=2/3 xóa mũi tên ngay dù m còn giá trị', () => {
  const st = h.createState()
  h.accept(st, msg({ q: 1 }), 0)
  assert.ok(h.view(st, 0).arrow)
  h.accept(st, msg({ q: 2, k: 2, m: 3 }), 100)
  let v = h.view(st, 100)
  assert.strictEqual(v.mode, 'recalc'); assert.strictEqual(v.arrow, null); assert.strictEqual(v.banner, 'Đang tính lại')
  h.accept(st, msg({ q: 3, k: 3, m: 12 }), 200)
  v = h.view(st, 200)
  assert.strictEqual(v.mode, 'arrived'); assert.strictEqual(v.arrow, null); assert.strictEqual(v.banner, 'Đã đến')
})

test('a > 10000 => mất dữ liệu, không rung', () => {
  const st = h.createState()
  assert.strictEqual(h.accept(st, msg({ q: 1, a: 10001, d: 40 }), 0).vibe, null)
  assert.strictEqual(h.view(st, 0).mode, 'stale')
  assert.strictEqual(h.view(st, 0).arrow, null)
  assert.strictEqual(h.tick(st, 0), null) // tin đầu đã cũ: không rung mất dữ liệu
  assert.strictEqual(h.accept(st, msg({ q: 2, a: 10000, d: 40 }), 1000).vibe, 'near')
  assert.strictEqual(h.view(st, 1000).mode, 'nav')
})

test('mất dữ liệu rung đúng 1 lần mỗi đợt; k!=1 hoặc chưa có tin thì không rung', () => {
  const st = h.createState()
  assert.strictEqual(h.tick(st, 9999), null)
  h.accept(st, msg(), 0)
  assert.strictEqual(h.tick(st, 3000), null)
  assert.strictEqual(h.tick(st, 3500), 'lost')
  assert.strictEqual(h.tick(st, 4000), null)
  h.accept(st, msg({ q: 2 }), 5000)
  assert.strictEqual(h.tick(st, 9000), 'lost')
  const st2 = h.createState()
  h.accept(st2, msg({ k: 3, m: 12 }), 0)
  assert.strictEqual(h.tick(st2, 9000), null)
})

test('rung: 200 m = far, 50 m = near, mỗi mức một lần cho mỗi i', () => {
  const st = h.createState()
  const seq = [[500, null], [300, null], [200, 'far'], [190, null], [120, null], [60, null], [50, 'near'], [40, null], [20, null]]
  seq.forEach(([d, expect], n) => {
    assert.strictEqual(h.accept(st, msg({ q: n + 1, d }), n * 1000).vibe, expect, 'd=' + d)
  })
})

test('rung: nhảy 210 -> 25 m chỉ rung near', () => {
  const st = h.createState()
  assert.strictEqual(h.accept(st, msg({ q: 1, d: 210 }), 0).vibe, null)
  assert.strictEqual(h.accept(st, msg({ q: 2, d: 25 }), 1).vibe, 'near')
  assert.strictEqual(h.accept(st, msg({ q: 3, d: 20 }), 2).vibe, null)
})

test('rung: quan sát đầu đã trong ngưỡng vẫn rung, mức khẩn nhất', () => {
  assert.strictEqual(h.accept(h.createState(), msg({ d: 150 }), 0).vibe, 'far')
  assert.strictEqual(h.accept(h.createState(), msg({ d: 40 }), 0).vibe, 'near')
})

test('rung: d=-1, m=0, k!=1 không rung; bước i mới reset', () => {
  const st = h.createState()
  assert.strictEqual(h.accept(st, msg({ q: 1, d: -1 }), 0).vibe, null)
  assert.strictEqual(h.view(st, 0).distance, '--')
  assert.strictEqual(h.accept(st, msg({ q: 2, m: 0, d: 30 }), 1).vibe, null)
  assert.strictEqual(h.accept(st, msg({ q: 3, k: 2, d: 30 }), 2).vibe, null)
  assert.strictEqual(h.accept(st, msg({ q: 4, d: 190, i: 1 }), 3).vibe, 'far')
  assert.strictEqual(h.accept(st, msg({ q: 5, d: 600, i: 2 }), 4).vibe, null)
  assert.strictEqual(h.accept(st, msg({ q: 6, d: 190, i: 2 }), 5).vibe, 'far')
})

test('FOLD_DIACRITICS và vòng xuyến', () => {
  const cfg = Object.assign({}, require('../src/common/config.js'), { FOLD_DIACRITICS: true })
  const st = h.createState(cfg)
  h.accept(st, msg({ m: 9, x: 2, s: 'Đường Lê Lợi' }), 0)
  assert.strictEqual(h.view(st, 0).street, 'Lối 2 · Duong Le Loi')
})

test('demo message hợp lệ với parser', () => {
  for (let i = 0; i < h.DEMO_LENGTH; i++) assert.ok(h.parsePayload(h.demoMessage(i, i + 1)))
})

test('đồng hồ lùi rồi tiến lại không khôi phục mũi tên (high-water mark)', () => {
  const st = h.createState()
  h.accept(st, msg({ q: 1 }), 1000)
  assert.strictEqual(h.view(st, 3000).mode, 'nav')
  assert.strictEqual(h.view(st, 2000).mode, 'stale') // lùi
  assert.strictEqual(h.view(st, 2500).mode, 'stale') // < mốc 3000 và < lúc nhận + 3000
  assert.strictEqual(h.view(st, 3900).mode, 'stale') // đã latch
  h.accept(st, msg({ q: 2 }), 4000)
  assert.strictEqual(h.view(st, 4000).mode, 'nav')
})

test('rung mất dữ liệu chỉ sau khi đã nhận tin dẫn đường còn tươi', () => {
  const st = h.createState()
  h.accept(st, msg({ q: 1, a: 20000 }), 0)
  assert.strictEqual(h.tick(st, 100), null)
  assert.strictEqual(h.invalidate(st), null)
  h.accept(st, msg({ q: 2 }), 1000)
  assert.strictEqual(h.tick(st, 5000), 'lost')
  assert.strictEqual(h.tick(st, 6000), null)
})

test('local demo 1 Hz không bao giờ báo MẤT DỮ LIỆU và đếm ngược khoảng cách mượt', () => {
  const cfg = require('../src/common/config.js')
  assert.ok(cfg.DEMO_TICK_MS < cfg.STALE_MS, 'DEMO_TICK_MS phải nhỏ hơn STALE_MS')
  const ticksPerStep = Math.max(1, Math.round(cfg.DEMO_STEP_MS / cfg.DEMO_TICK_MS))
  const st = h.createState(cfg)
  let q = 0
  let stepIdx = 0
  let sub = 0
  for (let t = 0; t <= 60000; t += 500) {
    if (t % cfg.DEMO_TICK_MS === 0) {
      q++
      const m = h.parsePayload(h.demoMessage(stepIdx, q, sub, ticksPerStep))
      assert.ok(m)
      assert.strictEqual(h.accept(st, m, t).accepted, true)
      sub++
      if (sub >= ticksPerStep) { sub = 0; stepIdx++ }
    }
    assert.strictEqual(h.tick(st, t), null, 'demo không được phát rung lost tại t=' + t)
    assert.notStrictEqual(h.view(st, t).mode, 'stale', 'demo không được báo stale tại t=' + t)
  }
  // Bước vòng xuyến (index 8) đếm ngược từ 260m xuống 30m và giữ cùng mã bước i
  const d0 = h.demoMessage(8, 1, 0, 4)
  const d3 = h.demoMessage(8, 4, 3, 4)
  assert.strictEqual(d0.i, d3.i)
  assert.ok(d0.d > 200 && d3.d <= 50, 'khoảng cách bước gần phải đi qua cả 2 ngưỡng rung')
})
