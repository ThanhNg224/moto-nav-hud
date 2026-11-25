package dev.motonavhud.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.motonavhud.core.NavState
import dev.motonavhud.core.PlaceKind

/** Màn chính: một màn duy nhất, đổi nội dung theo [HomeMode] (thiết lập, chờ Maps, đang dẫn đường...) và [DockMode]. */
class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var ui: Ui
    private lateinit var c: Palette.Tokens
    private val perms = PermissionFlow(this) { render() }

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var bezel: WatchBezelView
    private lateinit var eyebrow: TextView
    private lateinit var h1: TextView
    private lateinit var sentence: TextView
    private lateinit var subLine: TextView
    private lateinit var segs: LinearLayout
    private lateinit var warnRow: TextView
    private lateinit var setupList: LinearLayout
    private lateinit var setupNote: TextView
    private lateinit var btRow: UiSetupRow
    private lateinit var notifRow: UiSetupRow
    private lateinit var accessRow: UiSetupRow
    private lateinit var batteryRow: UiSetupRow
    private lateinit var rail: LinearLayout
    private lateinit var remainRow: UiRailRow
    private lateinit var etaRow: UiRailRow
    private lateinit var watchRow: UiRailRow
    private lateinit var mapsRow: UiRailRow
    private lateinit var autoStopRow: UiRailRow
    private lateinit var quick: LinearLayout
    private lateinit var quickHint: TextView
    private lateinit var statusHead: TextView
    private val quickBtns = ArrayList<UiPill>()
    private lateinit var stopBtn: UiPill
    private lateinit var settingsBtn: UiPill
    private lateinit var primary: UiPill

    private var dock = DockMode.IDLE
    private var statusDotColor = 0
    private var segsDone = -1
    private var sentenceBig: Boolean? = null
    private var pulse: ObjectAnimator? = null
    private var resumed = false

    private val refresh: () -> Unit = { main.post { renderIfResumed() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventLog.init(applicationContext)
        actionBar?.hide()
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        c = Palette.of(dark)
        ui = Ui(this, c)
        ShortcutPublisher.publish(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c.bg)
            fitsSystemWindows = true
        }
        root.addView(buildTopBar())
        root.addView(buildBody(dark), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buildDock())
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        NavBus.addListener(refresh)
        refreshQuick()
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        perms.handleResult(requestCode, permissions, grantResults)
    }

    private fun renderIfResumed() {
        if (resumed) render()
    }

    // ---------- Dựng view ----------

    private fun buildTopBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(64)
        setPadding(ui.dp(20), 0, ui.dp(20), 0)
        addView(UiLogoView(this@MainActivity, c).apply { contentDescription = getString(R.string.ui_logo_desc) }, LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)))
        addView(
            ui.text(18f, c.on, ui.condensed).apply {
                text = "MOTONAV"
                letterSpacing = 0.14f
                isAccessibilityHeading = true
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = ui.dp(10) },
        )
        addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
        statusDot = ui.dot(8)
        statusText = ui.label().apply { maxLines = 1 }
        addView(statusDot, LinearLayout.LayoutParams(ui.dp(8), ui.dp(8)).apply { marginEnd = ui.dp(8) })
        addView(statusText)
    }

    private fun railParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { marginStart = ui.dp(20); marginEnd = ui.dp(20) }

    private fun buildBody(dark: Boolean): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bezel = WatchBezelView(this, dark)
        col.addView(bezel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Khối chữ: nhãn nhỏ, tiêu đề lớn, câu mô tả, dòng phụ, thanh tiến độ thiết lập
        val textBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0)
        }
        eyebrow = ui.label().apply { visibility = View.GONE }
        h1 = ui.h1()
        sentence = ui.text(15f, c.dim)
        subLine = ui.text(13f, c.dim)
        segs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(4) { i ->
            segs.addView(
                View(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
                LinearLayout.LayoutParams(0, ui.dp(4), 1f).apply { if (i > 0) marginStart = ui.dp(6) },
            )
        }
        textBlock.addView(eyebrow)
        textBlock.addView(h1)
        textBlock.addView(sentence, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(4) })
        textBlock.addView(subLine, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(4) })
        textBlock.addView(segs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(4)).apply { topMargin = ui.dp(16); bottomMargin = ui.dp(16) })
        col.addView(textBlock)

        // Cảnh báo (chỉ hiện khi có): hàng nền hổ phách
        warnRow = ui.text(13f, c.onWarnTint, ui.bold).apply {
            setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
            background = ui.fill(c.warnTint, 12f)
            visibility = View.GONE
        }
        col.addView(warnRow, railParams().apply { topMargin = ui.dp(16); bottomMargin = ui.dp(12) })

        col.addView(buildSetupList(), railParams())
        setupNote = ui.text(13f, c.dim).apply {
            text = getString(R.string.ui_setup_note)
            setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(8))
        }
        col.addView(setupNote)

        // "Đi nhanh" đặt trước trạng thái: hành động chính phải nằm trong màn hình đầu, không bị đẩy xuống dưới nếp gập.
        col.addView(buildQuick())
        statusHead = ui.label(getString(R.string.ui_section_status)).apply {
            isAccessibilityHeading = true
            setPadding(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(12))
        }
        col.addView(statusHead)
        col.addView(buildRail(), railParams())
        return ScrollView(this).apply {
            isFillViewport = true
            addView(col)
        }
    }

    private fun buildSetupList(): View {
        setupList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setupList.addView(ui.hairline())
        btRow = UiSetupRow(ui, getString(R.string.ui_perm_bt_title), getString(R.string.ui_btn_grant)).apply {
            onAction { perms.ensureBluetooth { render() } }
            onExtra { perms.openAppSettings() }
        }
        notifRow = UiSetupRow(ui, getString(R.string.ui_perm_notif_title), getString(R.string.ui_btn_grant)).apply {
            onAction { perms.onNotificationsClick() }
            onExtra { perms.openAppSettings() }
        }
        accessRow = UiSetupRow(ui, getString(R.string.ui_perm_listener_title), getString(R.string.ui_btn_grant)).apply {
            onAction { perms.openListenerSettings() }
        }
        batteryRow = UiSetupRow(ui, getString(R.string.ui_perm_battery_title), getString(R.string.ui_btn_open)).apply {
            onAction { perms.openBatterySettings() }
        }
        listOf(btRow, notifRow, accessRow, batteryRow).forEach { setupList.addView(it) }
        return setupList
    }

    private fun buildRail(): View {
        rail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        remainRow = UiRailRow(ui, getString(R.string.ui_row_remaining))
        etaRow = UiRailRow(ui, getString(R.string.ui_row_eta))
        watchRow = UiRailRow(ui, getString(R.string.ui_card_watch))
        mapsRow = UiRailRow(ui, getString(R.string.ui_card_maps))
        autoStopRow = UiRailRow(ui, getString(R.string.ui_row_auto_stop))
        listOf(remainRow, etaRow, watchRow, mapsRow, autoStopRow).forEach { rail.addView(it) }
        return rail
    }

    private fun buildQuick(): View {
        quick = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(12))
        }
        head.addView(ui.label(getString(R.string.ui_quick_title)).apply { isAccessibilityHeading = true })
        head.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        quickHint = ui.label().apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        head.addView(quickHint)
        quick.addView(head)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(20), 0, ui.dp(20), 0)
        }
        repeat(3) { i ->
            val b = UiPill(ui, 16f, 56, 14f, 20, stacked = true)
            quickBtns.add(b)
            row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = ui.dp(12) })
        }
        quick.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ui.dp(20) })
        // Chiều cao 1dp phải đặt tường minh: railParams() là WRAP_CONTENT nên View trơn bị đo bằng cả phần còn lại (khối xám lớn).
        quick.addView(ui.hairline(), railParams().apply { height = maxOf(1, ui.dp(1)) })
        return quick
    }

    private fun buildDock(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(24))
            setBackgroundColor(c.bg)
        }
        stopBtn = UiPill(ui, 20f, 64, 17f, 20).apply {
            bind(getString(R.string.ui_stop), UiIcon.STOP, c.errorTint, c.onErrorTint)
            setOnClickListener { NavForegroundService.stopByUser(this@MainActivity) }
        }
        settingsBtn = UiPill(ui, 20f, 64, 17f, 26).apply {
            bind(null, UiIcon.SLIDERS, c.s2, c.on)
            contentDescription = getString(R.string.ui_settings)
            setOnClickListener { startActivity(android.content.Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        primary = UiPill(ui, 20f, 64, 18f, 24).apply { setOnClickListener { onPrimary() } }
        bar.addView(stopBtn, LinearLayout.LayoutParams(ui.dp(120), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = ui.dp(12) })
        bar.addView(settingsBtn, LinearLayout.LayoutParams(ui.dp(64), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = ui.dp(12) })
        bar.addView(primary, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return bar
    }

    // ---------- Hành động ----------

    private fun onPrimary() {
        when (dock) {
            DockMode.IDLE -> perms.ensureBluetooth {
                if (!NavBus.serviceRunning) NavForegroundService.startByUser(this)
                if (Prefs.isOpenMapsOnStart(this) && !MapsLauncher.openMaps(this)) mapsMissing()
            }
            DockMode.RUNNING -> if (!MapsLauncher.openMaps(this)) mapsMissing()
            DockMode.DEMO -> perms.toggleDemo { render() }
            DockMode.SETUP -> Unit
        }
    }

    private fun onQuick(slot: QuickSlot) {
        val place = slot.place
        if (place == null) {
            startActivity(PlacesActivity.intent(this, slot.kind.takeIf { it != PlaceKind.OTHER }))
            return
        }
        perms.ensureBluetooth {
            if (!NavBus.serviceRunning) NavForegroundService.startByUser(this, "place")
            if (!MapsLauncher.navigateTo(this, place)) mapsMissing()
        }
    }

    private fun mapsMissing() = Toast.makeText(this, R.string.ui_maps_missing, Toast.LENGTH_SHORT).show()

    /** Đọc điểm đã lưu khi vào màn (PlacesActivity thay đổi xong sẽ quay lại onResume), không đọc mỗi lần render. */
    private fun refreshQuick() {
        quickHint.text = ui.upper(getString(R.string.ui_quick_hint, getString(travelModeLabel(Prefs.travelMode(this)))))
        quickSlots(PlacesStore.all(this)).forEachIndexed { i, slot ->
            val b = quickBtns[i]
            val place = slot.place
            when {
                slot.kind == PlaceKind.HOME -> b.bind(getString(R.string.ui_quick_home), UiIcon.HOME, c.s2, c.on)
                slot.kind == PlaceKind.WORK -> b.bind(getString(R.string.ui_quick_work), UiIcon.WORK, c.s2, c.on)
                place != null -> b.bind(place.name, UiIcon.PIN, c.s2, c.on)
                else -> b.bind(getString(R.string.ui_quick_add), UiIcon.PLUS, android.graphics.Color.TRANSPARENT, c.dim, c.outline)
            }
            b.setOnClickListener { onQuick(slot) }
        }
    }

    private fun setPulse(on: Boolean) {
        if (on) {
            if (pulse == null) {
                pulse = ObjectAnimator.ofFloat(watchRow.dot, View.ALPHA, 1f, 0.3f).apply {
                    setDuration(750)
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    start()
                }
            }
        } else {
            pulse?.cancel()
            pulse = null
            if (::watchRow.isInitialized) watchRow.dot.alpha = 1f
        }
    }

    // ---------- Vẽ trạng thái ----------

    private fun render() {
        val access = perms.listenerGranted()
        val btMissing = perms.missingBluetoothPermissions()
        val btOk = btMissing.isEmpty()
        val btOn = getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        val running = NavBus.serviceRunning
        val demo = NavBus.demo
        val auto = autoStartStatus(running, Prefs.isAutoStart(this), access, NavBus.listenerConnected, btOk, btOn, NavBus.autoStartGate.isSuppressed())
        val st = NavBus.currentState
        val setupNeeded = !btOk || !access
        val mode = homeMode(setupNeeded, running, st.k)
        dock = dockMode(setupNeeded, running, demo)
        val ws = watchStatus(btOk, btOn, running, NavBus.subscribers, NavBus.bleState, NavBus.warnCode)
        val ms = mapsStatus(access, NavBus.listenerConnected, demo, NavBus.mapsState.k)
        val setup = mode == HomeMode.SETUP

        renderStatus(auto, ms)
        bezel.visibility = if (setup) View.GONE else View.VISIBLE
        setupList.visibility = if (setup) View.VISIBLE else View.GONE
        setupNote.visibility = if (setup) View.VISIBLE else View.GONE
        segs.visibility = if (setup) View.VISIBLE else View.GONE
        rail.visibility = if (setup) View.GONE else View.VISIBLE
        statusHead.visibility = rail.visibility
        quick.visibility = if (mode == HomeMode.IDLE && !demo) View.VISIBLE else View.GONE
        if (setup) renderSetup(btOk, btMissing, access) else renderHero(mode, auto, running, st, if (demo) getString(R.string.ui_demo_prefix) else "")
        if (!setup) renderRail(mode, ws, ms, st)
        setPulse(resumed && !setup && isWaitingForWatch(ws))

        // Cảnh báo cho rider: chỉ dịch theo mã; chuỗi kỹ thuật nằm trong khung chẩn đoán
        val mtu = NavBus.warnCode == WarnCode.MTU_TOO_SMALL
        warnRow.visibility = if (mtu) View.VISIBLE else View.GONE
        if (mtu) warnRow.setText(R.string.ui_warn_mtu)

        renderDock()
    }

    private fun renderStatus(auto: AutoStartStatus, ms: MapsStatus) {
        val (text, dot) = when (auto) {
            AutoStartStatus.RUNNING ->
                (if (ms == MapsStatus.NAVIGATING || ms == MapsStatus.REROUTING) R.string.ui_maps_navigating else R.string.ui_badge_running) to c.accent
            AutoStartStatus.READY -> R.string.ui_badge_auto to c.accent
            AutoStartStatus.DISABLED, AutoStartStatus.SUPPRESSED -> R.string.ui_badge_stopped to c.dim
            AutoStartStatus.NO_BT_PERMISSION, AutoStartStatus.NO_LISTENER -> R.string.ui_badge_missing_perm to c.warn
            AutoStartStatus.LISTENER_CONNECTING -> R.string.ui_badge_connecting to c.warn
            AutoStartStatus.BT_OFF -> R.string.ui_badge_bt_off to c.warn
        }
        setIfChanged(statusText, ui.upper(getString(text)))
        statusText.setTextColor(if (auto == AutoStartStatus.RUNNING || auto == AutoStartStatus.READY) c.accent else c.dim)
        if (statusDotColor != dot) {
            statusDotColor = dot
            statusDot.background = ui.oval(dot)
        }
    }

    private fun renderSetup(btOk: Boolean, btMissing: List<String>, access: Boolean) {
        val notif = perms.notifState()
        val battery = perms.batteryIgnored()
        val left = setupStepsLeft(btOk, notif == NotifStep.OK, access, battery)
        bindText(
            eyebrow = getString(R.string.ui_setup_eyebrow, 4 - left),
            title = ui.upper(resources.getQuantityString(R.plurals.ui_setup_steps_left, left, left)),
            titleColor = c.on, line = getString(R.string.ui_setup_sentence), sub = null, big = false,
        )
        bezel.contentDescription = null
        if (segsDone != 4 - left) {
            segsDone = 4 - left
            for (i in 0 until segs.childCount) segs.getChildAt(i).background = ui.fill(if (i < segsDone) c.accent else c.s3, 2f)
        }

        btRow.bind(
            btOk, getString(R.string.ui_perm_bt_sub),
            if (perms.isPermPermanent(btMissing)) getString(R.string.ui_btn_open_settings) else null,
            primary = true, extraVisible = perms.wasDeniedOnce(btMissing),
        )
        val (notifSub, notifLabel) = when (notif) {
            NotifStep.OK, NotifStep.REQUEST ->
                getString(R.string.ui_perm_notif_sub) to
                    if (perms.isPermPermanent(perms.postNotifications)) getString(R.string.ui_btn_open_settings) else null
            NotifStep.APP_BLOCKED -> getString(R.string.ui_perm_notif_app_blocked) to getString(R.string.ui_btn_open)
            NotifStep.CHANNEL_BLOCKED -> getString(R.string.ui_perm_notif_channel_blocked) to getString(R.string.ui_btn_open)
        }
        notifRow.bind(
            notif == NotifStep.OK, notifSub, notifLabel,
            primary = false, extraVisible = notif == NotifStep.REQUEST && perms.wasDeniedOnce(perms.postNotifications),
        )
        accessRow.bind(access, getString(R.string.ui_perm_listener_sub), null, primary = true, extraVisible = false)
        batteryRow.bind(battery, getString(R.string.ui_perm_battery_sub), null, primary = false, extraVisible = false)
    }

    private data class Hero(val title: Int, val line: Int, val sub: Int, val warn: Boolean)

    private fun renderHero(mode: HomeMode, auto: AutoStartStatus, running: Boolean, st: NavState, prefix: String) {
        segs.visibility = View.GONE
        val desc: String
        when (mode) {
            HomeMode.NAVIGATING -> {
                val man = maneuverLabel(st.m)
                val turn = if (st.m == NavState.M_ROUNDABOUT && st.x > 0) getString(R.string.ui_turn_exit, man, st.x) else man
                val dist = formatDist(st.d)
                bindText(prefix + getString(R.string.ui_nav_next_turn), ui.upper(turn), c.on, navSentence(st.s, dist), null, big = true)
                bezel.update(st.k, st.m, st.d, dist, st.s.ifEmpty { null })
                desc = man
            }
            HomeMode.REROUTING -> {
                val title = getString(R.string.ui_hero_rerouting)
                val line = getString(R.string.ui_hero_rerouting_title, prefix)
                bindText(null, ui.upper(title), c.warn, line, st.e.ifEmpty { getString(R.string.ui_hero_rerouting_meta) }, big = false)
                bezel.update(st.k, st.m, st.d, title, null)
                desc = "$title. $line"
            }
            HomeMode.ARRIVED -> {
                val title = getString(R.string.ui_hero_arrived)
                val line = getString(R.string.ui_hero_arrived_title, prefix)
                bindText(null, ui.upper(title), c.accent, line, st.s.ifEmpty { getString(R.string.ui_hero_arrived_meta) }, big = false)
                bezel.update(st.k, st.m, st.d, title, null)
                desc = "$title. $line"
            }
            else -> {
                val (title, line, sub) = idleHero(auto, running)
                bindText(null, ui.upper(title), if (idleWarn(auto, running)) c.warn else c.on, line, sub, big = false)
                bezel.update(st.k, st.m, st.d, null, null)
                desc = "$title. $line"
            }
        }
        bezel.contentDescription = desc
    }

    /** Chờ Maps khi dịch vụ đang chạy; còn lại theo trạng thái tự bật (NO_BT/NO_LISTENER đã chuyển sang màn thiết lập). */
    private fun idleHero(auto: AutoStartStatus, running: Boolean): Triple<String, String, String?> {
        if (running) {
            val idleSec = NavBus.idleRemainingSec
            return Triple(
                getString(R.string.ui_hero_wait_maps), getString(R.string.ui_hero_wait_maps_title),
                if (idleSec > 0) getString(R.string.ui_hero_idle_countdown, idleSec / 60, idleSec % 60) else null,
            )
        }
        val h = when (auto) {
            // Chỉ khi tự bật bị chặn/tạm ngưng mới cần dòng phụ; trạng thái sẵn sàng bình thường chỉ một câu ngắn.
            AutoStartStatus.READY -> Hero(R.string.ui_hero_ready, R.string.ui_hero_ready_title, 0, false)
            AutoStartStatus.SUPPRESSED -> Hero(R.string.ui_hero_stopped, R.string.ui_hero_suppressed_title, R.string.ui_hero_suppressed_meta, false)
            AutoStartStatus.BT_OFF -> Hero(R.string.ui_hero_stopped, R.string.ui_hero_bt_off_title, R.string.ui_hero_bt_off_meta, true)
            AutoStartStatus.LISTENER_CONNECTING ->
                Hero(R.string.ui_hero_connecting, R.string.ui_hero_connecting_title, R.string.ui_hero_connecting_meta, true)
            else -> Hero(R.string.ui_hero_stopped, R.string.ui_hero_disabled_title, R.string.ui_hero_disabled_meta, false)
        }
        return Triple(getString(h.title), getString(h.line), if (h.sub == 0) null else getString(h.sub))
    }

    private fun idleWarn(auto: AutoStartStatus, running: Boolean) =
        !running && (auto == AutoStartStatus.BT_OFF || auto == AutoStartStatus.LISTENER_CONNECTING)

    private fun navSentence(street: String, dist: String): CharSequence {
        val s = if (street.isNotEmpty()) getString(R.string.ui_nav_into_after, street, dist) else getString(R.string.ui_nav_after, dist)
        val i = s.lastIndexOf(dist)
        if (i < 0) return s
        return SpannableString(s).apply {
            setSpan(StyleSpan(Typeface.BOLD), i, i + dist.length, 0)
            setSpan(ForegroundColorSpan(c.accent), i, i + dist.length, 0)
        }
    }

    private fun bindText(eyebrow: String?, title: String, titleColor: Int, line: CharSequence, sub: String?, big: Boolean) {
        setIfChanged(this.eyebrow, eyebrow.orEmpty())
        this.eyebrow.visibility = if (eyebrow.isNullOrEmpty()) View.GONE else View.VISIBLE
        setIfChanged(h1, title)
        if (h1.currentTextColor != titleColor) h1.setTextColor(titleColor)
        setIfChanged(sentence, line)
        if (sentenceBig != big) {
            sentenceBig = big
            sentence.textSize = if (big) 18f else 15f
            sentence.setTextColor(if (big) c.on else c.dim)
            sentence.typeface = if (big) ui.medium else Typeface.DEFAULT
        }
        setIfChanged(subLine, sub.orEmpty())
        subLine.visibility = if (sub.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun setIfChanged(v: TextView, t: CharSequence) {
        if (v.text.toString() != t.toString()) v.text = t
    }

    private fun renderRail(mode: HomeMode, ws: WatchStatus, ms: MapsStatus, st: NavState) {
        val nav = mode == HomeMode.NAVIGATING
        remainRow.visibility = if (nav && st.r >= 0) View.VISIBLE else View.GONE
        if (st.r >= 0) remainRow.set(formatDist(st.r), null, null)
        etaRow.visibility = if (nav && st.e.isNotEmpty()) View.VISIBLE else View.GONE
        if (st.e.isNotEmpty()) etaRow.set(st.e, null, null)
        mapsRow.visibility = if (nav) View.GONE else View.VISIBLE
        autoStopRow.visibility = if (nav) View.VISIBLE else View.GONE

        val (wValue, wSub, wColor) = when (ws) {
            WatchStatus.NO_PERMISSION -> Triple(R.string.ui_watch_no_permission, R.string.ui_watch_no_permission_sub, c.error)
            WatchStatus.DELIVERY_PROBLEM -> Triple(R.string.ui_watch_delivery, R.string.ui_watch_delivery_sub, c.error)
            WatchStatus.CONNECTED -> Triple(if (nav) R.string.ui_nav_receiving else R.string.ui_watch_connected, 0, c.accent)
            WatchStatus.OFF -> Triple(R.string.ui_watch_off, R.string.ui_watch_off_sub, c.dim)
            WatchStatus.STARTING -> Triple(R.string.ui_watch_starting, R.string.ui_watch_starting_sub, c.warn)
            WatchStatus.WAITING -> Triple(R.string.ui_watch_waiting, R.string.ui_watch_waiting_sub, c.warn)
            WatchStatus.WAITING_SAVER -> Triple(R.string.ui_watch_waiting, R.string.ui_watch_waiting_saver_sub, c.warn)
            WatchStatus.BT_OFF -> Triple(R.string.ui_watch_bt_off, R.string.ui_watch_bt_off_sub, c.error)
            WatchStatus.RETRYING -> Triple(R.string.ui_watch_retrying, R.string.ui_watch_retrying_sub, c.warn)
            WatchStatus.ERROR -> Triple(R.string.ui_watch_error, R.string.ui_watch_error_sub, c.error)
            WatchStatus.AUTO_STOPPED -> Triple(R.string.ui_watch_auto_stopped, R.string.ui_watch_auto_stopped_sub, c.dim)
        }
        // Dòng giải thích chỉ hiện khi có vấn đề (đỏ/hổ phách); trạng thái bình thường chỉ một giá trị.
        watchRow.set(getString(wValue), if (wColor == c.error || wColor == c.warn) getString(wSub) else null, wColor)

        val (mValue, mSub, mColor) = when (ms) {
            MapsStatus.NO_LISTENER -> Triple(R.string.ui_maps_no_listener, R.string.ui_maps_no_listener_sub, c.error)
            MapsStatus.CONNECTING -> Triple(R.string.ui_maps_connecting, R.string.ui_maps_connecting_sub, c.dim)
            MapsStatus.IDLE -> Triple(R.string.ui_maps_idle, R.string.ui_maps_idle_sub, c.warn)
            MapsStatus.NAVIGATING -> Triple(R.string.ui_maps_navigating, R.string.ui_maps_navigating_sub, c.accent)
            MapsStatus.REROUTING -> Triple(R.string.ui_maps_rerouting, R.string.ui_maps_rerouting_sub, c.warn)
            MapsStatus.ARRIVED -> Triple(R.string.ui_maps_arrived, R.string.ui_maps_arrived_sub, c.accent)
            MapsStatus.DEMO -> Triple(R.string.ui_maps_demo, R.string.ui_maps_demo_sub, c.warn)
        }
        mapsRow.set(getString(mValue), if (mColor == c.error || (mColor == c.warn && ms != MapsStatus.IDLE)) getString(mSub) else null, mColor)

        if (nav) {
            val min = Prefs.autoStopMinutes(this)
            autoStopRow.set(if (min == 0) getString(R.string.ui_chip_off) else getString(R.string.ui_auto_stop_after, min), null, null)
        }
    }

    private fun renderDock() {
        val running = dock == DockMode.RUNNING || dock == DockMode.DEMO
        stopBtn.visibility = if (running) View.VISIBLE else View.GONE
        settingsBtn.visibility = if (running) View.GONE else View.VISIBLE
        primary.isEnabled = dock != DockMode.SETUP
        when (dock) {
            DockMode.IDLE -> primary.bind(getString(if (Prefs.isOpenMapsOnStart(this)) R.string.ui_start_maps else R.string.ui_start), UiIcon.PIN, c.accent, c.onAccent)
            DockMode.RUNNING -> primary.bind(getString(R.string.ui_back_to_maps), UiIcon.PIN, c.accent, c.onAccent)
            DockMode.DEMO -> primary.bind(getString(R.string.ui_demo_stop), null, c.warnTint, c.onWarnTint)
            DockMode.SETUP -> primary.bind(getString(R.string.ui_setup_blocked_btn), null, c.s2, c.dim)
        }
    }

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
}
