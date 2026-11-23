package dev.motonavhud.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import dev.motonavhud.core.NavState
import org.json.JSONObject

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var serviceBadge: TextView
    private lateinit var watchBadge: TextView
    private lateinit var bleLine: TextView
    private lateinit var permLine: TextView
    private lateinit var warningCard: TextView
    private lateinit var navSummary: TextView
    private lateinit var payload: TextView
    private lateinit var startStop: Button
    private lateinit var demo: Button
    private lateinit var capture: Switch
    private lateinit var clearCaptures: Button
    private lateinit var token: EditText
    private lateinit var advToggle: TextView
    private lateinit var advBox: LinearLayout
    private var advOpen = false
    private var advToggledManually = false
    private var dark = false
    private var captureCount = -1
    private var pendingAfterPermission: (() -> Unit)? = null

    private val refresh: () -> Unit = { main.post { render() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = if (dark) Color.parseColor("#0F1216") else Color.parseColor("#F4F6F9")
        val cardBg = if (dark) Color.parseColor("#1A2028") else Color.parseColor("#FFFFFF")
        val ink = if (dark) Color.parseColor("#F1F5F9") else Color.parseColor("#0F172A")
        val muted = if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#475569")

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            fitsSystemWindows = true
        }

        // Thanh tiêu đề + nhãn trạng thái
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, dp(16), pad, dp(8))
        }
        header.addView(TextView(this).apply {
            text = "Moto Nav HUD"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ink)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        serviceBadge = TextView(this).apply {
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        header.addView(serviceBadge)
        root.addView(header)

        // Vùng cuộn thông tin
        val scroll = ScrollView(this)
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8), pad, dp(16))
        }

        // Thẻ trạng thái kết nối & quyền
        val statusCard = card(cardBg).apply {
            val topRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            bleLine = TextView(this@MainActivity).apply {
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ink)
            }
            topRow.addView(bleLine, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            watchBadge = TextView(this@MainActivity).apply {
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(10), dp(4), dp(10), dp(4))
            }
            topRow.addView(watchBadge)
            addView(topRow)
            addView(space(8))
            permLine = TextView(this@MainActivity).apply {
                textSize = 13f
                setTextColor(muted)
                setLineSpacing(dp(2).toFloat(), 1f)
            }
            addView(permLine)
        }
        info.addView(statusCard)
        info.addView(space(12))

        // Thẻ cảnh báo (chỉ hiện khi có cảnh báo)
        warningCard = TextView(this).apply {
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = pill(Color.parseColor("#B91C1C"), 14)
            visibility = View.GONE
        }
        info.addView(warningCard)

        // Thẻ xem trước chỉ dẫn đang gửi tới đồng hồ
        val navCard = card(cardBg).apply {
            addView(TextView(this@MainActivity).apply {
                text = "Đang gửi tới đồng hồ"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(muted)
            })
            addView(space(6))
            navSummary = TextView(this@MainActivity).apply {
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ink)
            }
            addView(navSummary)
            addView(space(8))
            payload = TextView(this@MainActivity).apply {
                typeface = Typeface.MONOSPACE
                textSize = 11.5f
                setTextColor(muted)
            }
            addView(payload)
        }
        info.addView(navCard)
        info.addView(space(12))

        // Thẻ Cài đặt & gỡ lỗi (thu gọn mặc định khi đã đủ quyền)
        val advCard = card(cardBg)
        advToggle = TextView(this).apply {
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ink)
            setPadding(0, dp(2), 0, dp(2))
            setOnClickListener {
                advToggledManually = true
                advOpen = !advOpen
                updateAdvVisibility()
                if (advOpen) refreshCaptureCount()
            }
        }
        advCard.addView(advToggle)

        advBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        advBox.addView(space(12))

        capture = Switch(this).apply {
            text = "Capture notification Maps (S4)"
            setTextColor(ink)
            isChecked = Prefs.isCapture(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.setCapture(this@MainActivity, on) }
        }
        advBox.addView(capture)
        advBox.addView(space(8))

        val tokenRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        token = EditText(this).apply {
            hint = "Mã ghép p (1-8 ký tự)"
            setText(Prefs.token(this@MainActivity))
            setTextColor(ink)
            setHintTextColor(muted)
            setSingleLine()
        }
        val saveToken = subtleButton("Lưu mã", ink, if (dark) "#2A3340" else "#E2E8F0") {
            val ok = Prefs.setToken(this@MainActivity, token.text.toString().trim())
            Toast.makeText(this@MainActivity, if (ok) "Đã lưu mã ghép" else "Mã không hợp lệ", Toast.LENGTH_SHORT).show()
            if (!ok) token.setText(Prefs.token(this@MainActivity))
        }
        tokenRow.addView(token, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tokenRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })
        tokenRow.addView(saveToken, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)))
        advBox.addView(tokenRow)
        advBox.addView(space(8))

        val btnBg = if (dark) "#2A3340" else "#E2E8F0"
        val perms = subtleButton("Cấp quyền Bluetooth / thông báo", ink, btnBg) {
            requestPerms(all = true) { render() }
        }
        val access = subtleButton("Mở cài đặt Notification access", ink, btnBg) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        clearCaptures = subtleButton("Xóa các capture đã lưu", ink, btnBg) {
            Thread {
                val n = NotificationCapture.clear(this@MainActivity)
                main.post {
                    captureCount = 0
                    Toast.makeText(this@MainActivity, "Đã xóa $n tệp capture", Toast.LENGTH_SHORT).show()
                    render()
                }
            }.start()
        }
        listOf(perms, access, clearCaptures).forEach {
            advBox.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
            advBox.addView(space(8))
        }
        advBox.addView(TextView(this).apply {
            textSize = 12f
            setTextColor(muted)
            text = "Pin & nền: vào Cài đặt > Pin > Moto Nav HUD > Không hạn chế (tắt tối ưu hóa pin). " +
                "Trên Xiaomi/HyperOS bật Tự khởi động và khóa app trong đa nhiệm."
        })

        advCard.addView(advBox)
        info.addView(advCard)

        scroll.addView(info)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Vùng thao tác chính ở nửa dưới màn hình (thumb zone)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8), pad, dp(16))
            setBackgroundColor(if (dark) Color.parseColor("#141920") else Color.parseColor("#FFFFFF"))
        }
        demo = Button(this).apply {
            isAllCaps = false
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { toggleDemo() }
        }
        startStop = Button(this).apply {
            isAllCaps = false
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setOnClickListener { toggleService() }
        }
        actions.addView(demo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        actions.addView(space(8))
        actions.addView(startStop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        root.addView(actions)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        NavBus.addListener(refresh)
        refreshCaptureCount()
        render()
    }

    private fun refreshCaptureCount() {
        Thread {
            val n = NotificationCapture.count(this@MainActivity)
            main.post {
                if (captureCount != n) {
                    captureCount = n
                    render()
                }
            }
        }.start()
    }

    override fun onPause() {
        NavBus.removeListener(refresh)
        super.onPause()
    }

    private fun updateAdvVisibility() {
        advBox.visibility = if (advOpen) View.VISIBLE else View.GONE
        advToggle.text = if (advOpen) "Cài đặt & gỡ lỗi ▾" else "Cài đặt & gỡ lỗi ▸"
    }

    private fun render() {
        val access = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val btOk = missingBluetoothPermissions().isEmpty()
        val notifOk = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!advToggledManually) {
            advOpen = !access || !btOk
            updateAdvVisibility()
        }

        val running = NavBus.serviceRunning
        serviceBadge.text = if (running) "ĐANG CHẠY" else "ĐÃ DỪNG"
        serviceBadge.setTextColor(if (running) Color.parseColor("#064E3B") else Color.parseColor("#CBD5E1"))
        serviceBadge.background = pill(if (running) Color.parseColor("#34D399") else Color.parseColor("#334155"), 99)

        val subs = NavBus.subscribers
        watchBadge.text = if (subs > 0) "Đồng hồ: $subs đã nối" else "Chưa nối đồng hồ"
        watchBadge.setTextColor(if (subs > 0) Color.parseColor("#064E3B") else Color.parseColor("#FDE68A"))
        watchBadge.background = pill(if (subs > 0) Color.parseColor("#6EE7B7") else Color.parseColor("#78350F"), 99)

        bleLine.text = "BLE: ${NavBus.bleStatus}"
        permLine.text = buildString {
            append("Notification access: ").append(if (access) "đã bật" else "CHƯA BẬT")
            append("  ·  Bluetooth: ").append(if (btOk) "đủ" else "THIẾU")
            append("\nThông báo trạng thái: ").append(if (notifOk) "đã cấp" else "chưa cấp (tùy chọn)")
        }

        if (NavBus.warning.isNotEmpty()) {
            warningCard.text = "CẢNH BÁO: ${NavBus.warning}"
            warningCard.visibility = View.VISIBLE
        } else {
            warningCard.visibility = View.GONE
        }

        navSummary.text = summarizePayload(NavBus.lastPayload, NavBus.demo)
        payload.text = NavBus.lastPayload.ifEmpty { "(chưa có payload)" }
        clearCaptures.text = if (captureCount >= 0) "Xóa các capture đã lưu ($captureCount)" else "Xóa các capture đã lưu"

        if (NavBus.demo) {
            demo.text = "Dừng Demo"
            demo.setTextColor(Color.parseColor("#FEF3C7"))
            demo.background = pill(Color.parseColor("#B45309"), 14)
        } else {
            demo.text = "Demo (giả lập chỉ đường, 4 s/bước)"
            demo.setTextColor(if (dark) Color.parseColor("#E2E8F0") else Color.parseColor("#0F172A"))
            demo.background = pill(if (dark) Color.parseColor("#2A3340") else Color.parseColor("#E2E8F0"), 14)
        }

        if (running) {
            startStop.text = "Dừng service"
            startStop.background = pill(Color.parseColor("#DC2626"), 14)
        } else {
            startStop.text = "Bắt đầu service"
            startStop.background = pill(Color.parseColor("#059669"), 14)
        }
    }

    private fun summarizePayload(raw: String, isDemo: Boolean): String {
        if (raw.isEmpty()) return "Chưa phát dữ liệu — bấm Bắt đầu service"
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return raw
        val k = o.optInt("k", 0)
        val prefix = if (isDemo) "[DEMO] " else ""
        return when (k) {
            NavState.K_IDLE -> "${prefix}Chờ chỉ đường từ Google Maps"
            NavState.K_REROUTING -> "${prefix}Đang tính lại tuyến đường"
            NavState.K_ARRIVED -> "${prefix}Đã đến nơi"
            else -> {
                val m = o.optInt("m", 0)
                val d = o.optInt("d", -1)
                val s = o.optString("s", "")
                val e = o.optString("e", "")
                val r = o.optInt("r", -1)
                val dist = when {
                    d < 0 -> "--"
                    d < 1000 -> "$d m"
                    else -> "%.1f km".format(d / 1000.0)
                }
                val rem = if (r >= 0) (if (r < 1000) "$r m" else "%.1f km".format(r / 1000.0)) else ""
                val tail = listOf(s, e, rem).filter { it.isNotEmpty() }.joinToString(" · ")
                "$prefix${maneuverLabel(m)} · $dist" + if (tail.isNotEmpty()) "\n$tail" else ""
            }
        }
    }

    private fun maneuverLabel(m: Int) = when (m) {
        1 -> "Đi thẳng"; 2 -> "Chếch trái"; 3 -> "Rẽ trái"; 4 -> "Gắt trái"
        5 -> "Chếch phải"; 6 -> "Rẽ phải"; 7 -> "Gắt phải"; 8 -> "Quay đầu"
        9 -> "Vòng xuyến"; 10 -> "Giữ trái"; 11 -> "Giữ phải"; 12 -> "Đích"; 13 -> "Xuất phát"
        else -> "Dẫn đường"
    }

    private fun card(bgColor: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = pill(bgColor, 16)
    }

    private fun subtleButton(label: String, textColor: Int, bgHex: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13.5f
        setTextColor(textColor)
        background = pill(Color.parseColor(bgHex), 12)
        setOnClickListener { onClick() }
    }

    private fun pill(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun toggleService() {
        if (NavBus.serviceRunning) {
            NavForegroundService.stop(this)
        } else {
            requestPerms { NavForegroundService.start(this) }
        }
    }

    private fun toggleDemo() {
        if (NavBus.demo) {
            NavBus.demo = false
            NavLog.i("demo off")
            render()
            return
        }
        requestPerms {
            if (!NavBus.serviceRunning) NavForegroundService.start(this)
            NavBus.demo = true
            NavLog.i("demo on")
            render()
        }
    }

    private fun missingPermissions(): List<String> {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            need += Manifest.permission.BLUETOOTH_ADVERTISE
            need += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        return need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }

    /** Chỉ quyền Bluetooth là bắt buộc để chạy; POST_NOTIFICATIONS tùy chọn và có nút riêng. */
    private fun missingBluetoothPermissions() = missingPermissions().filter { it != Manifest.permission.POST_NOTIFICATIONS }

    private fun requestPerms(all: Boolean = false, then: () -> Unit) {
        val missing = if (all) missingPermissions() else missingBluetoothPermissions()
        if (missing.isEmpty()) {
            then()
        } else {
            pendingAfterPermission = then
            requestPermissions(missing.toTypedArray(), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val then = pendingAfterPermission
        pendingAfterPermission = null
        if (then != null && missingBluetoothPermissions().isEmpty()) then()
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
}
