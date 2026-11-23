package dev.motonavhud.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import dev.motonavhud.core.NavState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Vòng mặt đồng hồ thu nhỏ trên phone: vẽ mũi tên maneuver + vòng màu cảnh báo đồng bộ với Watch S5. */
class ArrowPreviewView(ctx: Context) : View(ctx) {
    private var maneuver = NavState.M_UNKNOWN
    private var stateK = NavState.K_IDLE
    private var distanceM = -1

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#080B10") }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#1E293B") }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    private val arcRect = RectF()
    private val path = Path()

    fun update(k: Int, m: Int, d: Int) {
        if (k == stateK && m == maneuver && d == distanceM) return
        stateK = k
        maneuver = m
        distanceM = d
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val size = minOf(w, h)
        if (size <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val r = size / 2f - size * 0.06f

        canvas.drawCircle(cx, cy, r, bgPaint)

        val ringW = size * 0.065f
        trackPaint.strokeWidth = ringW
        ringPaint.strokeWidth = ringW
        arcRect.set(cx - r + ringW / 2f, cy - r + ringW / 2f, cx + r - ringW / 2f, cy + r - ringW / 2f)
        canvas.drawArc(arcRect, 135f, 270f, false, trackPaint)

        val (ringColor, sweep) = when (stateK) {
            NavState.K_ARRIVED -> Color.parseColor("#34D399") to 270f
            NavState.K_REROUTING -> Color.parseColor("#FBBF24") to 180f
            NavState.K_NAVIGATING -> {
                val c = when {
                    distanceM in 0..50 -> Color.parseColor("#FF4D2D")
                    distanceM in 51..200 -> Color.parseColor("#FFD000")
                    else -> Color.parseColor("#2DD4BF")
                }
                val pct = if (distanceM >= 0) (1f - (distanceM / 500f)).coerceIn(0.08f, 1f) else 0.25f
                c to (270f * pct)
            }
            else -> Color.parseColor("#334155") to 0f
        }
        if (sweep > 0f) {
            ringPaint.color = ringColor
            canvas.drawArc(arcRect, 135f, sweep, false, ringPaint)
        }

        // Vẽ biểu tượng maneuver bên trong mặt tròn
        val s = size * 0.34f
        strokePaint.strokeWidth = size * 0.095f
        strokePaint.color = if (stateK == NavState.K_IDLE) Color.parseColor("#475569") else Color.WHITE
        fillPaint.color = strokePaint.color

        when {
            stateK == NavState.K_IDLE || stateK == NavState.K_REROUTING -> {
                canvas.drawCircle(cx, cy, s * 0.45f, strokePaint)
            }
            stateK == NavState.K_ARRIVED || maneuver == NavState.M_ARRIVE -> {
                canvas.drawLine(cx - s * 0.4f, cy - s * 0.65f, cx - s * 0.4f, cy + s * 0.7f, strokePaint)
                path.reset()
                path.moveTo(cx - s * 0.35f, cy - s * 0.65f)
                path.lineTo(cx + s * 0.55f, cy - s * 0.65f)
                path.lineTo(cx + s * 0.3f, cy - s * 0.25f)
                path.lineTo(cx + s * 0.55f, cy + s * 0.15f)
                path.lineTo(cx - s * 0.35f, cy + s * 0.15f)
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            maneuver == NavState.M_ROUNDABOUT -> {
                canvas.drawCircle(cx, cy + s * 0.2f, s * 0.35f, strokePaint)
                drawArrow(canvas, cx, cy - s * 0.15f, cx, cy - s * 0.7f, s * 0.36f)
            }
            maneuver == NavState.M_UTURN -> {
                path.reset()
                path.moveTo(cx + s * 0.35f, cy + s * 0.65f)
                path.lineTo(cx + s * 0.35f, cy - s * 0.25f)
                path.quadTo(cx + s * 0.35f, cy - s * 0.7f, cx, cy - s * 0.7f)
                path.quadTo(cx - s * 0.35f, cy - s * 0.7f, cx - s * 0.35f, cy - s * 0.25f)
                path.lineTo(cx - s * 0.35f, cy + s * 0.2f)
                canvas.drawPath(path, strokePaint)
                drawHead(canvas, cx - s * 0.35f, cy + s * 0.2f, Math.PI / 2.0, s * 0.36f)
            }
            maneuver in listOf(NavState.M_LEFT, NavState.M_RIGHT) -> {
                val dir = if (maneuver == NavState.M_LEFT) -1f else 1f
                path.reset()
                path.moveTo(cx - dir * s * 0.3f, cy + s * 0.65f)
                path.lineTo(cx - dir * s * 0.3f, cy - s * 0.15f)
                path.lineTo(cx + dir * s * 0.4f, cy - s * 0.15f)
                canvas.drawPath(path, strokePaint)
                drawHead(canvas, cx + dir * s * 0.4f, cy - s * 0.15f, if (dir < 0) Math.PI else 0.0, s * 0.36f)
            }
            maneuver in listOf(NavState.M_SLIGHT_LEFT, NavState.M_SLIGHT_RIGHT, NavState.M_KEEP_LEFT, NavState.M_KEEP_RIGHT) -> {
                val dir = if (maneuver == NavState.M_SLIGHT_LEFT || maneuver == NavState.M_KEEP_LEFT) -1f else 1f
                path.reset()
                path.moveTo(cx - dir * s * 0.15f, cy + s * 0.65f)
                path.lineTo(cx - dir * s * 0.15f, cy + s * 0.1f)
                path.lineTo(cx + dir * s * 0.42f, cy - s * 0.48f)
                canvas.drawPath(path, strokePaint)
                val ang = atan2((-s * 0.58f).toDouble(), (dir * s * 0.57f).toDouble())
                drawHead(canvas, cx + dir * s * 0.42f, cy - s * 0.48f, ang, s * 0.36f)
            }
            maneuver in listOf(NavState.M_SHARP_LEFT, NavState.M_SHARP_RIGHT) -> {
                val dir = if (maneuver == NavState.M_SHARP_LEFT) -1f else 1f
                path.reset()
                path.moveTo(cx - dir * s * 0.35f, cy + s * 0.65f)
                path.lineTo(cx - dir * s * 0.35f, cy - s * 0.45f)
                path.lineTo(cx + dir * s * 0.42f, cy + s * 0.32f)
                canvas.drawPath(path, strokePaint)
                val ang = atan2((s * 0.77f).toDouble(), (dir * s * 0.77f).toDouble())
                drawHead(canvas, cx + dir * s * 0.42f, cy + s * 0.32f, ang, s * 0.36f)
            }
            maneuver == NavState.M_DEPART -> {
                canvas.drawCircle(cx, cy + s * 0.55f, s * 0.2f, fillPaint)
                drawArrow(canvas, cx, cy + s * 0.22f, cx, cy - s * 0.6f, s * 0.38f)
            }
            else -> {
                drawArrow(canvas, cx, cy + s * 0.65f, cx, cy - s * 0.55f, s * 0.4f)
            }
        }
    }

    private fun drawArrow(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, head: Float) {
        c.drawLine(x1, y1, x2, y2, strokePaint)
        drawHead(c, x2, y2, atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()), head)
    }

    private fun drawHead(c: Canvas, bx: Float, by: Float, ang: Double, size: Float) {
        val dx = cos(ang).toFloat()
        val dy = sin(ang).toFloat()
        val px = -dy
        val py = dx
        path.reset()
        path.moveTo(bx + dx * size, by + dy * size)
        path.lineTo(bx + px * size * 0.72f - dx * size * 0.15f, by + py * size * 0.72f - dy * size * 0.15f)
        path.lineTo(bx - px * size * 0.72f - dx * size * 0.15f, by - py * size * 0.72f - dy * size * 0.15f)
        path.close()
        c.drawPath(path, fillPaint)
    }
}
