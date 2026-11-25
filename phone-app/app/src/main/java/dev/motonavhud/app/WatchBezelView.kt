package dev.motonavhud.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.ViewGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

/**
 * Chiếc Xiaomi Watch vẽ tay làm tâm điểm màn chính: hai dây mờ dần, viền kim loại, núm xoay, màn hình đen.
 * Nội dung màn hình = [ArrowPreviewView] (vòng + mũi tên) cùng khoảng cách và tên đường vẽ bên dưới.
 * Thân đồng hồ luôn tối ở cả hai theme; chỉ theme sáng có bóng đổ nhẹ.
 */
class WatchBezelView(ctx: Context, private val dark: Boolean) : ViewGroup(ctx) {
    private val arrow = ArrowPreviewView(ctx)
    private var distanceText: String? = null
    private var subText: String? = null
    private val idleText = ctx.getString(R.string.ui_hero_wait_maps)
    private val density = resources.displayMetrics.density

    private val strapFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strapEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Palette.Device.RIM_ON_DARK
    }
    private val strapColor = if (dark) Palette.Device.STRAP_ON_DARK else Palette.Device.STRAP
    private val strapEdgeColor = if (dark) Palette.Device.STRAP_EDGE_ON_DARK else Palette.Device.STRAP_EDGE
    private val crownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.Device.CROWN }
    private val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.Device.SCREEN }
    private val bigText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.Device.TEXT
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val smallText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.Device.TEXT_DIM
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    /** Giờ nhỏ ở phần trên mặt đồng hồ (giữa đỉnh vòng và mũi tên), như mặt đồng hồ thật. */
    private val clockFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val clockPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.Device.TEXT_DIM
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val clockTick = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    private val topStrap = Path()
    private val bottomStrap = Path()
    private val strapRect = RectF()
    private val crownRect = RectF()
    private var dia = 0f
    private var screenDia = 0f
    private var cx = 0f
    private var cy = 0f
    private var strapW = 0f
    private var strapH = 0f
    private var scale = 1f

    init {
        setWillNotDraw(false)
        arrow.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(arrow)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(clockTick)
        post(clockTick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(clockTick)
        super.onDetachedFromWindow()
    }

    /** [distanceText] và [subText] (tên đường) vẽ dưới mũi tên; cả hai null = đang chờ Maps. */
    fun update(k: Int, m: Int, d: Int, distanceText: String?, subText: String?) {
        arrow.update(k, m, d)
        if (distanceText != this.distanceText || subText != this.subText) {
            this.distanceText = distanceText
            this.subText = subText
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val full = 236f * density
        // Màn hình thấp: thu nhỏ đồng hồ (tối đa ~20% chiều cao) để chữ, "Đi nhanh" và nút chính vẫn vừa màn đầu.
        val maxByHeight = resources.displayMetrics.heightPixels * 0.20f
        dia = minOf(full, w - 48f * density, maxByHeight)
        scale = dia / full
        screenDia = dia - 16f * density * scale
        val h = (dia + 50f * density * scale).toInt()
        arrow.measure(
            MeasureSpec.makeMeasureSpec(screenDia.toInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(screenDia.toInt(), MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val half = screenDia / 2f
        arrow.layout((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h / 2f
        val u = density * scale
        strapW = 104f * u
        strapH = 84f * u
        val corner = 26f * u
        val left = cx - strapW / 2f

        strapRect.set(left, 0f, left + strapW, strapH)
        topStrap.reset()
        topStrap.addRoundRect(strapRect, floatArrayOf(corner, corner, corner, corner, 0f, 0f, 0f, 0f), Path.Direction.CW)
        strapRect.set(left, h - strapH, left + strapW, h.toFloat())
        bottomStrap.reset()
        bottomStrap.addRoundRect(strapRect, floatArrayOf(0f, 0f, 0f, 0f, corner, corner, corner, corner), Path.Direction.CW)
        strapEdge.strokeWidth = maxOf(1f, density)
        rimPaint.strokeWidth = maxOf(1f, 1.5f * density)

        // Viền kim loại: gradient 145deg (CSS) qua hộp bao của mặt đồng hồ.
        val rad = Math.toRadians(145.0)
        val dx = sin(rad).toFloat()
        val dy = (-cos(rad)).toFloat()
        val len = dia * (dx + dy)
        bezelPaint.shader = LinearGradient(
            cx - dx * len / 2f, cy - dy * len / 2f, cx + dx * len / 2f, cy + dy * len / 2f,
            intArrayOf(Palette.Device.BEZEL_LIGHT, Palette.Device.BEZEL_MID, Palette.Device.BEZEL_DARK),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        if (!dark) bezelPaint.setShadowLayer(24f * u, 0f, 10f * u, 0x47000000) else bezelPaint.clearShadowLayer()

        val crownLeft = cx + dia / 2f - 4f * u
        crownRect.set(crownLeft, cy - dia / 2f + 88f * u, crownLeft + 9f * u, cy - dia / 2f + 118f * u)
    }

    override fun onDraw(canvas: Canvas) {
        drawStrap(canvas, topStrap, 0f, strapH)
        drawStrap(canvas, bottomStrap, height.toFloat(), height - strapH)
        canvas.drawCircle(cx, cy, dia / 2f, bezelPaint)
        if (dark) canvas.drawCircle(cx, cy, dia / 2f - rimPaint.strokeWidth / 2f, rimPaint)
        val cr = 4f * density * scale
        canvas.drawRoundRect(crownRect, cr, cr, crownPaint)
        canvas.drawCircle(cx, cy, screenDia / 2f, screenPaint)
    }

    /** Dây đồng hồ mờ dần: trong suốt ở mép ngoài, đặc ở 65% chiều dài. */
    private fun drawStrap(canvas: Canvas, path: Path, outerY: Float, innerY: Float) {
        val pos = floatArrayOf(0f, 0.65f)
        strapFill.shader = LinearGradient(0f, outerY, 0f, innerY, intArrayOf(withAlpha(strapColor, 0), strapColor), pos, Shader.TileMode.CLAMP)
        canvas.drawPath(path, strapFill)
        strapEdge.shader = LinearGradient(0f, outerY, 0f, innerY, intArrayOf(withAlpha(strapEdgeColor, 0), strapEdgeColor), pos, Shader.TileMode.CLAMP)
        val l = cx - strapW / 2f
        val r = cx + strapW / 2f
        canvas.drawLine(l, outerY, l, innerY, strapEdge)
        canvas.drawLine(r, outerY, r, innerY, strapEdge)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val sd = screenDia
        clockPaint.textSize = sd * 0.065f
        clockFmt.timeZone = TimeZone.getDefault()
        canvas.drawText(clockFmt.format(Date()), cx, cy - sd * 0.285f, clockPaint)
        val dist = distanceText
        val sub = subText
        if (dist == null && sub == null) {
            smallText.textSize = sd * 0.075f
            canvas.drawText(idleText, cx, cy + sd * 0.33f, smallText)
            return
        }
        if (dist != null) {
            bigText.textSize = sd * 0.105f
            canvas.drawText(dist, cx, cy + sd * 0.36f, bigText)
        }
        if (!sub.isNullOrEmpty()) {
            smallText.textSize = sd * 0.055f
            val fit = TextUtils.ellipsize(sub, smallText, sd * 0.5f, TextUtils.TruncateAt.END).toString()
            canvas.drawText(fit, cx, cy + sd * 0.43f, smallText)
        }
    }

    private fun withAlpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
