package dev.motonavhud.app

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import dev.motonavhud.core.TravelMode

/** Màn Cài đặt riêng: nhóm Tự động, Google Maps, Quyền truy cập, Kết nối đồng hồ, Nâng cao. Vẽ lại ở onResume vì quyền có thể đổi trong cài đặt hệ thống. */
class SettingsActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var ui: Ui
    private lateinit var c: Palette.Tokens
    private val perms = PermissionFlow(this) { render() }

    /** Một hàng trong thẻ nhóm: vùng chạm cả hàng, biểu tượng tròn, tiêu đề, mô tả, phần cuối tùy chọn. */
    private class Item(val root: LinearLayout, val title: TextView, val sub: TextView)

    private class Perm(val item: Item, val pill: LinearLayout, val pillIcon: UiIconView, val pillText: TextView, val extra: TextView)

    private lateinit var autoRow: Item
    private lateinit var autoSw: Switch
    private val segs = LinkedHashMap<Int, UiPill>()
    private val modeSegs = LinkedHashMap<TravelMode, UiPill>()
    private lateinit var openMapsRow: Item
    private lateinit var openMapsSw: Switch
    private lateinit var placesRow: Item
    private lateinit var btPerm: Perm
    private lateinit var notifPerm: Perm
    private lateinit var listenerPerm: Perm
    private lateinit var batteryPerm: Perm
    private lateinit var tokenRow: Item
    private lateinit var demoRow: Item
    private lateinit var captureRow: Item
    private lateinit var captureSw: Switch
    private lateinit var clearRow: Item
    private lateinit var diagRow: Item
    private lateinit var diagChevron: UiIconView
    private lateinit var diagBlock: LinearLayout
    private lateinit var payload: TextView
    private lateinit var diag: TextView
    private var diagOpen = false
    private var captureCount = -1
    private var resumed = false

    private val refresh: () -> Unit = { main.post { if (resumed) render() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        c = Palette.of(dark)
        ui = Ui(this, c)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c.bg)
            fitsSystemWindows = true
        }
        root.addView(buildBar())
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(24))
        }
        body.addView(group(R.string.ui_set_group_auto, autoCard()), groupParams())
        body.addView(group(R.string.ui_set_group_maps, mapsCard()), groupParams())
        body.addView(group(R.string.ui_set_group_perms, permsCard()), groupParams())
        body.addView(group(R.string.ui_set_group_pair, pairCard()), groupParams())
        body.addView(group(R.string.ui_set_group_adv, advancedCard()), groupParams())
        body.addView(
            ui.text(12f, c.dim).apply {
                text = getString(R.string.ui_tip_tile)
                setPadding(ui.dp(12), 0, ui.dp(12), 0)
            },
        )
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        perms.handleResult(requestCode, permissions, grantResults)
    }

    private fun refreshCaptureCount() {
        Thread {
            val n = NotificationCapture.count(this@SettingsActivity)
            main.post {
                if (captureCount != n) {
                    captureCount = n
                    if (resumed) render()
                }
            }
        }.start()
    }

    // ---------- Khung ----------

    private fun buildBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(72)
        setPadding(ui.dp(8), 0, ui.dp(16), 0)
        val back = UiPill(ui, 24f, 48, 14f, 24).apply {
            bind(null, UiIcon.BACK, Color.TRANSPARENT, c.on)
            contentDescription = getString(R.string.ui_back)
            setOnClickListener { finish() }
        }
        addView(back, LinearLayout.LayoutParams(ui.dp(48), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = ui.dp(4) })
        addView(ui.text(22f, c.on, ui.bold).apply {
            text = getString(R.string.ui_settings)
            isAccessibilityHeading = true
        })
    }

    private fun groupParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ui.dp(14) }

    private fun group(titleRes: Int, card: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            ui.text(14f, c.accent, ui.bold).apply {
                text = getString(titleRes)
                isAccessibilityHeading = true
                setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(8))
            },
        )
        addView(card)
    }

    private fun card(vararg rows: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4))
        background = ui.fill(c.s1, 28f)
        rows.forEach { addView(it) }
    }

    /** [trailing] cuối hàng; [onClick] null = hàng không chạm được. */
    private fun item(icon: UiIcon, title: String, sub: String?, trailing: View?, onClick: (() -> Unit)?): Item {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(64)
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            if (onClick != null) {
                isClickable = true
                isFocusable = true
                background = ui.ripple(ui.fill(Color.TRANSPARENT, 20f))
                setOnClickListener { onClick() }
            }
        }
        val ic = FrameLayout(this).apply {
            background = ui.oval(c.accentTint)
            addView(UiIconView(this@SettingsActivity, icon, 22, c.onAccentTint), FrameLayout.LayoutParams(ui.dp(22), ui.dp(22), Gravity.CENTER))
        }
        root.addView(ic, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)).apply { marginEnd = ui.dp(14) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val t = ui.text(16f, c.on, ui.medium).apply { text = title }
        val s = ui.text(14f, c.dim).apply {
            text = sub.orEmpty()
            visibility = if (sub.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        col.addView(t)
        col.addView(s)
        root.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (trailing != null) root.addView(trailing, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = ui.dp(12) })
        return Item(root, t, s)
    }

    private fun chevron() = UiIconView(this, UiIcon.CHEVRON, 24, c.dim)

    private fun setIfChanged(v: TextView, t: CharSequence) {
        if (v.text.toString() != t.toString()) v.text = t
    }

    /** Hàng công tắc: chạm cả hàng để đổi; trạng thái đọc qua stateDescription. */
    private fun switchItem(icon: UiIcon, title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit): Pair<Item, Switch> {
        val sw = ui.switchView()
        sw.isChecked = checked
        val row = item(icon, title, sub, sw) { sw.toggle() }
        ViewCompat.setStateDescription(row.root, getString(if (checked) R.string.ui_state_on else R.string.ui_state_off))
        sw.setOnCheckedChangeListener { _, on ->
            ViewCompat.setStateDescription(row.root, getString(if (on) R.string.ui_state_on else R.string.ui_state_off))
            onChange(on)
        }
        return row to sw
    }

    // ---------- Các nhóm ----------

    private fun autoCard(): View {
        val (row, sw) = switchItem(UiIcon.PLAY, getString(R.string.ui_set_auto_start), getString(R.string.ui_auto_start_desc), Prefs.isAutoStart(this)) { on ->
            Prefs.setAutoStart(this, on)
            NavTileService.requestRefresh(this)
        }
        autoRow = row
        autoSw = sw

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(12))
        }
        box.addView(ui.text(16f, c.on, ui.medium).apply {
            text = getString(R.string.ui_set_auto_stop)
            setPadding(0, ui.dp(4), 0, ui.dp(10))
        })
        val descs = Prefs.AUTO_STOP_OPTIONS.map { min ->
            if (min == 0) getString(R.string.ui_chip_off) else resources.getQuantityString(R.plurals.ui_chip_minutes_desc, min, min)
        }
        val options = Prefs.AUTO_STOP_OPTIONS.toList()
        segmented(box, descs, minDp = 48) { i, b ->
            segs[options[i]] = b
            b.setOnClickListener {
                Prefs.setAutoStopMinutes(this@SettingsActivity, options[i])
                render()
            }
        }
        return card(row.root, box)
    }

    /** Điều khiển phân đoạn: cửa sổ hẹp thì chia nhiều hàng để mỗi đoạn rộng >= [minDp]. [bind] nhận từng nút để gắn dữ liệu và chạm. */
    private fun segmented(box: LinearLayout, descs: List<String>, minDp: Int, bind: (Int, UiPill) -> Unit) {
        val cols = chipColumns(resources.configuration.screenWidthDp - 72, descs.size, gapDp = 0, minDp = minDp)
        descs.indices.toList().chunked(cols).forEachIndexed { rowIdx, idxs ->
            val seg = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = ui.outlined(Color.TRANSPARENT, c.outline, 1f, 20f)
                clipToOutline = true
            }
            idxs.forEachIndexed { i, idx ->
                if (i > 0) seg.addView(View(this).apply { setBackgroundColor(c.outline) }, LinearLayout.LayoutParams(maxOf(1, ui.dp(1)), ViewGroup.LayoutParams.MATCH_PARENT))
                val b = UiPill(ui, 0f, 48, 14f, 16).apply { contentDescription = descs[idx] }
                bind(idx, b)
                seg.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            box.addView(seg, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (rowIdx > 0) topMargin = ui.dp(8) })
        }
    }

    private fun travelModeBox(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(12))
        }
        box.addView(ui.text(16f, c.on, ui.medium).apply {
            text = getString(R.string.ui_set_travel_mode)
            setPadding(0, ui.dp(4), 0, ui.dp(10))
        })
        val modes = TravelMode.values().toList()
        // Nhãn dài hơn số phút: mỗi đoạn cần >= 84dp, nên máy hẹp sẽ thành lưới 2x2.
        segmented(box, modes.map { getString(travelModeLabel(it)) }, minDp = 84) { i, b ->
            modeSegs[modes[i]] = b
            b.setOnClickListener {
                Prefs.setTravelMode(this@SettingsActivity, modes[i])
                render()
            }
        }
        box.addView(ui.text(13f, c.dim).apply {
            text = getString(R.string.ui_set_travel_mode_help)
            setPadding(0, ui.dp(8), 0, 0)
        })
        return box
    }

    private fun mapsCard(): View {
        val (row, sw) = switchItem(UiIcon.PIN, getString(R.string.ui_set_open_maps), getString(R.string.ui_set_open_maps_sub), Prefs.isOpenMapsOnStart(this)) { on ->
            Prefs.setOpenMapsOnStart(this, on)
        }
        openMapsRow = row
        openMapsSw = sw
        placesRow = item(UiIcon.HOME, getString(R.string.ui_set_places), null, chevron()) {
            startActivity(PlacesActivity.intent(this))
        }
        return card(openMapsRow.root, travelModeBox(), placesRow.root)
    }

    private fun permItem(icon: UiIcon, titleRes: Int, onClick: () -> Unit, onExtra: () -> Unit): Perm {
        val pillIcon = UiIconView(this, UiIcon.CHECK, 14, c.onAccentTint, 3f)
        val pillText = ui.text(13f, c.onAccentTint, ui.bold)
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(28)
            setPadding(ui.dp(10), 0, ui.dp(10), 0)
            addView(pillIcon, LinearLayout.LayoutParams(ui.dp(14), ui.dp(14)).apply { marginEnd = ui.dp(4) })
            addView(pillText)
        }
        val row = item(icon, getString(titleRes), null, pill, onClick)
        // Liên kết phụ mở cài đặt ứng dụng khi đã bị từ chối ít nhất một lần
        val extra = ui.text(13f, c.accent, ui.bold).apply {
            text = getString(R.string.ui_open_app_settings)
            minHeight = ui.dp(48)
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            setOnClickListener { onExtra() }
        }
        (row.sub.parent as LinearLayout).addView(extra)
        return Perm(row, pill, pillIcon, pillText, extra)
    }

    private fun permsCard(): View {
        btPerm = permItem(UiIcon.BLUETOOTH, R.string.ui_perm_bt_title, { perms.ensureBluetooth { render() } }) { perms.openAppSettings() }
        notifPerm = permItem(UiIcon.BELL, R.string.ui_perm_notif_title, { perms.onNotificationsClick() }) { perms.openAppSettings() }
        listenerPerm = permItem(UiIcon.PIN, R.string.ui_perm_listener_title, { perms.openListenerSettings() }) {}
        batteryPerm = permItem(UiIcon.BATTERY, R.string.ui_perm_battery_title, { perms.openBatterySettings() }) {}
        return card(btPerm.item.root, notifPerm.item.root, listenerPerm.item.root, batteryPerm.item.root)
    }

    private fun pairCard(): View {
        val change = ui.text(14f, c.accent, ui.bold).apply {
            text = getString(R.string.ui_pair_change)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        tokenRow = item(UiIcon.KEY, getString(R.string.ui_pair_title), null, change) { showTokenDialog() }
        return card(tokenRow.root)
    }

    private fun advancedCard(): View {
        demoRow = item(UiIcon.PLAY, getString(R.string.ui_adv_demo_title), getString(R.string.ui_adv_demo_sub), chevron()) {
            perms.toggleDemo { if (NavBus.demo) finish() else render() }
        }
        val (cap, sw) = switchItem(UiIcon.BELL, getString(R.string.ui_capture), null, Prefs.isCapture(this)) { on -> Prefs.setCapture(this, on) }
        captureRow = cap
        captureSw = sw
        clearRow = item(UiIcon.TERMINAL, getString(R.string.ui_capture_clear), null, null) {
            Thread {
                val n = NotificationCapture.clear(this@SettingsActivity)
                main.post {
                    captureCount = 0
                    Toast.makeText(this@SettingsActivity, resources.getQuantityString(R.plurals.ui_capture_cleared, n, n), Toast.LENGTH_SHORT).show()
                    if (resumed) render()
                }
            }.start()
        }
        diagChevron = chevron()
        diagRow = item(UiIcon.TERMINAL, getString(R.string.ui_adv_diag_title), getString(R.string.ui_adv_diag_sub), diagChevron) { toggleDiag() }
        ViewCompat.setStateDescription(diagRow.root, getString(R.string.ui_state_collapsed))

        payload = ui.text(11f, c.dim, Typeface.MONOSPACE)
        diag = ui.text(11f, c.dim, Typeface.MONOSPACE)
        diagBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(12))
            visibility = View.GONE
            addView(ui.label(getString(R.string.ui_section_payload)))
            addView(payload)
            addView(ui.space(10))
            addView(ui.label(getString(R.string.ui_section_diag)))
            addView(diag)
        }
        return card(demoRow.root, captureRow.root, clearRow.root, diagRow.root, diagBlock)
    }

    private fun toggleDiag() {
        diagOpen = !diagOpen
        diagBlock.visibility = if (diagOpen) View.VISIBLE else View.GONE
        diagChevron.rotation = if (diagOpen) 90f else 0f
        ViewCompat.setStateDescription(diagRow.root, getString(if (diagOpen) R.string.ui_state_expanded else R.string.ui_state_collapsed))
    }

    private fun showTokenDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.ui_token_hint)
            setText(Prefs.token(this@SettingsActivity))
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSelection(text.length)
            minHeight = ui.dp(48)
        }
        val box = FrameLayout(this).apply {
            setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0)
            addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.ui_pair_title)
            .setMessage(R.string.ui_token_desc)
            .setView(box)
            .setPositiveButton(R.string.ui_token_save) { _, _ ->
                val ok = Prefs.setToken(this, input.text.toString().trim())
                Toast.makeText(this, if (ok) R.string.ui_token_saved else R.string.ui_token_invalid, Toast.LENGTH_SHORT).show()
                render()
            }
            .setNeutralButton(R.string.ui_token_reset) { _, _ ->
                Prefs.setToken(this, Prefs.DEFAULT_TOKEN)
                Toast.makeText(this, R.string.ui_token_reset_done, Toast.LENGTH_SHORT).show()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------- Vẽ trạng thái ----------

    private fun render() {
        val autoOn = Prefs.isAutoStart(this)
        if (autoSw.isChecked != autoOn) autoSw.isChecked = autoOn
        val openMaps = Prefs.isOpenMapsOnStart(this)
        if (openMapsSw.isChecked != openMaps) openMapsSw.isChecked = openMaps
        val cap = Prefs.isCapture(this)
        if (captureSw.isChecked != cap) captureSw.isChecked = cap

        val selected = Prefs.autoStopMinutes(this)
        for ((min, seg) in segs) {
            val sel = min == selected
            val label = if (min == 0) getString(R.string.ui_chip_off) else getString(R.string.ui_seg_minutes, min)
            // Không kèm dấu tick: đoạn chỉ rộng ~72dp, tick làm "10 phút" rớt dòng và mất chữ. Trạng thái chọn có nền tint + stateDescription.
            if (sel) seg.bind(label, null, c.accentTint, c.onAccentTint) else seg.bind(label, null, Color.TRANSPARENT, c.on)
            seg.isSelected = sel
            ViewCompat.setStateDescription(seg, getString(if (sel) R.string.ui_state_selected else R.string.ui_state_not_selected))
        }

        val mode = Prefs.travelMode(this)
        for ((m, seg) in modeSegs) {
            val sel = m == mode
            val label = getString(travelModeLabel(m))
            if (sel) seg.bind(label, null, c.accentTint, c.onAccentTint) else seg.bind(label, null, Color.TRANSPARENT, c.on)
            seg.isSelected = sel
            ViewCompat.setStateDescription(seg, getString(if (sel) R.string.ui_state_selected else R.string.ui_state_not_selected))
        }

        val places = PlacesStore.all(this)
        setIfChanged(placesRow.sub, if (places.isEmpty()) getString(R.string.ui_places_none) else places.joinToString(", ") { it.name })
        placesRow.sub.visibility = View.VISIBLE

        renderPerms()

        setIfChanged(tokenRow.sub, maskToken(Prefs.token(this)))
        tokenRow.sub.visibility = View.VISIBLE
        setIfChanged(demoRow.title, getString(if (NavBus.demo) R.string.ui_demo_stop else R.string.ui_adv_demo_title))
        setIfChanged(clearRow.title, if (captureCount >= 0) getString(R.string.ui_capture_clear_count, captureCount) else getString(R.string.ui_capture_clear))
        setIfChanged(payload, NavBus.lastPayload.ifEmpty { getString(R.string.ui_payload_empty) })
        setIfChanged(diag, listOf(NavBus.bleStatus, NavBus.bleDetail, NavBus.warning).filter { it.isNotEmpty() }.joinToString("\n").ifEmpty { "-" })
    }

    private fun renderPerms() {
        val btMissing = perms.missingBluetoothPermissions()
        val btOk = btMissing.isEmpty()
        bindPerm(
            btPerm, btOk, if (btOk) null else getString(R.string.ui_perm_bt_sub), actionable = !btOk,
            extra = !btOk && perms.wasDeniedOnce(btMissing),
        )
        val notif = perms.notifState()
        val notifSub = when (notif) {
            NotifStep.OK -> null
            NotifStep.REQUEST -> getString(R.string.ui_perm_notif_sub)
            NotifStep.APP_BLOCKED -> getString(R.string.ui_perm_notif_app_blocked)
            NotifStep.CHANNEL_BLOCKED -> getString(R.string.ui_perm_notif_channel_blocked)
        }
        bindPerm(
            notifPerm, notif == NotifStep.OK, notifSub, actionable = notif != NotifStep.OK,
            extra = notif == NotifStep.REQUEST && perms.wasDeniedOnce(perms.postNotifications),
        )
        // Đọc thông báo và pin luôn mở được cài đặt hệ thống (để thu hồi), như bản cũ.
        val access = perms.listenerGranted()
        bindPerm(listenerPerm, access, if (access) null else getString(R.string.ui_perm_listener_sub), actionable = true, extra = false)
        val battery = perms.batteryIgnored()
        bindPerm(batteryPerm, battery, if (battery) null else getString(R.string.ui_perm_battery_sub), actionable = true, extra = false)
    }

    private fun bindPerm(p: Perm, granted: Boolean, sub: String?, actionable: Boolean, extra: Boolean) {
        val fg = if (granted) c.onAccentTint else c.onWarnTint
        p.pill.background = ui.fill(if (granted) c.accentTint else c.warnTint, 10f)
        p.pillIcon.visibility = if (granted) View.VISIBLE else View.GONE
        p.pillText.setTextColor(fg)
        setIfChanged(p.pillText, getString(if (granted) R.string.ui_status_granted else R.string.ui_status_needed))
        setIfChanged(p.item.sub, sub.orEmpty())
        p.item.sub.visibility = if (sub.isNullOrEmpty()) View.GONE else View.VISIBLE
        p.item.root.isClickable = actionable
        p.item.root.isFocusable = actionable
        p.extra.visibility = if (extra) View.VISIBLE else View.GONE
    }
}
