package dev.motonavhud.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.PathParser
import dev.motonavhud.core.MapsUri
import dev.motonavhud.core.PlaceKind
import dev.motonavhud.core.QuickPlace

/** Màn "Điểm đến nhanh": 3 ô (Nhà, Cơ quan, thứ ba) + form sửa. Giao diện dựng bằng code theo Palette. */
class PlacesActivity : Activity() {
    private lateinit var c: Palette.Tokens
    private lateinit var scroll: ScrollView
    private lateinit var rail: LinearLayout
    private lateinit var form: LinearLayout
    private lateinit var nameField: EditText
    private lateinit var addrField: EditText
    private lateinit var nameErr: TextView
    private lateinit var addrErr: TextView
    private lateinit var locBtn: TextView
    private lateinit var locErr: TextView
    private lateinit var settingsBtn: TextView
    private lateinit var saveBtn: TextView
    private lateinit var deleteBtn: TextView

    // Trạng thái form (giữ qua xoay màn hình).
    private var editKind: PlaceKind? = null
    private var editId: String? = null
    private var lat: Double? = null
    private var lng: Double? = null
    /** Chữ địa chỉ đúng lúc có tọa độ; nếu người dùng sửa chữ khác đi thì phải geocode lại. */
    private var coordsAddr: String? = null
    private var locating = false
    private var saving = false
    private var permDeniedOnce = false

    /** Tăng mỗi lần bắt đầu/huỷ một tác vụ bất đồng bộ; callback cũ so khớp để bỏ qua. */
    private var gen = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        c = Palette.of(dark)
        setContentView(buildUi())
        refreshRail()
        if (savedInstanceState != null) restore(savedInstanceState) else applyIntent()
        updateFormState()
    }

    override fun onDestroy() {
        gen++
        super.onDestroy()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putBoolean("form", form.visibility == View.VISIBLE)
        out.putString("kind", editKind?.name)
        out.putString("id", editId)
        out.putString("name", nameField.text.toString())
        out.putString("addr", addrField.text.toString())
        out.putDouble("lat", lat ?: Double.NaN)
        out.putDouble("lng", lng ?: Double.NaN)
        out.putString("coordsAddr", coordsAddr)
        out.putBoolean("denied", permDeniedOnce)
    }

    private fun restore(b: Bundle) {
        editKind = b.getString("kind")?.let { runCatching { PlaceKind.valueOf(it) }.getOrNull() }
        editId = b.getString("id")
        lat = b.getDouble("lat", Double.NaN).takeUnless { it.isNaN() }
        lng = b.getDouble("lng", Double.NaN).takeUnless { it.isNaN() }
        coordsAddr = b.getString("coordsAddr")
        permDeniedOnce = b.getBoolean("denied")
        nameField.setText(b.getString("name").orEmpty())
        addrField.setText(b.getString("addr").orEmpty())
        form.visibility = if (b.getBoolean("form") && editKind != null) View.VISIBLE else View.GONE
    }

    private fun applyIntent() {
        val id = intent.getStringExtra(EXTRA_EDIT_ID)
        val existing = id?.let { PlacesStore.get(this, it) }
        val kind = existing?.kind ?: intent.getStringExtra(EXTRA_KIND)?.let { runCatching { PlaceKind.valueOf(it) }.getOrNull() }
        if (kind != null) loadSlot(kind, existing ?: slotPlace(kind))
    }

    // ---- UI ----

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c.bg)
            fitsSystemWindows = true
        }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        val back = IconView(this, PATH_BACK, c.on).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = getString(R.string.pl_back)
            isClickable = true
            setOnClickListener { finish() }
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(label(R.string.pl_title, 22f, c.on, medium = true).apply { isAccessibilityHeading = true },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)))

        scroll = ScrollView(this).apply { isFillViewport = false }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(24))
        }
        col.addView(label(R.string.pl_intro, 15f, c.dim).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(24), 0, dp(24), dp(16))
        })
        rail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(rail, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(20), 0, dp(20), 0) })
        form = buildForm()
        col.addView(form, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(20), dp(20), dp(20), 0) })
        scroll.addView(col, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun buildForm(): LinearLayout {
        val f = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = fill(c.s1, 24)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            visibility = View.GONE
        }
        f.addView(caps(R.string.pl_field_name))
        nameField = field(imeDone = false).apply { hint = getString(R.string.pl_field_name) }
        f.addView(nameField, fieldLp(top = 6))
        nameErr = errLine(R.string.pl_err_name)
        f.addView(nameErr)

        f.addView(caps(R.string.pl_field_address), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        addrField = field(imeDone = true).apply { hint = getString(R.string.pl_field_address) }
        addrField.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { onSave(); true } else false
        }
        f.addView(addrField, fieldLp(top = 6))
        addrErr = errLine(R.string.pl_err_address)
        f.addView(addrErr)

        locBtn = button(R.string.pl_use_location, c.accentTint, c.onAccentTint).apply { setOnClickListener { onUseLocation() } }
        f.addView(locBtn, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14) })
        locErr = errLine().apply { setPadding(0, dp(8), 0, 0) }
        f.addView(locErr)
        settingsBtn = label(R.string.pl_open_settings, 14f, c.accent, medium = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(48)
            visibility = View.GONE
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
        }
        f.addView(settingsBtn)

        f.addView(label(R.string.pl_hint, 13f, c.dim).apply { setLineSpacing(dp(2).toFloat(), 1f) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        saveBtn = button(R.string.pl_save, c.accent, c.onAccent).apply { setOnClickListener { onSave() } }
        f.addView(saveBtn, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14) })
        deleteBtn = label(R.string.pl_delete, 14f, c.error, medium = true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setOnClickListener { onDelete() }
        }
        f.addView(deleteBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        return f
    }

    private fun refreshRail() {
        rail.removeAllViews()
        rail.addView(View(this).apply { setBackgroundColor(c.line) }, LinearLayout.LayoutParams(-1, dp(1)))
        for (kind in PlaceKind.values()) rail.addView(slotRow(kind, slotPlace(kind)))
    }

    private fun slotPlace(kind: PlaceKind): QuickPlace? =
        if (kind == PlaceKind.OTHER) PlacesStore.all(this).firstOrNull { it.kind == PlaceKind.OTHER }
        else PlacesStore.forKind(this, kind)

    private fun slotRow(kind: PlaceKind, place: QuickPlace?): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(76)
        }
        val tile = IconView(
            this, when (kind) { PlaceKind.HOME -> PATH_HOME; PlaceKind.WORK -> PATH_WORK; PlaceKind.OTHER -> PATH_PLUS },
            if (place != null) c.onAccentTint else c.dim,
        ).apply {
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = if (place != null) fill(c.accentTint, 14)
            else GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setStroke(dp(1.5f), c.outline); setColor(0) }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        row.addView(tile, LinearLayout.LayoutParams(dp(44), dp(44)))
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = place?.name ?: getString(when (kind) { PlaceKind.HOME -> R.string.pl_home; PlaceKind.WORK -> R.string.pl_work; PlaceKind.OTHER -> R.string.pl_third })
        text.addView(label(title, 17f, c.on, medium = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        text.addView(label(place?.address?.ifBlank { null } ?: getString(R.string.pl_not_set), 14f, c.dim).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16); topMargin = dp(10); bottomMargin = dp(10) })
        val edit = label(R.string.pl_edit, 14f, c.accent, medium = true).apply {
            gravity = Gravity.CENTER
            minWidth = dp(56)
            minHeight = dp(48)
            setPadding(dp(8), 0, dp(4), 0)
            setOnClickListener { loadSlot(kind, place) }
        }
        row.addView(edit, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(row, LinearLayout.LayoutParams(-1, -2))
        wrap.addView(View(this).apply { setBackgroundColor(c.line) }, LinearLayout.LayoutParams(-1, dp(1)))
        return wrap
    }

    // ---- Form ----

    private fun loadSlot(kind: PlaceKind, existing: QuickPlace?) {
        gen++
        locating = false
        saving = false
        editKind = kind
        editId = existing?.id
        lat = existing?.lat
        lng = existing?.lng
        coordsAddr = existing?.takeIf { it.hasCoordinates }?.address
        nameField.setText(existing?.name ?: defaultName(kind))
        addrField.setText(existing?.address.orEmpty())
        clearErrors()
        form.visibility = View.VISIBLE
        updateFormState()
        scroll.post { scroll.smoothScrollTo(0, form.top) }
    }

    private fun defaultName(kind: PlaceKind) = when (kind) {
        PlaceKind.HOME -> getString(R.string.pl_home)
        PlaceKind.WORK -> getString(R.string.pl_work)
        PlaceKind.OTHER -> ""
    }

    private fun clearErrors() {
        nameErr.visibility = View.GONE
        addrErr.visibility = View.GONE
        showLocMessage(null, false)
    }

    private fun showLocMessage(msg: CharSequence?, settings: Boolean) {
        locErr.text = msg
        locErr.visibility = if (msg == null) View.GONE else View.VISIBLE
        settingsBtn.visibility = if (settings) View.VISIBLE else View.GONE
    }

    private fun updateFormState() {
        locBtn.isEnabled = !locating && !saving
        locBtn.alpha = if (locBtn.isEnabled) 1f else 0.6f
        locBtn.text = getString(if (locating) R.string.pl_locating else R.string.pl_use_location)
        saveBtn.isEnabled = !saving
        saveBtn.alpha = if (saving) 0.6f else 1f
        saveBtn.text = getString(if (saving) R.string.pl_saving else R.string.pl_save)
        deleteBtn.visibility = if (editId?.let { PlacesStore.get(this, it) } != null && !saving) View.VISIBLE else View.GONE
    }

    private fun onUseLocation() {
        if (locating || saving) return
        if (!LocationHelper.hasPermission(this)) {
            requestPermissions(LocationHelper.PERMISSIONS, REQ_LOCATION)
            return
        }
        locate()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_LOCATION) return
        if (grantResults.any { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            locate()
        } else if (permDeniedOnce || !shouldShowRequestPermissionRationale(LocationHelper.PERMISSION)) {
            // Đã từ chối từ trước hoặc hệ thống không cho hỏi nữa: chỉ còn cách bật trong Cài đặt.
            permDeniedOnce = true
            showLocMessage(getString(R.string.pl_perm_settings_hint), true)
        } else {
            permDeniedOnce = true
            showLocMessage(getString(R.string.pl_err_perm_denied), false)
        }
    }

    private fun locate() {
        val my = ++gen
        locating = true
        showLocMessage(null, false)
        updateFormState()
        LocationHelper.currentLocation(this) { fix ->
            if (my != gen || isFinishing || isDestroyed) return@currentLocation
            if (fix == null) {
                locating = false
                showLocMessage(getString(R.string.pl_err_no_fix), false)
                updateFormState()
                return@currentLocation
            }
            val (la, lo) = fix
            LocationHelper.reverseGeocode(this, la, lo) { addr ->
                if (my != gen || isFinishing || isDestroyed) return@reverseGeocode
                // Tọa độ, địa chỉ và coordsAddr đổi cùng lúc: không để Lưu ghép địa chỉ cũ với tọa độ mới.
                lat = la
                lng = lo
                val text = addr?.takeIf { it.isNotBlank() } ?: "%.6f, %.6f".format(java.util.Locale.US, la, lo)
                addrField.setText(text)
                addrField.setSelection(text.length)
                coordsAddr = text
                addrErr.visibility = View.GONE
                locating = false
                updateFormState()
            }
        }
    }

    private fun onSave() {
        if (saving) return
        val kind = editKind ?: return
        val name = nameField.text.toString().trim()
        val addr = addrField.text.toString().trim()
        nameErr.visibility = if (name.isEmpty()) View.VISIBLE else View.GONE
        addrErr.visibility = if (addr.isEmpty()) View.VISIBLE else View.GONE
        if (name.isEmpty() || addr.isEmpty()) return
        val id = editId ?: PlacesStore.newId().also { editId = it }
        val la = lat
        val lo = lng
        if (la != null && lo != null && addr == coordsAddr && MapsUri.validCoordinates(la, lo)) {
            commit(QuickPlace(id, name, addr, la, lo, kind))
            return
        }
        val my = ++gen
        locating = false
        saving = true
        updateFormState()
        LocationHelper.forwardGeocode(this, addr) { p ->
            if (my != gen || isFinishing || isDestroyed) return@forwardGeocode
            val ok = p != null && MapsUri.validCoordinates(p.first, p.second)
            commit(QuickPlace(id, name, addr, if (ok) p!!.first else null, if (ok) p!!.second else null, kind))
        }
    }

    private fun commit(place: QuickPlace) {
        saving = false
        if (!PlacesStore.save(this, place)) {
            updateFormState()
            showLocMessage(getString(R.string.pl_err_full), false)
            return
        }
        ShortcutPublisher.publish(this)
        closeForm()
    }

    private fun onDelete() {
        val id = editId ?: return
        PlacesStore.remove(this, id)
        ShortcutPublisher.publish(this)
        closeForm()
    }

    private fun closeForm() {
        gen++
        editKind = null
        editId = null
        lat = null
        lng = null
        coordsAddr = null
        locating = false
        saving = false
        nameField.setText("")
        addrField.setText("")
        clearErrors()
        form.visibility = View.GONE
        refreshRail()
        updateFormState()
    }

    // ---- Helpers ----

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private fun fill(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun label(res: Int, sp: Float, color: Int, medium: Boolean = false) = label(getString(res), sp, color, medium)

    private fun label(text: CharSequence, sp: Float, color: Int, medium: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = sp
        setTextColor(color)
        typeface = Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    }

    /** Nhãn chữ hoa nhỏ 12sp, cách chữ 0.1. */
    private fun caps(res: Int) = label(getString(res).uppercase(), 12f, c.dim, medium = true).apply { letterSpacing = 0.1f }

    private fun errLine(res: Int? = null) = label(res?.let { getString(it) } ?: "", 13f, c.error).apply {
        visibility = View.GONE
        setPadding(0, dp(6), 0, 0)
    }

    private fun fieldLp(top: Int) = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(top) }

    private fun field(imeDone: Boolean) = EditText(this).apply {
        textSize = 16f
        setTextColor(c.on)
        setHintTextColor(c.dim)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setSingleLine(true)
        imeOptions = if (imeDone) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
        setPadding(dp(16), 0, dp(16), 0)
        gravity = Gravity.CENTER_VERTICAL
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), outlined(c.accent, 2f))
            addState(intArrayOf(), outlined(c.outline, 1.5f))
        }
    }

    private fun outlined(stroke: Int, widthDp: Float) = GradientDrawable().apply {
        setColor(c.bg)
        cornerRadius = dp(14).toFloat()
        setStroke(dp(widthDp), stroke)
    }

    private fun button(res: Int, bg: Int, fg: Int) = label(res, 16f, fg, medium = true).apply {
        gravity = Gravity.CENTER
        background = fill(bg, 16)
        isClickable = true
    }

    /** Vẽ icon nét 2/24 từ chuỗi path SVG (hệ 24x24), màu theo token. */
    private class IconView(ctx: Context, pathData: String, color: Int) : View(ctx) {
        private val path = PathParser.createPathFromPathData(pathData)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }

        override fun onDraw(canvas: Canvas) {
            val w = width - paddingLeft - paddingRight
            val h = height - paddingTop - paddingBottom
            val s = minOf(w, h) / 24f
            canvas.save()
            canvas.translate(paddingLeft + (w - 24f * s) / 2f, paddingTop + (h - 24f * s) / 2f)
            canvas.scale(s, s)
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_EDIT_ID = "edit_id"
        private const val REQ_LOCATION = 21
        private const val PATH_BACK = "M19 12H5M11 6l-6 6 6 6"
        private const val PATH_HOME = "M4 11l8-7 8 7v9a1 1 0 0 1-1 1h-4v-6h-6v6H5a1 1 0 0 1-1-1z"
        private const val PATH_WORK = "M5 7h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z" +
            "M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2M3 13h18"
        private const val PATH_PLUS = "M12 5v14M5 12h14"

        fun intent(ctx: Context, kind: PlaceKind? = null, editId: String? = null): Intent =
            Intent().setClassName(ctx, PlacesActivity::class.java.name).apply {
                if (kind != null) putExtra(EXTRA_KIND, kind.name)
                if (editId != null) putExtra(EXTRA_EDIT_ID, editId)
            }
    }
}
