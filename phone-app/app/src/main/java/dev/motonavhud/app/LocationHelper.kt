package dev.motonavhud.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.location.LocationManagerCompat
import java.util.Locale
import java.util.concurrent.Executor

/** Vị trí hiện tại + geocoding bằng API nền tảng (không Play services). Mọi callback chạy trên main thread. */
object LocationHelper {
    const val PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION
    /** Android 12+ chỉ hiện hộp thoại khi xin chính xác kèm gần đúng trong cùng một yêu cầu. */
    val PERMISSIONS = arrayOf(PERMISSION, Manifest.permission.ACCESS_COARSE_LOCATION)
    private const val PROVIDER_TIMEOUT_MS = 8_000L
    private const val GEOCODE_TIMEOUT_MS = 8_000L
    /** Vị trí cũ hơn mức này không dùng làm điểm đến (người lái có thể đã di chuyển). */
    private const val MAX_CACHE_AGE_MS = 120_000L

    private val main = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { main.post(it) }

    private fun granted(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Chỉ cần một trong hai quyền (người dùng có thể chỉ cấp vị trí gần đúng). */
    fun hasPermission(ctx: Context): Boolean = PERMISSIONS.any { granted(ctx, it) }

    /** Quyền đã được kiểm tra bằng [hasPermission] ngay bên dưới; lint không theo dõi được qua hàm cục bộ. */
    @SuppressLint("MissingPermission")
    fun currentLocation(ctx: Context, onResult: (Pair<Double, Double>?) -> Unit) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        if (lm == null || !hasPermission(ctx)) return deliver(onResult, null)
        val providers = listOfNotNull(
            LocationManager.GPS_PROVIDER.takeIf { granted(ctx, PERMISSION) },
            LocationManager.NETWORK_PROVIDER,
        ).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return deliver(onResult, null)

        var done = false
        fun finish(r: Pair<Double, Double>?) {
            if (done) return
            done = true
            onResult(r)
        }
        fun freshCache(): Pair<Double, Double>? = providers.firstNotNullOfOrNull { p ->
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()
                ?.takeIf {
                    // elapsedRealtime không đổi khi chỉnh đồng hồ hệ thống; tuổi âm cũng bị loại.
                    val ageMs = (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000L
                    ageMs in 0..MAX_CACHE_AGE_MS
                }
                ?.let { it.latitude to it.longitude }
        }

        // Thử lần lượt từng provider (GPS trong nhà thường không có fix, mạng thì có); hết thì mới dùng cache còn mới.
        fun next(i: Int) {
            if (done) return
            if (i >= providers.size) return finish(freshCache())
            val cancel = CancellationSignal()
            var settled = false
            val timeout = Runnable {
                if (settled) return@Runnable
                settled = true
                cancel.cancel()
                next(i + 1)
            }
            main.postDelayed(timeout, PROVIDER_TIMEOUT_MS)
            try {
                LocationManagerCompat.getCurrentLocation(lm, providers[i], cancel, mainExecutor) { loc: Location? ->
                    if (settled || done) return@getCurrentLocation
                    settled = true
                    main.removeCallbacks(timeout)
                    if (loc != null) finish(loc.latitude to loc.longitude) else next(i + 1)
                }
            } catch (_: SecurityException) {
                settled = true
                main.removeCallbacks(timeout)
                finish(null)
            } catch (_: RuntimeException) {
                settled = true
                main.removeCallbacks(timeout)
                next(i + 1)
            }
        }
        next(0)
    }

    fun reverseGeocode(ctx: Context, lat: Double, lng: Double, callback: (String?) -> Unit) {
        val onResult = guarded(callback)
        if (!Geocoder.isPresent()) return deliver(onResult, null)
        val geocoder = Geocoder(ctx, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = deliver(onResult, addresses.firstOrNull()?.let(::line))
                    override fun onError(errorMessage: String?) = deliver(onResult, null)
                })
            } catch (_: RuntimeException) {
                deliver(onResult, null)
            }
        } else {
            background(onResult) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()?.let(::line) }.getOrNull()
            }
        }
    }

    fun forwardGeocode(ctx: Context, address: String, callback: (Pair<Double, Double>?) -> Unit) {
        val onResult = guarded(callback)
        if (!Geocoder.isPresent()) return deliver(onResult, null)
        val geocoder = Geocoder(ctx, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                geocoder.getFromLocationName(address, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) =
                        deliver(onResult, addresses.firstOrNull()?.let { it.latitude to it.longitude })
                    override fun onError(errorMessage: String?) = deliver(onResult, null)
                })
            } catch (_: RuntimeException) {
                deliver(onResult, null)
            }
        } else {
            background(onResult) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocationName(address, 1)?.firstOrNull()?.let { it.latitude to it.longitude } }.getOrNull()
            }
        }
    }

    private fun line(a: Address): String? {
        val joined = (0..a.maxAddressLineIndex).mapNotNull { a.getAddressLine(it) }.joinToString(", ")
        return joined.ifBlank { a.getAddressLine(0) }?.takeIf { it.isNotBlank() }
    }

    /** Bọc callback: chạy đúng một lần trên main thread, hết hạn thì trả null (backend geocoder có thể treo vô hạn). */
    private fun <T> guarded(cb: (T?) -> Unit): (T?) -> Unit {
        var done = false
        val timeout = Runnable {
            if (done) return@Runnable
            done = true
            cb(null)
        }
        main.postDelayed(timeout, GEOCODE_TIMEOUT_MS)
        return { v ->
            main.post {
                if (!done) {
                    done = true
                    main.removeCallbacks(timeout)
                    cb(v)
                }
            }
        }
    }

    private fun <T> deliver(cb: (T?) -> Unit, v: T?) {
        if (Looper.myLooper() == Looper.getMainLooper()) cb(v) else main.post { cb(v) }
    }

    /** Chạy [work] trên thread nền, trả kết quả trên main thread. */
    private fun <T> background(cb: (T?) -> Unit, work: () -> T?) {
        Thread { val r = work(); main.post { cb(r) } }.start()
    }
}
