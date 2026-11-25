package dev.motonavhud.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Nhật ký sự kiện bền vững trong bộ nhớ riêng của app: `files/events.log` (+ `events.log.1`).
 * Đọc sau chuyến đi: `adb shell run-as dev.motonavhud.app cat files/events.log.1 files/events.log`.
 *
 * Dòng ghi trước khi [init] được gom vào bộ đệm nhỏ trong RAM (tối đa [PRE_INIT_CAP], bỏ dòng cũ nhất)
 * rồi ghi ra sau khi init; nếu init không bao giờ chạy thì bị bỏ. Không ghi nội dung notification.
 * Dọn dẹp: xoay vòng theo kích thước (256 KB, 1 bản sao) và khi init xoá file quá 7 ngày. Chỉ có đúng hai file này, không ghi gì khác.
 */
object EventLog {
    private const val PRE_INIT_CAP = 200
    private const val MAX_AGE_MS = 7L * 24 * 3600 * 1000

    fun isExpired(lastModifiedMs: Long, nowMs: Long, maxAgeMs: Long) = nowMs - lastModifiedMs > maxAgeMs
    private val lock = Any()
    private var writer: EventLogWriter? = null
    private var initStarted = false
    private val pre = ArrayDeque<Pair<Long, String>>()

    /** Idempotent; chỉ cần application context. Việc chạm đĩa nằm ngoài [lock] để callback BLE không bị chặn. */
    fun init(context: Context) {
        synchronized(lock) {
            if (initStarted) return
            initStarted = true
        }
        val dir = try {
            context.applicationContext.filesDir
        } catch (_: Throwable) {
            return // không có thư mục: tắt log, không ảnh hưởng service
        }
        try {
            val now = System.currentTimeMillis()
            listOf("events.log", "events.log.1").map { File(dir, it) }
                .filter { it.exists() && isExpired(it.lastModified(), now, MAX_AGE_MS) }.forEach { it.delete() }
        } catch (_: Throwable) {
        }
        val w = EventLogWriter(dir)
        // Dòng khởi động rồi đến bộ đệm, rồi mới công bố writer: thứ tự file luôn đúng.
        w.offer("--- process start pid=${android.os.Process.myPid()} v${BuildConfig.VERSION_NAME} ---")
        synchronized(lock) {
            pre.forEach { (t, s) -> w.offer(s, t) }
            pre.clear()
            writer = w
        }
    }

    fun log(text: String) {
        val now = System.currentTimeMillis()
        val w = synchronized(lock) {
            writer ?: run {
                if (pre.size >= PRE_INIT_CAP) pre.removeFirst()
                pre.addLast(now to text)
                return
            }
        }
        w.offer(text, now)
    }
}

/**
 * Ghi file không chặn caller: hàng đợi giới hạn + 1 thread daemon, ghi theo lô.
 * [clock] trả epoch ms (tiêm được để test). Mọi exception bị nuốt.
 */
class EventLogWriter(
    dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxBytes: Long = 256 * 1024,
    private val maxPending: Int = 500,
) {
    private val file = File(dir, "events.log")
    private val old = File(dir, "events.log.1")
    private val lock = Any()
    private val queue = ArrayDeque<Pair<Long, String>>()
    private var scheduled = false
    private val dedupe = PayloadDedupe()
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "EventLog").apply { isDaemon = true } }

    fun offer(text: String, atMs: Long = clock()) {
        try {
            synchronized(lock) {
                if (!dedupe.shouldPersist(text, atMs)) return
                if (queue.size >= maxPending) queue.removeFirst()
                queue.addLast(atMs to text)
                if (scheduled) return
                scheduled = true
            }
            executor.execute(::drain)
        } catch (_: Throwable) {
        }
    }

    /** Chờ ghi xong mọi dòng đang chờ (dùng cho test). */
    fun awaitIdle() {
        try {
            executor.submit {}.get()
        } catch (_: Throwable) {
        }
    }

    private fun drain() {
        while (true) {
            val batch = synchronized(lock) {
                if (queue.isEmpty()) {
                    scheduled = false
                    return
                }
                queue.toList().also { queue.clear() }
            }
            try {
                if (shouldRotate(file.length(), maxBytes)) {
                    old.delete()
                    file.renameTo(old)
                }
                val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
                val sb = StringBuilder()
                batch.forEach { (t, s) -> sb.append(formatLine(fmt, t, s)).append('\n') }
                file.appendText(sb.toString())
            } catch (_: Throwable) {
            }
        }
    }

    companion object {
        fun shouldRotate(currentBytes: Long, maxBytes: Long) = currentBytes > maxBytes

        fun formatLine(fmt: SimpleDateFormat, atMs: Long, text: String) = "${fmt.format(Date(atMs))} $text"
    }
}

/** Dòng `payload ` chỉ ghi khi các trường ngoài `q` và `a` (tuổi nguồn, tăng mỗi giây) đổi, hoặc đã quá [intervalMs] từ lần ghi trước. */
class PayloadDedupe(private val intervalMs: Long = 30_000) {
    private var lastKey: String? = null
    private var lastAt = 0L

    fun shouldPersist(text: String, nowMs: Long): Boolean {
        if (!text.startsWith("payload ")) return true
        val key = text.split(' ').filterNot { it.startsWith("q=") || it.startsWith("a=") }.joinToString(" ")
        if (key == lastKey && nowMs - lastAt < intervalMs) return false
        lastKey = key
        lastAt = nowMs
        return true
    }
}
