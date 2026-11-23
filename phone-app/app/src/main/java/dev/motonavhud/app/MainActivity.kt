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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import dev.motonavhud.core.NavState

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var serviceBadge: TextView
    private lateinit var arrowPreview: ArrowPreviewView
    private lateinit var navDist: TextView
    private lateinit var navTitle: TextView
    private lateinit var navMeta: TextView
    private lateinit var setupCard: LinearLayout
    private lateinit var setupText: TextView
    private lateinit var setupBtn: Button
    private lateinit var watchValue: TextView
    private lateinit var watchSub: TextView
    private lateinit var mapsValue: TextView
    private lateinit var mapsSub: TextView
    private lateinit var warningCard: TextView
    private lateinit var payload: TextView
    private lateinit var startStop: Button
    private lateinit var demo: Button
    private lateinit var advBtn: Button
    private lateinit var advCard: LinearLayout
    private lateinit var autoStart: Switch
    private lateinit var capture: Switch
    private lateinit var clearCaptures: Button
    private lateinit var token: EditText
    private val autoStopChips = LinkedHashMap<Int, Button>()
    private var advOpen = false
    private var dark = false
    private var captureCount = -1
    private var pendingAfterPermission: (() -> Unit)? = null

    private val refresh: () -> Unit = { main.post { render() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val bg = if (dark) Color.parseColor("#0B0E14") else Color.parseColor("#F1F5F9")
        val cardBg = if (dark) Color.parseColor("#151B24") else Color.parseColor("#FFFFFF")
        val border = if (dark) Color.parseColor("#243042") else Color.parseColor("#E2E8F0")
        val ink = if (dark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        val muted = if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
        val dividerColor = if (dark) Color.parseColor("#243042") else Color.parseColor("#E2E8F0")

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            fitsSystemWindows = true
        }

        // Header: logo MotoNav + tiêu đề thân thiện + badge trạng thái
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, dp(14), pad, dp(10))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(38), dp(38)))
        header.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(12), 1) })
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(TextView(this).apply {
            text = "MotoNav"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ink)
        })
        titleCol.addView(TextView(this).apply {
            text = "Chỉ đường trên Watch S5 · v0.3.1"
            textSize = 12f
            setTextColor(muted)
        })
        header.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        serviceBadge = TextView(this).apply {
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        header.addView(serviceBadge)
        root.addView(header)

        // Vùng cuộn chính
        val scroll = ScrollView(this)
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(4), pad, dp(16))
        }

        // Thẻ mô phỏng mặt đồng hồ trực tiếp
        val heroCard = card(cardBg, border).apply {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            arrowPreview = ArrowPreviewView(this@MainActivity)
            row.addView(arrowPreview, LinearLayout.LayoutParams(dp(86), dp(86)))
            row.addView(View(this@MainActivity).apply { layoutParams = LinearLayout.LayoutParams(dp(16), 1) })
            val textCol = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            navDist = TextView(this@MainActivity).apply {
                textSize = 29f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ink)
            }
            navTitle = TextView(this@MainActivity).apply {
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ink)
                maxLines = 2
            }
            navMeta = TextView(this@MainActivity).apply {
                textSize = 13f
                setTextColor(muted)
            }
            textCol.addView(navDist)
            textCol.addView(navTitle)
            textCol.addView(space(2))
            textCol.addView(navMeta)
            row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(row)
        }
        info.addView(heroCard)
        info.addView(space(12))

        // Thẻ hướng dẫn cấp quyền 1 chạm (chỉ hiện khi thiếu quyền Bluetooth hoặc đọc thông báo)
        setupCard = card(
            if (dark) Color.parseColor("#2A1F08") else Color.parseColor("#FEF3C7"),
            if (dark) Color.parseColor("#854D0E") else Color.parseColor("#F59E0B"),
        ).apply {
            visibility = View.GONE
            setupText = TextView(this@MainActivity).apply {
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (dark) Color.parseColor("#FDE68A") else Color.parseColor("#92400E"))
            }
            setupBtn = Button(this@MainActivity).apply {
                isAllCaps = false
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = pill(Color.parseColor("#D97706"), 12)
            }
            addView(setupText)
            addView(space(10))
            addView(setupBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
        }
        info.addView(setupCard)

        // Cảnh báo (chỉ hiện khi có)
        warningCard = TextView(this).apply {
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = pill(Color.parseColor("#B91C1C"), 14)
            visibility = View.GONE
        }
        info.addView(warningCard)

        // Lưới 2 cột trạng thái: Đồng hồ S5 | Google Maps
        val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val watchCard = card(cardBg, border).apply {
            addView(label("ĐỒNG HỒ S5", muted))
            addView(space(4))
            watchValue = TextView(this@MainActivity).apply { textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(ink) }
            watchSub = TextView(this@MainActivity).apply { textSize = 12f; setTextColor(muted); maxLines = 2 }
            addView(watchValue)
            addView(space(2))
            addView(watchSub)
        }
        val mapsCard = card(cardBg, border).apply {
            addView(label("GOOGLE MAPS", muted))
            addView(space(4))
            mapsValue = TextView(this@MainActivity).apply { textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(ink) }
            mapsSub = TextView(this@MainActivity).apply { textSize = 12f; setTextColor(muted); maxLines = 2 }
            addView(mapsValue)
            addView(space(2))
            addView(mapsSub)
        }
        grid.addView(watchCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        grid.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(10), 1) })
        grid.addView(mapsCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        info.addView(grid)
        info.addView(space(12))

        // Thẻ Cài đặt
        advCard = card(cardBg, border).apply {
            addView(label("TỰ ĐỘNG HÓA", muted))
            addView(space(8))
            autoStart = Switch(this@MainActivity).apply {
                text = "Tự bật khi mở Google Maps"
                textSize = 14.5f
                setTextColor(ink)
                isChecked = Prefs.isAutoStart(this@MainActivity)
                setOnCheckedChangeListener { _, on ->
                    Prefs.setAutoStart(this@MainActivity, on)
                    NavTileService.requestRefresh(this@MainActivity)
                    render()
                }
            }
            addView(autoStart)
            addView(TextView(this@MainActivity).apply {
                text = "Tự kết nối đồng hồ ngay khi Google Maps bắt đầu dẫn đường."
                textSize = 12f
                setTextColor(muted)
            })
            addView(space(14))

            addView(label("TỰ DỪNG KHI CHỜ (TIẾT KIỆM PIN)", muted))
            addView(space(8))
            val chipRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            Prefs.AUTO_STOP_OPTIONS.forEachIndexed { idx, min ->
                if (idx > 0) chipRow.addView(View(this@MainActivity).apply { layoutParams = LinearLayout.LayoutParams(dp(6), 1) })
                val chip = Button(this@MainActivity).apply {
                    text = if (min == 0) "Tắt" else "${min}p"
                    isAllCaps = false
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, 0)
                    setOnClickListener {
                        Prefs.setAutoStopMinutes(this@MainActivity, min)
                        render()
                    }
                }
                autoStopChips[min] = chip
                chipRow.addView(chip, LinearLayout.LayoutParams(0, dp(38), 1f))
            }
            addView(chipRow)

            addView(space(16))
            addView(divider(dividerColor))
            addView(space(14))
            addView(label("QUYỀN & CHẠY NỀN", muted))
            addView(space(8))

            val btnBg = if (dark) "#1F2937" else "#E2E8F0"
            val perms = subtleButton("Cấp quyền Bluetooth & thông báo", ink, btnBg) {
                requestPerms(all = true) { render() }
            }
            val access = subtleButton("Quyền đọc chỉ đường (Notification access)", ink, btnBg) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            val battery = subtleButton("Cho phép chạy nền (tắt tối ưu pin)", ink, btnBg) {
                runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    .onFailure { Toast.makeText(this@MainActivity, "Mở Cài đặt > Pin > MotoNav", Toast.LENGTH_SHORT).show() }
            }
            listOf(perms, access, battery).forEach {
                addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
                addView(space(8))
            }

            addView(space(8))
            addView(divider(dividerColor))
            addView(space(14))
            addView(label("NÂNG CAO & GỠ LỖI", muted))
            addView(space(8))

            capture = Switch(this@MainActivity).apply {
                text = "Ghi mẫu thông báo Maps (gỡ lỗi)"
                setTextColor(ink)
                isChecked = Prefs.isCapture(this@MainActivity)
                setOnCheckedChangeListener { _, on -> Prefs.setCapture(this@MainActivity, on) }
            }
            addView(capture)
            addView(space(8))

            clearCaptures = subtleButton("Xóa mẫu đã lưu", ink, btnBg) {
                Thread {
                    val n = NotificationCapture.clear(this@MainActivity)
                    main.post {
                        captureCount = 0
                        Toast.makeText(this@MainActivity, "Đã xóa $n tệp mẫu", Toast.LENGTH_SHORT).show()
                        render()
                    }
                }.start()
            }
            addView(clearCaptures, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
            addView(space(10))

            val tokenRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            token = EditText(this@MainActivity).apply {
                hint = "Mã ghép đồng hồ (1-8 ký tự)"
                setText(Prefs.token(this@MainActivity))
                setTextColor(ink)
                setHintTextColor(muted)
                setSingleLine()
            }
            val saveToken = subtleButton("Lưu mã", ink, btnBg) {
                val ok = Prefs.setToken(this@MainActivity, token.text.toString().trim())
                Toast.makeText(this@MainActivity, if (ok) "Đã lưu mã ghép" else "Mã không hợp lệ (1-8 chữ/số)", Toast.LENGTH_SHORT).show()
                if (!ok) token.setText(Prefs.token(this@MainActivity))
            }
            tokenRow.addView(token, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            tokenRow.addView(View(this@MainActivity).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })
            tokenRow.addView(saveToken, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)))
            addView(tokenRow)
            addView(space(10))

            addView(label("GÓI TIN GẦN NHẤT", muted))
            addView(space(4))
            payload = TextView(this@MainActivity).apply {
                typeface = Typeface.MONOSPACE
                textSize = 11f
                setTextColor(muted)
            }
            addView(payload)
            addView(space(8))
            addView(TextView(this@MainActivity).apply {
                textSize = 11.5f
                setTextColor(muted)
                text = "Mẹo: thêm ô \"MotoNav\" vào thanh kéo nhanh (Quick Settings) để bật/tắt 1 chạm trước khi chạy xe."
            })
        }
        info.addView(advCard)

        scroll.addView(info)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Vùng thao tác chính ở nửa dưới màn hình (thumb zone)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(10), pad, dp(16))
            setBackgroundColor(if (dark) Color.parseColor("#11161F") else Color.parseColor("#FFFFFF"))
        }
        val secRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        demo = Button(this).apply {
            isAllCaps = false
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { toggleDemo() }
        }
        advBtn = Button(this).apply {
            isAllCaps = false
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setOnClickListener {
                advOpen = !advOpen
                if (advOpen) refreshCaptureCount()
                render()
            }
        }
        secRow.addView(demo, LinearLayout.LayoutParams(0, dp(46), 1.3f))
        secRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })
        secRow.addView(advBtn, LinearLayout.LayoutParams(0, dp(46), 1f))

        startStop = Button(this).apply {
            isAllCaps = false
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setOnClickListener { toggleService() }
        }
        actions.addView(secRow)
        actions.addView(space(8))
        actions.addView(startStop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        root.addView(actions)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        NavBus.addListener(refresh)
        refreshCaptureCount()
        render()
    }

    override fun onPause() {
        NavBus.removeListener(refresh)
        super.onPause()
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

    private fun render() {
        val access = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val btOk = missingBluetoothPermissions().isEmpty()
        val autoOn = Prefs.isAutoStart(this)

        val running = NavBus.serviceRunning
        serviceBadge.text = when {
            running -> "ĐANG CHẠY"
            autoOn && access && btOk -> "TỰ BẬT"
            else -> "ĐÃ DỪNG"
        }
        serviceBadge.setTextColor(
            when {
                running -> Color.parseColor("#064E3B")
                autoOn && access && btOk -> Color.parseColor("#0C4A6E")
                else -> Color.parseColor("#CBD5E1")
            },
        )
        serviceBadge.background = pill(
            when {
                running -> Color.parseColor("#34D399")
                autoOn && access && btOk -> Color.parseColor("#38BDF8")
                else -> Color.parseColor("#334155")
            },
            99,
        )

        // Banner hướng dẫn cấp quyền nhanh nếu còn thiếu
        if (!btOk || !access) {
            setupCard.visibility = View.VISIBLE
            if (!btOk) {
                setupText.text = "Cần quyền Bluetooth để gửi chỉ đường sang Watch S5."
                setupBtn.text = "Cấp quyền Bluetooth ngay"
                setupBtn.setOnClickListener { requestPerms(all = true) { render() } }
            } else {
                setupText.text = "Cần quyền đọc thông báo để MotoNav lấy chỉ đường từ Google Maps."
                setupBtn.text = "Bật quyền đọc thông báo"
                setupBtn.setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
        } else {
            setupCard.visibility = View.GONE
        }

        // Hero live preview
        val st = NavBus.currentState
        arrowPreview.update(st.k, st.m, st.d)
        val prefix = if (NavBus.demo) "[THỬ] " else ""
        when {
            !running -> {
                navDist.text = if (autoOn && access && btOk) "Sẵn sàng" else "Đã dừng"
                navDist.setTextColor(if (dark) Color.parseColor("#E2E8F0") else Color.parseColor("#0F172A"))
                navTitle.text = if (autoOn && access && btOk) {
                    "Chỉ cần mở Google Maps — MotoNav sẽ tự bật"
                } else {
                    "Bấm Bắt đầu để kết nối với Watch S5"
                }
                navMeta.text = "Hoặc chạm ô MotoNav trên thanh kéo nhanh"
            }
            st.k == NavState.K_IDLE -> {
                navDist.text = "Chờ Maps"
                navDist.setTextColor(if (dark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A"))
                navTitle.text = "Mở Google Maps chế độ xe máy"
                val idleSec = NavBus.idleRemainingSec
                navMeta.text = if (idleSec > 0) "Tự dừng sau ${idleSec / 60}p ${idleSec % 60}s" else "Đang giữ kết nối với đồng hồ"
            }
            st.k == NavState.K_REROUTING -> {
                navDist.text = "Tính lại"
                navDist.setTextColor(Color.parseColor("#FBBF24"))
                navTitle.text = "${prefix}Đang tìm lại tuyến đường"
                navMeta.text = st.e.ifEmpty { "Chờ Google Maps cập nhật" }
            }
            st.k == NavState.K_ARRIVED -> {
                navDist.text = "Đã đến"
                navDist.setTextColor(Color.parseColor("#34D399"))
                navTitle.text = "${prefix}Hoàn thành chuyến đi"
                navMeta.text = st.s.ifEmpty { "Bạn có thể dừng MotoNav" }
            }
            else -> {
                navDist.text = formatDist(st.d)
                navDist.setTextColor(
                    when {
                        st.d in 0..50 -> Color.parseColor("#FF5A36")
                        st.d in 51..200 -> Color.parseColor("#FFD000")
                        else -> if (dark) Color.WHITE else Color.parseColor("#0F172A")
                    },
                )
                val turn = maneuverLabel(st.m) + if (st.m == NavState.M_ROUNDABOUT && st.x > 0) " · Lối ${st.x}" else ""
                navTitle.text = prefix + if (st.s.isNotEmpty()) "$turn · ${st.s}" else turn
                val rem = if (st.r >= 0) formatDist(st.r) else ""
                navMeta.text = listOf(st.e, rem).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Đang dẫn đường" }
            }
        }

        // 2 thẻ trạng thái
        val subs = NavBus.subscribers
        watchValue.text = if (subs > 0) "Đã kết nối" else if (running) "Đang chờ nối" else "Chưa bật"
        watchValue.setTextColor(
            when {
                subs > 0 -> Color.parseColor("#34D399")
                running -> Color.parseColor("#FBBF24")
                else -> Color.parseColor("#94A3B8")
            },
        )
        watchSub.text = when {
            !btOk -> "Thiếu quyền Bluetooth"
            subs > 0 -> "$subs thiết bị đang nhận chỉ đường"
            else -> NavBus.bleStatus
        }

        mapsValue.text = when {
            !access -> "Chưa cấp quyền"
            NavBus.demo -> "Đang chạy thử"
            st.k != NavState.K_IDLE -> "Đang dẫn đường"
            NavBus.listenerConnected -> "Sẵn sàng"
            else -> "Đang nối..."
        }
        mapsValue.setTextColor(
            when {
                !access -> Color.parseColor("#F87171")
                NavBus.demo -> Color.parseColor("#FBBF24")
                st.k != NavState.K_IDLE -> Color.parseColor("#34D399")
                else -> if (dark) Color.parseColor("#E2E8F0") else Color.parseColor("#0F172A")
            },
        )
        mapsSub.text = when {
            !access -> "Cần bật quyền đọc thông báo"
            NavBus.demo -> "Giả lập 4 giây/khúc cua"
            autoOn -> "Tự bật khi bắt đầu đi"
            else -> "Đã sẵn sàng đọc chỉ đường"
        }

        if (NavBus.warning.isNotEmpty()) {
            warningCard.text = "Lưu ý: ${NavBus.warning}"
            warningCard.visibility = View.VISIBLE
        } else {
            warningCard.visibility = View.GONE
        }

        // Thẻ cài đặt
        advCard.visibility = if (advOpen) View.VISIBLE else View.GONE
        if (autoStart.isChecked != autoOn) autoStart.isChecked = autoOn
        val selectedAutoStop = Prefs.autoStopMinutes(this)
        for ((min, chip) in autoStopChips) {
            val sel = min == selectedAutoStop
            chip.background = pill(
                if (sel) Color.parseColor("#0EA5E9") else if (dark) Color.parseColor("#1F2937") else Color.parseColor("#E2E8F0"),
                10,
            )
            chip.setTextColor(if (sel) Color.WHITE else if (dark) Color.parseColor("#CBD5E1") else Color.parseColor("#334155"))
        }
        payload.text = NavBus.lastPayload.ifEmpty { "(chưa có dữ liệu)" }
        clearCaptures.text = if (captureCount >= 0) "Xóa mẫu đã lưu ($captureCount)" else "Xóa mẫu đã lưu"

        // Thanh thao tác dưới cùng
        if (NavBus.demo) {
            demo.text = "Dừng thử"
            demo.setTextColor(Color.parseColor("#FEF3C7"))
            demo.background = pill(Color.parseColor("#B45309"), 14)
        } else {
            demo.text = "Chạy thử (4 s)"
            demo.setTextColor(if (dark) Color.parseColor("#E2E8F0") else Color.parseColor("#0F172A"))
            demo.background = pill(if (dark) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0"), 14)
        }
        advBtn.text = if (advOpen) "Cài đặt ▾" else "Cài đặt ▸"
        advBtn.setTextColor(if (dark) Color.parseColor("#CBD5E1") else Color.parseColor("#334155"))
        advBtn.background = pill(if (dark) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0"), 14)

        if (running) {
            startStop.text = "Dừng"
            startStop.background = pill(Color.parseColor("#DC2626"), 16)
        } else {
            startStop.text = "Bắt đầu"
            startStop.background = pill(Color.parseColor("#059669"), 16)
        }
    }

    private fun formatDist(d: Int) = when {
        d < 0 -> "--"
        d < 1000 -> "$d m"
        else -> "%.1f km".format(d / 1000.0)
    }

    private fun maneuverLabel(m: Int) = when (m) {
        1 -> "Đi thẳng"; 2 -> "Chếch trái"; 3 -> "Rẽ trái"; 4 -> "Ngoặt trái"
        5 -> "Chếch phải"; 6 -> "Rẽ phải"; 7 -> "Ngoặt phải"; 8 -> "Quay đầu"
        9 -> "Vòng xuyến"; 10 -> "Giữ trái"; 11 -> "Giữ phải"; 12 -> "Đích"; 13 -> "Xuất phát"
        else -> "Dẫn đường"
    }

    private fun label(t: String, color: Int) = TextView(this).apply {
        text = t
        textSize = 11f
        setTypeface(null, Typeface.BOLD)
        setTextColor(color)
        letterSpacing = 0.06f
    }

    private fun card(bgColor: Int, strokeColor: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            setColor(bgColor)
            setStroke(dp(1), strokeColor)
            cornerRadius = dp(18).toFloat()
        }
    }

    private fun divider(color: Int) = View(this).apply {
        setBackgroundColor(color)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun subtleButton(label: String, textColor: Int, bgHex: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
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
            NavForegroundService.stopByUser(this)
        } else {
            requestPerms { NavForegroundService.startByUser(this) }
        }
    }

    private fun toggleDemo() {
        if (NavBus.demo) {
            NavBus.demo = false
            NavBus.onServiceWake?.invoke()
            NavLog.i("demo off")
            render()
            return
        }
        requestPerms {
            if (!NavBus.serviceRunning) NavForegroundService.startByUser(this)
            NavBus.demo = true
            NavBus.onServiceWake?.invoke()
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
