// Rung bằng vibrator.start (task hủy được) + vibrator.stop(id). api = module @system.vibrator (hoặc mock khi test).
var L = require('./log.js')
var PATTERNS = {
  far: { duration: 150, interval: 250, count: 2, mode: 'short' },
  near: { duration: 500, interval: 400, count: 3, mode: 'long' },
  lost: { duration: 600, interval: 100, count: 1, mode: 'long' }
}

function patternMs(p) {
  return p.duration * p.count + p.interval * Math.max(0, p.count - 1)
}

function create(api, onError) {
  var gen = 0
  var taskId = null
  var taskEndsAt = 0
  var pending = false  // đã gọi start nhưng id chưa về (Vela trả 205 nếu start tiếp khi còn task)
  var want = null      // mẫu yêu cầu mới nhất trong lúc start trước chưa có id
  var curKind = ''
  var pendUntil = 0    // start đã gửi mà chưa có id: coi là đang rung tới mốc này (ms)
  var retryTimer = null
  function err(s) { try { if (onError) onError(s) } catch (e) {} }

  function clearRetry() { if (retryTimer) { clearTimeout(retryTimer); retryTimer = null } }
  function clearTask() { taskId = null; taskEndsAt = 0; clearRetry() }

  // stop(id) trả false/ném lỗi khi task còn chạy hoặc đã tự kết thúc từ trước: chỉ chờ nếu chưa quá hạn tự kết thúc của mẫu
  function tryStop(id) {
    try { return api.stop(id) !== false } catch (e) { err('vib stop ' + e); return false }
  }
  function release() {
    if (taskId == null) return true
    if (tryStop(taskId) || Date.now() >= taskEndsAt) { clearTask(); return true }
    schedule(0)
    return false
  }
  function schedule(n) {
    if (retryTimer) return
    retryTimer = setTimeout(function () {
      retryTimer = null
      if (taskId == null) { flush(); return }
      if (tryStop(taskId) || Date.now() >= taskEndsAt || n >= 20) { clearTask(); flush() } else schedule(n + 1)
    }, 150)
  }

  function cancel() {
    gen++
    want = null
    if (curKind) L.log('vib stop', curKind)
    curKind = ''
    release()
  }

  // Chỉ hủy mẫu rẽ; để xung "lost" chạy hết
  function cancelTurn() { if (curKind !== 'lost') cancel() }

  function flush() {
    if (!want || pending) return
    if (!release()) return
    var k = want
    want = null
    start(k)
  }

  function start(kind) {
    var p = PATTERNS[kind]
    var my = gen
    pendUntil = Date.now() + patternMs(p) + 250
    pending = true
    curKind = kind
    L.log('vib start', kind)
    try {
      api.start({
        duration: p.duration, interval: p.interval, count: p.count,
        success: function (d) {
          pending = false
          var endsAt = Date.now() + patternMs(p) + 250
          if (my === gen) { taskId = d.id; taskEndsAt = endsAt }
          else if (tryStop(d.id)) flush()
          else { taskId = d.id; taskEndsAt = endsAt; schedule(0) }
        },
        fail: function (data, code) {
          pending = false
          err('vib ' + (code != null ? code : '?'))
          if (my === gen) { try { api.vibrate({ mode: p.mode }) } catch (e) {} } else flush()
        }
      })
    } catch (e) {
      pending = false
      err('vib ' + e)
      try { api.vibrate({ mode: p.mode }) } catch (e2) {}
    }
  }

  function play(kind) {
    if (!PATTERNS[kind]) return
    cancel()
    if (pending || taskId != null) { want = kind; curKind = kind; return }
    start(kind)
  }

  // true khi còn có thể đang rung: mẫu xếp hàng, start chưa có id (trong hạn), hoặc task native chưa stop được và chưa quá hạn
  function active() {
    var now = Date.now()
    return !!want || (pending && now < pendUntil) || (taskId != null && now < taskEndsAt)
  }

  return { play: play, cancel: cancel, cancelTurn: cancelTurn, active: active }
}

module.exports = { create: create }
