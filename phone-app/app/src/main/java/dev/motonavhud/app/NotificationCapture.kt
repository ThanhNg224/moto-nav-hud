package dev.motonavhud.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ghi mỗi notification Maps khác biệt ra `getExternalFilesDir("captures")` (JSON + icon PNG) để
 * `adb pull` làm fixture thật cho parser (spike S4). I/O chạy trên một luồng nền với hàng đợi giới hạn
 * (đầy thì bỏ qua), giới hạn số bộ capture tính trên các tệp đang có trên đĩa.
 */
object NotificationCapture {
    private const val MAX_SETS = 500
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8), ThreadPoolExecutor.DiscardPolicy(),
    )
    private val seq = AtomicInteger()
    private var lastHash: String? = null
    private val seenHashes = HashSet<String>()

    fun enqueue(ctx: Context, sbn: StatusBarNotification) {
        val app = ctx.applicationContext
        executor.execute { runCatching { capture(app, sbn) } }
    }

    private fun dir(ctx: Context): File? = ctx.getExternalFilesDir("captures")?.also { it.mkdirs() }

    fun count(ctx: Context): Int = dir(ctx)?.listFiles { f -> f.name.endsWith(".json") }?.size ?: 0

    /** Xóa toàn bộ capture đã giữ; trả số tệp đã xóa. */
    @Synchronized
    fun clear(ctx: Context): Int {
        // Cùng khóa với capture(): liệt kê, xóa và reset dedupe không đua với việc ghi.
        val files = dir(ctx)?.listFiles().orEmpty()
        lastHash = null
        seenHashes.clear()
        return files.count { it.delete() }
    }

    @Synchronized
    private fun capture(ctx: Context, sbn: StatusBarNotification) {
        val dir = dir(ctx) ?: return
        if (count(ctx) >= MAX_SETS) return
        val n = sbn.notification
        val json = JSONObject()
        json.put("package", sbn.packageName)
        json.put("tag", sbn.tag ?: JSONObject.NULL)
        json.put("id", sbn.id)
        json.put("channel", n.channelId ?: JSONObject.NULL)
        json.put("category", n.category ?: JSONObject.NULL)
        json.put("flags", n.flags)
        json.put("ongoing", (n.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
        json.put("hasContentView", n.contentView != null)
        json.put("hasBigContentView", n.bigContentView != null)
        json.put("extras", bundleToJson(n.extras))
        val actions = JSONArray()
        n.actions?.forEach { actions.put(it.title?.toString()) }
        json.put("actions", actions)
        val large = iconBytes(ctx, n.getLargeIcon())
        val small = iconBytes(ctx, n.smallIcon)
        // postTime đổi theo từng lần đăng: giữ trong JSON lưu nhưng không tham gia dedupe.
        val hash = dedupeKey(json, large, small)
        json.put("postTime", sbn.postTime)
        val text = json.toString(2)
        if (hash == lastHash || hash in seenHashes) return

        val base = "capture-%d-%03d".format(System.currentTimeMillis(), seq.incrementAndGet() % 1000)
        val written = ArrayList<File>()
        try {
            File(dir, "$base.json").also { written += it }.writeText(text)
            large?.let { File(dir, "$base-large.png").also { f -> written += f }.writeBytes(it) }
            small?.let { File(dir, "$base-small.png").also { f -> written += f }.writeBytes(it) }
        } catch (e: Exception) {
            written.forEach { it.delete() }
            throw e
        }
        // Chỉ ghi nhận dedupe sau khi toàn bộ bộ tệp đã ghi thành công.
        lastHash = hash
        seenHashes.add(hash)
    }

    /** Khóa dedupe: JSON (không có postTime) + icon. */
    internal fun dedupeKey(json: JSONObject, large: ByteArray?, small: ByteArray?): String {
        val text = JSONObject(json.toString()).apply { remove("postTime") }.toString(2)
        return sha(text.toByteArray() + (large ?: ByteArray(0)) + (small ?: ByteArray(0)))
    }

    private fun bundleToJson(b: Bundle?): JSONObject {
        val o = JSONObject()
        if (b == null) return o
        for (key in b.keySet().sorted()) {
            @Suppress("DEPRECATION")
            o.put(key, stringify(b.get(key)))
        }
        return o
    }

    private fun stringify(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Bundle -> bundleToJson(v)
        is Array<*> -> JSONArray(v.map { stringify(it) })
        is IntArray -> v.contentToString()
        is LongArray -> v.contentToString()
        is CharSequence -> v.toString()
        else -> v.toString()
    }

    private fun iconBytes(ctx: Context, icon: Icon?): ByteArray? {
        val d = icon?.loadDrawable(ctx) ?: return null
        val w = d.intrinsicWidth.coerceIn(1, 512)
        val h = d.intrinsicHeight.coerceIn(1, 512)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }
}
