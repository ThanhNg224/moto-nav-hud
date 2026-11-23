package dev.motonavhud.app

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import dev.motonavhud.core.NavState

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var c: Palette.Tokens
    private lateinit var scroll: ScrollView
    private lateinit var info: LinearLayout
    private lateinit var serviceBadge: TextView
    private lateinit var arrowPreview: ArrowPreviewView
    private lateinit var navDist: TextView
    private lateinit var navTitle: TextView
    private lateinit var navMeta: TextView
    private lateinit var setupCard: LinearLayout
    private lateinit var setupText: TextView
    private lateinit var setupBtn: Button
    private lateinit var watchDot: View
    private lateinit var watchValue: TextView
    private lateinit var watchSub: TextView
    private lateinit var mapsValue: TextView
    private lateinit var mapsSub: TextView
    private lateinit var warningCard: TextView
    private lateinit var payload: TextView
    private lateinit var diag: TextView
    private lateinit var startStop: Button
    private lateinit var demo: Button
    private lateinit var advBtn: Button
    private lateinit var advCard: LinearLayout
    private lateinit var advHeading: TextView
    private lateinit var autoStart: Switch
    private lateinit var capture: Switch
    private lateinit var clearCaptures: Button
    private lateinit var token: EditText
    private lateinit var setupSettingsBtn: Button
    private lateinit var btSettingsBtn: Button
    private lateinit var notifSettingsBtn: Button
    private lateinit var btPermBtn: Button
    private lateinit var notifPermBtn: Button
    private val autoStopChips = LinkedHashMap<Int, Button>()
    private var advOpen = false
    private var dark = false
    private var captureCount = -1
    private var pendingAfterPermission: (() -> Unit)? = null
    private var pulse: ObjectAnimator? = null
    private var resumed = false
    private val rationaleBefore = HashMap<String, Boolean>()

    private val refresh: () -> Unit = { main.post { renderIfResumed() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        c = Palette.of(dark)
        val ink = c.ink
        val muted = c.muted

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c.bg)
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
            contentDescription = getString(R.string.ui_logo_desc)
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(38), dp(38)))
        header.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(12), 1) })
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(TextView(this).apply {
            text = "MotoNav"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ink)
            isAccessibilityHeading = true
        })
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        titleCol.addView(TextView(this).apply {
            text = getString(R.string.ui_header_subtitle, versionName)
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
        scroll = ScrollView(this)
        info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(4), pad, dp(16))
        }

        // Thẻ mô phỏng mặt đồng hồ trực tiếp
        val heroCard = card(c.card, c.border).apply {
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
                maxLines = 3
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

        // Thẻ hướng dẫn cấp quyền bắt buộc (chỉ hiện khi thiếu quyền Bluetooth hoặc đọc thông báo)
        setupCard = card(c.cautionTint, c.caution).apply {
            visibility = View.GONE
            setupText = TextView(this@MainActivity).apply {
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(c.caution)
            }
            setupBtn = Button(this@MainActivity).apply {
                isAllCaps = false
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(c.onAccent)
                background = pill(c.caution, 12)
                touchTarget(this)
            }
            addView(setupText)
            addView(space(10))
            addView(setupBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setupSettingsBtn = subtleButton(getString(R.string.ui_open_app_settings)) { openAppSettings() }.apply { visibility = View.GONE }
            addView(setupSettingsBtn, matchWrap().apply { topMargin = dp(8) })
        }
        info.addView(setupCard)

        // Cảnh báo (chỉ hiện khi có)
        warningCard = TextView(this).apply {
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Palette.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = pill(Palette.FILL_DANGER, 14)
            visibility = View.GONE
        }
        info.addView(warningCard)

        // Lưới 2 cột trạng thái: Đồng hồ S5 | Google Maps
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
        }
        val watchCard = card(c.card, c.border).apply {
            minimumHeight = dp(92)
            addView(label(getString(R.string.ui_card_watch)))
            addView(space(4))
            watchDot = View(this@MainActivity).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            watchValue = TextView(this@MainActivity).apply { textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(ink) }
            watchSub = TextView(this@MainActivity).apply { textSize = 12f; setTextColor(muted) }
            val valueRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            valueRow.addView(watchDot, LinearLayout.LayoutParams(dp(10), dp(10)))
            valueRow.addView(View(this@MainActivity).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })
            valueRow.addView(watchValue, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(valueRow)
            addView(space(2))
            addView(watchSub)
        }
        val mapsCard = card(c.card, c.border).apply {
            minimumHeight = dp(92)
            addView(label(getString(R.string.ui_card_maps)))
            addView(space(4))
            mapsValue = TextView(this@MainActivity).apply { textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(ink) }
            mapsSub = TextView(this@MainActivity).apply { textSize = 12f; setTextColor(muted) }
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
        advCard = card(c.card, c.border).apply {
            advHeading = TextView(this@MainActivity).apply {
                text = getString(R.string.ui_settings)
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ink)
                isAccessibilityHeading = true
            }
            addView(advHeading)
            addView(space(12))
            addView(label(getString(R.string.ui_section_auto)))
            addView(space(8))
            autoStart = Switch(this@MainActivity).apply {
                text = getString(R.string.ui_auto_start)
                textSize = 14.5f
                setTextColor(ink)
                minHeight = dp(48)
                isChecked = Prefs.isAutoStart(this@MainActivity)
                setOnCheckedChangeListener { _, on ->
                    Prefs.setAutoStart(this@MainActivity, on)
                    NavTileService.requestRefresh(this@MainActivity)
                    render()
                }
            }
            addView(autoStart)
            addView(note(getString(R.string.ui_auto_start_desc), 12f))
            addView(space(14))

            addView(label(getString(R.string.ui_section_auto_stop)))
            addView(space(8))
            val chipBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            // Cửa sổ hẹp: chia chip thành nhiều hàng để mỗi chip rộng >= 48dp
            val cols = chipColumns(resources.configuration.screenWidthDp - 64, Prefs.AUTO_STOP_OPTIONS.size)
            Prefs.AUTO_STOP_OPTIONS.toList().chunked(cols).forEachIndexed { rowIdx, rowOpts ->
                if (rowIdx > 0) chipBox.addView(space(6))
                val chipRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
                chipBox.addView(chipRow)
                rowOpts.forEachIndexed { idx, min ->
                if (idx > 0) chipRow.addView(View(this@MainActivity).apply { layoutParams = LinearLayout.LayoutParams(dp(6), 1) })
                val chip = Button(this@MainActivity).apply {
                    text = if (min == 0) getString(R.string.ui_chip_off) else getString(R.string.ui_chip_minutes, min)
                    contentDescription = if (min == 0) getString(R.string.ui_chip_off) else resources.getQuantityString(R.plurals.ui_chip_minutes_desc, min, min)
                    isAllCaps = false
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                    touchTarget(this)
                    setOnClickListener {
                        Prefs.setAutoStopMinutes(this@MainActivity, min)
                        render()
                    }
                }
                autoStopChips[min] = chip
                chipRow.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
            }
            addView(chipBox)

            addView(space(16))
            addView(divider(c.divider))
            addView(space(14))
            addView(label(getString(R.string.ui_section_perms)))
            addView(space(8))

            btPermBtn = subtleButton(getString(R.string.ui_perm_bt_btn)) { ensureBluetooth { render() } }
            addView(btPermBtn, matchWrap())
            btSettingsBtn = subtleButton(getString(R.string.ui_open_app_settings)) { openAppSettings() }.apply { visibility = View.GONE }
            addView(btSettingsBtn, matchWrap().apply { topMargin = dp(8) })
            addView(note(getString(R.string.ui_perm_bt_why), 12f))
            addView(space(10))

            notifPermBtn = subtleButton(getString(R.string.ui_perm_notif_btn)) { onNotificationsClick() }
            addView(notifPermBtn, matchWrap())
            notifSettingsBtn = subtleButton(getString(R.string.ui_open_app_settings)) { openAppSettings() }.apply { visibility = View.GONE }
            addView(notifSettingsBtn, matchWrap().apply { topMargin = dp(8) })
            addView(note(getString(R.string.ui_perm_notif_why), 12f))
            addView(space(10))

            val access = subtleButton(getString(R.string.ui_perm_listener_btn)) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            addView(access, matchWrap())
            addView(note(getString(R.string.ui_perm_listener_why), 12f))
            addView(space(10))

            val battery = subtleButton(getString(R.string.ui_perm_battery_btn)) {
                runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    .onFailure { Toast.makeText(this@MainActivity, R.string.ui_perm_battery_fallback, Toast.LENGTH_SHORT).show() }
            }
            addView(battery, matchWrap())
            addView(note(getString(R.string.ui_perm_battery_why), 12f))

            addView(space(16))
            addView(divider(c.divider))
            addView(space(14))
            addView(label(getString(R.string.ui_section_debug)))
            addView(space(8))

            capture = Switch(this@MainActivity).apply {
                text = getString(R.string.ui_capture)
                setTextColor(ink)
                minHeight = dp(48)
                isChecked = Prefs.isCapture(this@MainActivity)
                setOnCheckedChangeListener { _, on -> Prefs.setCapture(this@MainActivity, on) }
            }
            addView(capture)
            addView(space(8))

            clearCaptures = subtleButton(getString(R.string.ui_capture_clear)) {
                Thread {
                    val n = NotificationCapture.clear(this@MainActivity)
                    main.post {
                        captureCount = 0
                        Toast.makeText(this@MainActivity, resources.getQuantityString(R.plurals.ui_capture_cleared, n, n), Toast.LENGTH_SHORT).show()
                        renderIfResumed()
                    }
                }.start()
            }
            addView(clearCaptures, matchWrap())
            addView(space(14))

            // Mã ghép: chỉ dành cho bản Watch tự build; giải thích hiện trước nút Lưu
            addView(label(getString(R.string.ui_section_token)))
            addView(space(4))
            addView(note(getString(R.string.ui_token_desc), 12f))
            addView(space(8))
            val tokenRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            token = EditText(this@MainActivity).apply {
                hint = getString(R.string.ui_token_hint)
                setText(Prefs.token(this@MainActivity))
                setTextColor(ink)
                setHintTextColor(muted)
                setSingleLine()
                minHeight = dp(48)
            }
            val saveToken = subtleButton(getString(R.string.ui_token_save)) {
                val ok = Prefs.setToken(this@MainActivity, token.text.toString().trim())
                Toast.makeText(this@MainActivity, if (ok) R.string.ui_token_saved else R.string.ui_token_invalid, Toast.LENGTH_SHORT).show()
                if (!ok) token.setText(Prefs.token(this@MainActivity))
            }
            tokenRow.addView(token, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            tokenRow.addView(View(this@MainActivity).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })
            tokenRow.addView(saveToken, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(tokenRow)
            addView(space(8))
            val resetToken = subtleButton(getString(R.string.ui_token_reset)) {
                Prefs.setToken(this@MainActivity, Prefs.DEFAULT_TOKEN)
                token.setText(Prefs.DEFAULT_TOKEN)
                Toast.makeText(this@MainActivity, R.string.ui_token_reset_done, Toast.LENGTH_SHORT).show()
            }
            addView(resetToken, matchWrap())
            addView(space(14))

            addView(label(getString(R.string.ui_section_payload)))
            addView(space(4))
            payload = TextView(this@MainActivity).apply {
                typeface = Typeface.MONOSPACE
                textSize = 11f
                setTextColor(muted)
            }
            addView(payload)
            addView(space(10))
            addView(label(getString(R.string.ui_section_diag)))
            addView(space(4))
            diag = TextView(this@MainActivity).apply {
                typeface = Typeface.MONOSPACE
                textSize = 11f
                setTextColor(muted)
            }
            addView(diag)
            addView(space(8))
            addView(note(getString(R.string.ui_tip_tile), 11.5f))
        }
        info.addView(advCard)

        scroll.addView(info)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Vùng thao tác chính ở nửa dưới màn hình (thumb zone)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(10), pad, dp(16))
            setBackgroundColor(c.bar)
        }
        val secRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        demo = Button(this).apply {
            isAllCaps = false
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            touchTarget(this)
            setOnClickListener { toggleDemo() }
        }
        advBtn = Button(this).apply {
            isAllCaps = false
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            touchTarget(this)
            contentDescription = getString(R.string.ui_settings)
            setOnClickListener { toggleSettings() }
        }
        secRow.addView(demo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f))
        secRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })
        secRow.addView(advBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        startStop = Button(this).apply {
            isAllCaps = false
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Palette.WHITE)
            touchTarget(this, 56)
            setOnClickListener { toggleService() }
        }
        actions.addView(secRow)
        actions.addView(space(8))
        actions.addView(startStop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(actions)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        NavBus.addListener(refresh)
        refreshCaptureCount()
        render()
    }

    override fun onPause() {
        resumed = false
        NavBus.removeListener(refresh)
        setPulse(false)
        super.onPause()
    }

    override fun onDestroy() {
        setPulse(false)
        super.onDestroy()
    }

    private fun renderIfResumed() {
        if (resumed) render()
    }

    private fun refreshCaptureCount() {
        Thread {
            val n = NotificationCapture.count(this@MainActivity)
            main.post {
                if (captureCount != n) {
                    captureCount = n
                    renderIfResumed()
                }
            }
        }.start()
    }

    private fun toggleSettings() {
        advOpen = !advOpen
        TransitionManager.beginDelayedTransition(info, AutoTransition().setDuration(180))
        if (advOpen) {
            refreshCaptureCount()
            // Cuộn tới thẻ ngay khi nó có kích thước, rồi đưa tiêu điểm trợ năng vào tiêu đề Cài đặt
            advCard.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or2: Int, ob: Int) {
                    advCard.removeOnLayoutChangeListener(this)
                    scroll.smoothScrollTo(0, advCard.top)
                    a11yFocus(advHeading)
                }
            })
            render()
        } else {
            render()
            a11yFocus(advBtn)
        }
    }

    private fun a11yFocus(v: View) {
        v.post { v.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) }
    }

    private fun setPulse(on: Boolean) {
        if (on) {
            if (pulse == null) {
                pulse = ObjectAnimator.ofFloat(watchDot, View.ALPHA, 1f, 0.3f).apply {
                    setDuration(750)
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    start()
                }
            }
        } else {
            pulse?.cancel()
            pulse = null
            watchDot.alpha = 1f
        }
    }

    private fun render() {
        val access = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val btMissing = missingBluetoothPermissions()
        val btOk = btMissing.isEmpty()
        val btOn = getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        val autoOn = Prefs.isAutoStart(this)
        val running = NavBus.serviceRunning
        val auto = autoStartStatus(running, autoOn, access, NavBus.listenerConnected, btOk, btOn, NavBus.autoStartGate.isSuppressed())

        // Badge phản ánh trạng thái thật của tự bật
        val (badgeText, badgeFill, badgeInk) = when (auto) {
            AutoStartStatus.RUNNING -> Triple(R.string.ui_badge_running, c.ok, c.onAccent)
            AutoStartStatus.READY -> Triple(R.string.ui_badge_auto, c.teal, c.onAccent)
            AutoStartStatus.DISABLED, AutoStartStatus.SUPPRESSED -> Triple(R.string.ui_badge_stopped, c.chip, c.chipText)
            AutoStartStatus.NO_BT_PERMISSION, AutoStartStatus.NO_LISTENER -> Triple(R.string.ui_badge_missing_perm, c.caution, c.onAccent)
            AutoStartStatus.LISTENER_CONNECTING -> Triple(R.string.ui_badge_connecting, c.caution, c.onAccent)
            AutoStartStatus.BT_OFF -> Triple(R.string.ui_badge_bt_off, c.caution, c.onAccent)
        }
        serviceBadge.setText(badgeText)
        serviceBadge.setTextColor(badgeInk)
        serviceBadge.background = pill(badgeFill, 99)

        // Banner quyền bắt buộc: nêu lý do cần quyền + hành động kế tiếp
        if (!btOk || !access) {
            setupCard.visibility = View.VISIBLE
            if (!btOk) {
                setupText.setText(R.string.ui_setup_bt_why)
                setupBtn.setText(if (isPermPermanent(btMissing)) R.string.ui_open_app_settings else R.string.ui_setup_bt_btn)
                setupBtn.setOnClickListener { ensureBluetooth { render() } }
                setupSettingsBtn.visibility = if (wasDeniedOnce(btMissing)) View.VISIBLE else View.GONE
            } else {
                setupText.setText(R.string.ui_setup_listener_why)
                setupBtn.setText(R.string.ui_setup_listener_btn)
                setupBtn.setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                setupSettingsBtn.visibility = View.GONE
            }
        } else {
            setupCard.visibility = View.GONE
        }

        // Hero live preview
        val st = NavBus.currentState
        arrowPreview.update(st.k, st.m, st.d)
        val prefix = if (NavBus.demo) getString(R.string.ui_demo_prefix) else ""
        var arrowDesc: CharSequence? = null
        when {
            !running -> {
                val (dist, title, meta, color) = when (auto) {
                    AutoStartStatus.READY -> Hero(R.string.ui_hero_ready, R.string.ui_hero_ready_title, R.string.ui_hero_ready_meta, c.ink)
                    AutoStartStatus.SUPPRESSED -> Hero(R.string.ui_hero_stopped, R.string.ui_hero_suppressed_title, R.string.ui_hero_suppressed_meta, c.ink)
                    AutoStartStatus.BT_OFF -> Hero(R.string.ui_hero_stopped, R.string.ui_hero_bt_off_title, R.string.ui_hero_bt_off_meta, c.caution)
                    AutoStartStatus.NO_BT_PERMISSION -> Hero(R.string.ui_hero_missing, R.string.ui_hero_no_bt_title, R.string.ui_hero_no_bt_meta, c.caution)
                    AutoStartStatus.LISTENER_CONNECTING -> Hero(R.string.ui_hero_connecting, R.string.ui_hero_connecting_title, R.string.ui_hero_connecting_meta, c.caution)
                    AutoStartStatus.NO_LISTENER -> Hero(R.string.ui_hero_missing, R.string.ui_hero_no_listener_title, R.string.ui_hero_no_listener_meta, c.caution)
                    else -> Hero(R.string.ui_hero_stopped, R.string.ui_hero_disabled_title, R.string.ui_hero_disabled_meta, c.ink)
                }
                navDist.setText(dist)
                navDist.setTextColor(color)
                navTitle.setText(title)
                navMeta.setText(meta)
            }
            st.k == NavState.K_IDLE -> {
                navDist.setText(R.string.ui_hero_wait_maps)
                navDist.setTextColor(c.ink)
                navTitle.setText(R.string.ui_hero_wait_maps_title)
                val idleSec = NavBus.idleRemainingSec
                navMeta.text = if (idleSec > 0) getString(R.string.ui_hero_idle_countdown, idleSec / 60, idleSec % 60) else getString(R.string.ui_hero_idle_holding)
            }
            st.k == NavState.K_REROUTING -> {
                navDist.setText(R.string.ui_hero_rerouting)
                navDist.setTextColor(c.caution)
                navTitle.text = getString(R.string.ui_hero_rerouting_title, prefix)
                navMeta.text = st.e.ifEmpty { getString(R.string.ui_hero_rerouting_meta) }
            }
            st.k == NavState.K_ARRIVED -> {
                navDist.setText(R.string.ui_hero_arrived)
                navDist.setTextColor(c.ok)
                navTitle.text = getString(R.string.ui_hero_arrived_title, prefix)
                navMeta.text = st.s.ifEmpty { getString(R.string.ui_hero_arrived_meta) }
            }
            else -> {
                navDist.text = formatDist(st.d)
                navDist.setTextColor(
                    when {
                        st.d in 0..50 -> c.orange
                        st.d in 51..200 -> c.caution
                        else -> c.ink
                    },
                )
                val man = maneuverLabel(st.m)
                val turn = if (st.m == NavState.M_ROUNDABOUT && st.x > 0) getString(R.string.ui_turn_exit, man, st.x) else man
                navTitle.text = prefix + if (st.s.isNotEmpty()) "$turn · ${st.s}" else turn
                val rem = if (st.r >= 0) formatDist(st.r) else ""
                navMeta.text = listOf(st.e, rem).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { getString(R.string.ui_hero_navigating_meta) }
                arrowDesc = man
            }
        }
        arrowPreview.contentDescription = arrowDesc ?: navTitle.text

        // 2 thẻ trạng thái
        val subs = NavBus.subscribers
        val ws = watchStatus(btOk, btOn, running, subs, NavBus.bleState, NavBus.warnCode)
        val (wValue, wSub, wColor) = when (ws) {
            WatchStatus.NO_PERMISSION -> Triple(R.string.ui_watch_no_permission, R.string.ui_watch_no_permission_sub, c.danger)
            WatchStatus.DELIVERY_PROBLEM -> Triple(R.string.ui_watch_delivery, R.string.ui_watch_delivery_sub, c.danger)
            WatchStatus.CONNECTED -> Triple(R.string.ui_watch_connected, 0, c.ok)
            WatchStatus.OFF -> Triple(R.string.ui_watch_off, R.string.ui_watch_off_sub, c.muted)
            WatchStatus.STARTING -> Triple(R.string.ui_watch_starting, R.string.ui_watch_starting_sub, c.caution)
            WatchStatus.WAITING -> Triple(R.string.ui_watch_waiting, R.string.ui_watch_waiting_sub, c.caution)
            WatchStatus.WAITING_SAVER -> Triple(R.string.ui_watch_waiting, R.string.ui_watch_waiting_saver_sub, c.caution)
            WatchStatus.BT_OFF -> Triple(R.string.ui_watch_bt_off, R.string.ui_watch_bt_off_sub, c.danger)
            WatchStatus.RETRYING -> Triple(R.string.ui_watch_retrying, R.string.ui_watch_retrying_sub, c.caution)
            WatchStatus.ERROR -> Triple(R.string.ui_watch_error, R.string.ui_watch_error_sub, c.danger)
            WatchStatus.AUTO_STOPPED -> Triple(R.string.ui_watch_auto_stopped, R.string.ui_watch_auto_stopped_sub, c.muted)
        }
        watchValue.setText(wValue)
        watchValue.setTextColor(wColor)
        watchSub.text = if (ws == WatchStatus.CONNECTED) resources.getQuantityString(R.plurals.ui_watch_connected_sub, subs, subs) else getString(wSub)
        watchDot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(wColor) }
        setPulse(resumed && isWaitingForWatch(ws))

        val ms = mapsStatus(access, NavBus.listenerConnected, NavBus.demo, NavBus.mapsState.k)
        val (mValue, mSub, mColor) = when (ms) {
            MapsStatus.NO_LISTENER -> Triple(R.string.ui_maps_no_listener, R.string.ui_maps_no_listener_sub, c.danger)
            MapsStatus.CONNECTING -> Triple(R.string.ui_maps_connecting, R.string.ui_maps_connecting_sub, c.muted)
            MapsStatus.IDLE -> Triple(R.string.ui_maps_idle, R.string.ui_maps_idle_sub, c.ink)
            MapsStatus.NAVIGATING -> Triple(R.string.ui_maps_navigating, R.string.ui_maps_navigating_sub, c.ok)
            MapsStatus.REROUTING -> Triple(R.string.ui_maps_rerouting, R.string.ui_maps_rerouting_sub, c.caution)
            MapsStatus.ARRIVED -> Triple(R.string.ui_maps_arrived, R.string.ui_maps_arrived_sub, c.ok)
            MapsStatus.DEMO -> Triple(R.string.ui_maps_demo, R.string.ui_maps_demo_sub, c.caution)
        }
        mapsValue.setText(mValue)
        mapsValue.setTextColor(mColor)
        mapsSub.setText(mSub)

        // Cảnh báo cho rider: chỉ dịch theo mã; chuỗi kỹ thuật nằm trong khung chẩn đoán
        if (NavBus.warnCode == WarnCode.MTU_TOO_SMALL) {
            warningCard.setText(R.string.ui_warn_mtu)
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
            chip.isSelected = sel
            ViewCompat.setStateDescription(chip, getString(if (sel) R.string.ui_state_selected else R.string.ui_state_not_selected))
            chip.background = pill(if (sel) c.teal else c.chip, 10)
            chip.setTextColor(if (sel) c.onAccent else c.chipText)
        }
        btPermBtn.isEnabled = !btOk
        btPermBtn.alpha = if (btOk) 0.6f else 1f
        btPermBtn.setText(
            when {
                btOk -> R.string.ui_perm_bt_granted
                isPermPermanent(btMissing) -> R.string.ui_open_app_settings
                else -> R.string.ui_perm_bt_btn
            },
        )
        val notif = notifStep(!notificationsMissing(), NotificationManagerCompat.from(this).areNotificationsEnabled(), serviceChannelBlocked())
        notifPermBtn.isEnabled = notif != NotifStep.OK
        notifPermBtn.alpha = if (notif == NotifStep.OK) 0.6f else 1f
        notifPermBtn.setText(
            when (notif) {
                NotifStep.OK -> R.string.ui_perm_notif_granted
                NotifStep.APP_BLOCKED -> R.string.ui_perm_notif_app_blocked
                NotifStep.CHANNEL_BLOCKED -> R.string.ui_perm_notif_channel_blocked
                NotifStep.REQUEST ->
                    if (isPermPermanent(listOf(Manifest.permission.POST_NOTIFICATIONS))) R.string.ui_open_app_settings else R.string.ui_perm_notif_btn
            },
        )
        btSettingsBtn.visibility = if (!btOk && wasDeniedOnce(btMissing)) View.VISIBLE else View.GONE
        notifSettingsBtn.visibility =
            if (notif == NotifStep.REQUEST && wasDeniedOnce(listOf(Manifest.permission.POST_NOTIFICATIONS))) View.VISIBLE else View.GONE
        payload.text = NavBus.lastPayload.ifEmpty { getString(R.string.ui_payload_empty) }
        diag.text = listOf(NavBus.bleStatus, NavBus.bleDetail, NavBus.warning).filter { it.isNotEmpty() }.joinToString("\n").ifEmpty { "-" }
        clearCaptures.text = if (captureCount >= 0) getString(R.string.ui_capture_clear_count, captureCount) else getString(R.string.ui_capture_clear)

        // Thanh thao tác dưới cùng
        if (NavBus.demo) {
            demo.setText(R.string.ui_demo_stop)
            demo.setTextColor(c.onAccent)
            demo.background = pill(c.caution, 14)
        } else {
            demo.setText(R.string.ui_demo_start)
            demo.setTextColor(c.ink)
            demo.background = pill(c.chip, 14)
        }
        advBtn.setText(if (advOpen) R.string.ui_settings_open else R.string.ui_settings_closed)
        ViewCompat.setStateDescription(advBtn, getString(if (advOpen) R.string.ui_state_expanded else R.string.ui_state_collapsed))
        advBtn.setTextColor(c.chipText)
        advBtn.background = pill(c.chip, 14)

        if (running) {
            startStop.setText(R.string.ui_stop)
            startStop.background = pill(Palette.FILL_DANGER, 16)
        } else {
            startStop.setText(R.string.ui_start)
            startStop.background = pill(Palette.FILL_OK, 16)
        }
    }

    private data class Hero(val dist: Int, val title: Int, val meta: Int, val color: Int)

    private fun formatDist(d: Int) = when {
        d < 0 -> getString(R.string.ui_dist_unknown)
        d < 1000 -> getString(R.string.ui_dist_m, d)
        else -> getString(R.string.ui_dist_km, d / 1000.0)
    }

    private fun maneuverLabel(m: Int) = getString(
        when (m) {
            NavState.M_STRAIGHT -> R.string.ui_man_straight
            NavState.M_SLIGHT_LEFT -> R.string.ui_man_slight_left
            NavState.M_LEFT -> R.string.ui_man_left
            NavState.M_SHARP_LEFT -> R.string.ui_man_sharp_left
            NavState.M_SLIGHT_RIGHT -> R.string.ui_man_slight_right
            NavState.M_RIGHT -> R.string.ui_man_right
            NavState.M_SHARP_RIGHT -> R.string.ui_man_sharp_right
            NavState.M_UTURN -> R.string.ui_man_uturn
            NavState.M_ROUNDABOUT -> R.string.ui_man_roundabout
            NavState.M_KEEP_LEFT -> R.string.ui_man_keep_left
            NavState.M_KEEP_RIGHT -> R.string.ui_man_keep_right
            NavState.M_ARRIVE -> R.string.ui_man_arrive
            NavState.M_DEPART -> R.string.ui_man_depart
            else -> R.string.ui_man_unknown
        },
    )

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 11f
        setTypeface(null, Typeface.BOLD)
        setTextColor(c.muted)
        letterSpacing = 0.06f
        isAccessibilityHeading = true
    }

    private fun note(t: String, size: Float) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(c.muted)
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

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    /** Mục tiêu chạm tối thiểu 48dp nhưng cho phép cao hơn khi chữ lớn. */
    private fun touchTarget(v: TextView, minDp: Int = 48) {
        v.minHeight = dp(minDp)
        v.minimumHeight = dp(minDp)
    }

    private fun subtleButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(c.ink)
        background = pill(c.chip, 12)
        touchTarget(this)
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
            ensureBluetooth { NavForegroundService.startByUser(this) }
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
        if (!NavBus.canStartDemo()) {
            Toast.makeText(this, R.string.ui_demo_blocked, Toast.LENGTH_LONG).show()
            return
        }
        ensureBluetooth {
            if (!NavBus.serviceRunning) NavForegroundService.startByUser(this)
            NavBus.demo = true
            NavBus.onServiceWake?.invoke()
            NavLog.i("demo on")
            render()
        }
    }

    private fun missingBluetoothPermissions(): List<String> {
        if (Build.VERSION.SDK_INT < 31) return emptyList()
        return listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }

    private fun notificationsMissing() =
        Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Kênh thông báo của service (id khớp NavForegroundService.CHANNEL_ID); null nghĩa là chưa tạo, không coi là bị chặn. */
    private fun serviceChannelBlocked() =
        getSystemService(NotificationManager::class.java).getNotificationChannel(NOTIF_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE

    private val uiPrefs get() = getSharedPreferences("ui_state", MODE_PRIVATE)

    private fun memory(p: String) = PermMemory(uiPrefs.getBoolean("denied_$p", false), uiPrefs.getBoolean("perm_$p", false))

    /** Chụp rationale TRƯỚC khi xin để phân biệt từ chối lần hai (vĩnh viễn) với đóng hộp thoại lần đầu (mơ hồ). */
    private fun launchRequest(perms: List<String>, code: Int) {
        perms.forEach { rationaleBefore[it] = shouldShowRequestPermissionRationale(it) }
        requestPermissions(perms.toTypedArray(), code)
    }

    private fun recordResult(perms: Array<out String>, results: IntArray) {
        if (perms.isEmpty() || results.size != perms.size) return
        uiPrefs.edit().apply {
            perms.forEachIndexed { i, p ->
                val m = permAfterResult(
                    memory(p), results[i] == PackageManager.PERMISSION_GRANTED,
                    rationaleBefore[p] == true, shouldShowRequestPermissionRationale(p),
                )
                putBoolean("denied_$p", m.deniedOnce)
                putBoolean("perm_$p", m.permanent)
            }
        }.apply()
    }

    /** Chắc chắn không còn hộp thoại: nút chính mở cài đặt ứng dụng. */
    private fun isPermPermanent(perms: Collection<String>) = perms.any { memory(it).permanent }

    /** Đã bị từ chối ít nhất một lần: luôn hiện thêm lối mở cài đặt bên cạnh nút xin quyền. */
    private fun wasDeniedOnce(perms: Collection<String>) = perms.any { memory(it).deniedOnce }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    /** Bluetooth là quyền bắt buộc, xin riêng; bị từ chối vĩnh viễn thì mở cài đặt ứng dụng. */
    private fun ensureBluetooth(then: () -> Unit) {
        val missing = missingBluetoothPermissions()
        when {
            missing.isEmpty() -> then()
            isPermPermanent(missing) -> {
                Toast.makeText(this, R.string.ui_perm_bt_denied_toast, Toast.LENGTH_LONG).show()
                openAppSettings()
            }
            else -> {
                pendingAfterPermission = then
                launchRequest(missing, REQ_BT)
            }
        }
    }

    /** Thông báo của MotoNav là tùy chọn: xin quyền, hoặc mở cài đặt thông báo app/kênh khi bị tắt. */
    private fun onNotificationsClick() {
        val perm = Manifest.permission.POST_NOTIFICATIONS
        when (notifStep(!notificationsMissing(), NotificationManagerCompat.from(this).areNotificationsEnabled(), serviceChannelBlocked())) {
            NotifStep.OK -> Unit
            NotifStep.APP_BLOCKED ->
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            NotifStep.CHANNEL_BLOCKED ->
                startActivity(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, NOTIF_CHANNEL_ID),
                )
            NotifStep.REQUEST -> if (isPermPermanent(listOf(perm))) openAppSettings() else launchRequest(listOf(perm), REQ_NOTIF)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        recordResult(permissions, grantResults)
        if (requestCode == REQ_BT) {
            val then = pendingAfterPermission
            pendingAfterPermission = null
            if (then != null && missingBluetoothPermissions().isEmpty()) then()
        }
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    private companion object {
        const val REQ_BT = 1
        const val REQ_NOTIF = 2
        const val NOTIF_CHANNEL_ID = "nav_service"
    }
}
