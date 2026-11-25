package dev.motonavhud.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.graphics.PathParser

/** Hàm dựng View dùng chung cho các màn (không XML, không Material): chữ, đường kẻ, nền bo góc, công tắc. */
class Ui(val ctx: Context, val c: Palette.Tokens) {
    private val density = ctx.resources.displayMetrics.density

    fun dp(v: Int): Int = (v * density + 0.5f).toInt()
    fun dpf(v: Float): Float = v * density
    fun sp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, ctx.resources.displayMetrics)

    /** Tiêu đề lớn: chữ hẹp đậm. */
    val condensed: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val bold: Typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)

    fun upper(s: CharSequence): String = s.toString().uppercase(ctx.resources.configuration.locales[0])

    fun space(h: Int) = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    fun hspace(w: Int) = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(dp(w), 1) }

    fun text(sizeSp: Float, color: Int, face: Typeface? = null) = TextView(ctx).apply {
        textSize = sizeSp
        setTextColor(color)
        if (face != null) typeface = face
    }

    /** Heading lớn (~40sp, chữ hoa, chữ hẹp đậm). Cao dòng 1.25 để dấu chồng tiếng Việt (Ẵ, Ầ) không bị cắt. */
    fun h1() = text(40f, c.on, condensed).apply {
        letterSpacing = 0.01f
        lineHeight = (sp(40f) * 1.25f).toInt()
        isAccessibilityHeading = true
    }

    /** Nhãn nhỏ chữ hoa 12sp, màu phụ. */
    fun label(t: CharSequence? = null) = text(12f, c.dim, medium).apply {
        letterSpacing = 0.1f
        if (t != null) text = upper(t)
    }

    fun fill(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dpf(radiusDp)
    }

    fun outlined(fillColor: Int, strokeColor: Int, strokeDp: Float, radiusDp: Float) = GradientDrawable().apply {
        setColor(fillColor)
        setStroke(maxOf(1, dpf(strokeDp).toInt()), strokeColor)
        cornerRadius = dpf(radiusDp)
    }

    /** Hiệu ứng chạm; nền [content] làm mặt nạ để gợn sóng bo theo góc. */
    fun ripple(content: Drawable): Drawable =
        RippleDrawable(ColorStateList.valueOf((c.on and 0x00FFFFFF) or 0x26000000), content, null)

    fun hairline() = View(ctx).apply {
        setBackgroundColor(c.line)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, dp(1)))
    }

    fun switchView() = Switch(ctx).apply {
        val checked = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = ColorStateList(checked, intArrayOf(c.onAccent, c.outline))
        trackTintList = ColorStateList(checked, intArrayOf(c.accent, c.s3))
        // Cả hàng là vùng chạm; công tắc chỉ hiển thị trạng thái.
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun dot(sizeDp: Int = 8) = View(ctx).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    fun oval(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }
}

/** Biểu tượng vẽ bằng path trên lưới 24 (nét 2). */
enum class UiIcon(val raw: List<String>, val filled: Boolean = false) {
    PIN(listOf("M12 21s-6-5.2-6-10a6 6 0 0 1 12 0c0 4.8-6 10-6 10z", "M10 11a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z")),
    HOME(listOf("M4 11l8-7 8 7v9a1 1 0 0 1-1 1h-4v-6h-6v6H5a1 1 0 0 1-1-1z")),
    WORK(listOf("M5 7h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z", "M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2M3 13h18")),
    PLUS(listOf("M12 5v14M5 12h14")),
    SLIDERS(listOf("M4 7h10M18 7h2M4 17h2M10 17h10", "M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z", "M6 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z")),
    STOP(listOf("M8.5 6h7a2.5 2.5 0 0 1 2.5 2.5v7a2.5 2.5 0 0 1-2.5 2.5h-7a2.5 2.5 0 0 1-2.5-2.5v-7A2.5 2.5 0 0 1 8.5 6z"), filled = true),
    CHECK(listOf("M5 12.5l4.5 4.5L19 7.5")),
    BACK(listOf("M19 12H5M11 6l-6 6 6 6")),
    CHEVRON(listOf("M9 6l6 6-6 6")),
    BLUETOOTH(listOf("M7 7l10 10-5 4V3l5 4L7 17")),
    BELL(listOf("M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 21h4")),
    BATTERY(listOf("M5 8h12a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2z", "M21 11v3")),
    KEY(listOf("M12 15a4 4 0 1 1 -8 0a4 4 0 0 1 8 0z", "M11 12l9-9M16 7l3 3")),
    PLAY(listOf("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z", "M10 8.5v7l6-3.5z")),
    TERMINAL(listOf("M5 8l4 4-4 4", "M12 17h7"));

    val paths: List<Path> by lazy { raw.map { PathParser.createPathFromPathData(it) } }
}

class UiIconView(ctx: Context, private val icon: UiIcon, private val sizeDp: Int, color: Int, strokeW: Float = 2f) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = if (icon.filled) Paint.Style.FILL else Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = strokeW
        this.color = color
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setColor(color: Int) {
        if (paint.color != color) {
            paint.color = color
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val px = (sizeDp * resources.displayMetrics.density + 0.5f).toInt()
        setMeasuredDimension(px, px)
    }

    override fun onDraw(canvas: Canvas) {
        val s = width / 24f
        canvas.save()
        canvas.scale(s, s)
        icon.paths.forEach { canvas.drawPath(it, paint) }
        canvas.restore()
    }
}

/** Logo MotoNav: ô vuông bo góc màu nhấn, cung và mũi tên (lưới 40). */
class UiLogoView(ctx: Context, private val c: Palette.Tokens) : View(ctx) {
    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.accent }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        color = c.onAccent
    }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.onAccent }
    private val arcPath = PathParser.createPathFromPathData("M13 27a10 10 0 1 1 14 0")
    private val arrowPath = PathParser.createPathFromPathData("M20 12l5 12-5-3-5 3z")
    private val rect = RectF(0f, 0f, 40f, 40f)

    override fun onDraw(canvas: Canvas) {
        val s = width / 40f
        canvas.save()
        canvas.scale(s, s)
        canvas.drawRoundRect(rect, 12f, 12f, box)
        canvas.drawPath(arcPath, arc)
        canvas.drawPath(arrowPath, arrow)
        canvas.restore()
    }
}

/** Nút bo góc (icon tùy chọn + chữ). Đọc là Button cho trợ năng; style đổi theo [bind]. */
class UiPill(
    private val ui: Ui,
    private val radiusDp: Float,
    heightDp: Int,
    textSp: Float,
    private val iconDp: Int = 24,
    /** true: biểu tượng nằm trên chữ, chữ một dòng (cắt bằng dấu ...). */
    private val stacked: Boolean = false,
) : LinearLayout(ui.ctx) {
    private val label = ui.text(textSp, ui.c.on, ui.bold).apply {
        maxLines = if (stacked) 1 else 2
        if (stacked) ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
    }
    private var iconView: UiIconView? = null
    private var curIcon: UiIcon? = null
    private var curStyle: List<Int>? = null

    init {
        orientation = if (stacked) VERTICAL else HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        minimumHeight = ui.dp(heightDp)
        setPadding(ui.dp(12), 0, ui.dp(12), 0)
        addView(label)
    }

    /** [outline] != null: nền [bg] kèm viền 1.5dp. Chỉ dựng lại phần thực sự đổi. */
    fun bind(text: CharSequence?, icon: UiIcon?, bg: Int, fg: Int, outline: Int? = null) {
        if (icon != curIcon) {
            iconView?.let { removeView(it) }
            iconView = icon?.let { UiIconView(context, it, iconDp, fg).also { v -> addView(v, 0, LayoutParams(ui.dp(iconDp), ui.dp(iconDp)).apply { if (text.isNullOrEmpty()) Unit else if (stacked) bottomMargin = ui.dp(4) else marginEnd = ui.dp(8) }) } }
            curIcon = icon
        }
        iconView?.setColor(fg)
        if (label.text.toString() != (text?.toString() ?: "")) label.text = text
        label.visibility = if (text.isNullOrEmpty()) GONE else VISIBLE
        if (label.currentTextColor != fg) label.setTextColor(fg)
        val style = listOf(bg, fg, outline ?: 0, if (outline != null) 1 else 0)
        if (style != curStyle) {
            curStyle = style
            val shape = if (outline != null) ui.outlined(bg, outline, 1.5f, radiusDp) else ui.fill(bg, radiusDp)
            background = ui.ripple(shape)
        }
    }

    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name
}

/** Hàng trạng thái phẳng: nhãn bên trái, giá trị đậm (chấm màu tùy chọn) bên phải, dòng phụ chỉ khi cần giải thích, đường kẻ dưới chân. */
class UiRailRow(private val ui: Ui, labelText: CharSequence) : LinearLayout(ui.ctx) {
    val dot = ui.dot(8)
    private val value = ui.text(16f, ui.c.on, ui.bold).apply { gravity = Gravity.END }
    private val sub = ui.text(13f, ui.c.dim).apply { gravity = Gravity.END }
    private var dotColor = 0

    init {
        orientation = VERTICAL
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(48)
            setPadding(0, ui.dp(6), 0, ui.dp(6))
        }
        row.addView(ui.text(15f, ui.c.dim).apply { text = labelText })
        val right = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.END
        }
        val line = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        line.addView(dot, LayoutParams(ui.dp(8), ui.dp(8)).apply { marginEnd = ui.dp(8) })
        line.addView(value)
        right.addView(line)
        right.addView(sub)
        row.addView(right, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ui.dp(16) })
        addView(row)
        addView(ui.hairline())
    }

    /** [dotColor] null: ẩn chấm. [subText] rỗng/null: ẩn dòng phụ. */
    fun set(valueText: CharSequence, subText: CharSequence?, dotColor: Int?) {
        if (value.text.toString() != valueText.toString()) value.text = valueText
        val s = subText?.toString().orEmpty()
        if (sub.text.toString() != s) sub.text = s
        sub.visibility = if (s.isEmpty()) GONE else VISIBLE
        if (dotColor == null) {
            dot.visibility = GONE
        } else {
            dot.visibility = VISIBLE
            if (this.dotColor != dotColor) {
                this.dotColor = dotColor
                dot.background = ui.oval(dotColor)
            }
        }
    }
}

/** Hàng thiết lập quyền: dấu đã xong / chưa, tiêu đề + mô tả, nút hành động ở cuối (chỉ khi chưa xong), liên kết phụ mở cài đặt ứng dụng. */
class UiSetupRow(private val ui: Ui, titleText: CharSequence, private val actionLabelDefault: CharSequence) : LinearLayout(ui.ctx) {
    private val marker = FrameLayout(context)
    private val check = UiIconView(context, UiIcon.CHECK, 16, ui.c.onAccent, 3.5f)
    private val sub = ui.text(14f, ui.c.dim)
    private val extra = ui.text(13f, ui.c.accent, ui.bold).apply {
        minHeight = ui.dp(48)
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
    }
    private val action = UiPill(ui, 12f, 48, 14f)
    private var done: Boolean? = null

    init {
        orientation = VERTICAL
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(76)
            setPadding(0, ui.dp(8), 0, ui.dp(8))
        }
        marker.addView(check, FrameLayout.LayoutParams(ui.dp(16), ui.dp(16), Gravity.CENTER))
        row.addView(marker, LayoutParams(ui.dp(28), ui.dp(28)).apply { marginEnd = ui.dp(16) })
        val col = LinearLayout(context).apply { orientation = VERTICAL }
        col.addView(ui.text(17f, ui.c.on, ui.bold).apply { text = titleText })
        col.addView(sub)
        col.addView(extra)
        row.addView(col, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(action, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = ui.dp(12) })
        addView(row)
        addView(ui.hairline())
        extra.text = ui.ctx.getString(R.string.ui_open_app_settings)
    }

    fun onAction(l: () -> Unit) = action.setOnClickListener { l() }
    fun onExtra(l: () -> Unit) = extra.setOnClickListener { l() }

    /** [actionLabel] null dùng nhãn mặc định; [primary] = nút nền nhấn, ngược lại nút viền. */
    fun bind(done: Boolean, subText: CharSequence, actionLabel: CharSequence?, primary: Boolean, extraVisible: Boolean) {
        if (this.done != done) {
            this.done = done
            marker.background = if (done) ui.oval(ui.c.accent) else ui.outlined(Color.TRANSPARENT, ui.c.outline, 2f, 14f)
            check.visibility = if (done) VISIBLE else GONE
        }
        if (sub.text.toString() != subText.toString()) sub.text = subText
        action.visibility = if (done) GONE else VISIBLE
        extra.visibility = if (!done && extraVisible) VISIBLE else GONE
        if (!done) {
            val label = actionLabel ?: actionLabelDefault
            if (primary) action.bind(label, null, ui.c.accent, ui.c.onAccent)
            else action.bind(label, null, Color.TRANSPARENT, ui.c.on, ui.c.outline)
        }
    }
}

/** Nhãn phương tiện dẫn đường dùng chung cho màn chính và Cài đặt. */
fun travelModeLabel(m: dev.motonavhud.core.TravelMode): Int = when (m) {
    dev.motonavhud.core.TravelMode.TWO_WHEELER -> R.string.ui_mode_two_wheeler
    dev.motonavhud.core.TravelMode.DRIVE -> R.string.ui_mode_drive
    dev.motonavhud.core.TravelMode.BICYCLE -> R.string.ui_mode_bicycle
    dev.motonavhud.core.TravelMode.WALK -> R.string.ui_mode_walk
}
