package com.dilan.poolguide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Mesa de billar virtual (proporción 2:1). Centro (cx, cy), ancho tw, rotación angle. */
class TableView(ctx: Context) : View(ctx) {

    var cx = 0f
    var cy = 0f
    var tw = 0f
    var angle = 0f
    var tableAlpha = 0.75f
    var editMode = true

    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val th: Float get() = tw / 2f

    // gestos
    private var dragging = false
    private var multi = false
    private var lastX = 0f
    private var lastY = 0f
    private var startDist = 1f
    private var startAng = 0f
    private var baseW = 0f
    private var baseAngle = 0f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (tw == 0f) {
            cx = w / 2f
            cy = h / 2f
            tw = min(w * 0.7f, h * 1.4f)
        }
    }

    private fun col(r: Int, g: Int, b: Int, a: Int) =
        Color.argb((a * tableAlpha).toInt().coerceIn(0, 255), r, g, b)

    private fun fill(color: Int) { p.style = Paint.Style.FILL; p.color = color }
    private fun stroke(color: Int, wd: Float = 2f) {
        p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = wd * d
    }

    override fun onDraw(c: Canvas) {
        if (tw <= 0f) return

        c.save()
        c.translate(cx, cy)
        c.rotate(angle)

        val w = tw
        val h = th
        val rail = w * 0.05f
        val outer = RectF(-w / 2, -h / 2, w / 2, h / 2)
        val inner = RectF(outer.left + rail, outer.top + rail, outer.right - rail, outer.bottom - rail)

        // cojines
        fill(col(255, 200, 0, 25)); c.drawRoundRect(outer, 10 * d, 10 * d, p)
        stroke(col(255, 200, 0, 255)); c.drawRoundRect(outer, 10 * d, 10 * d, p)
        // paño
        fill(col(0, 255, 120, 20)); c.drawRect(inner, p)
        stroke(col(0, 255, 120, 255)); c.drawRect(inner, p)

        // troneras
        val pr = rail * 0.8f
        stroke(col(255, 60, 60, 255))
        val pockets = listOf(
            inner.left to inner.top, inner.right to inner.top,
            inner.left to inner.bottom, inner.right to inner.bottom,
            0f to (inner.top - rail * 0.2f), 0f to (inner.bottom + rail * 0.2f)
        )
        for ((x, y) in pockets) c.drawCircle(x, y, pr, p)

        // línea de cabecera
        val hx = inner.left + inner.width() * 0.25f
        stroke(col(255, 255, 255, 200), 1f)
        p.pathEffect = DashPathEffect(floatArrayOf(10 * d, 8 * d), 0f)
        c.drawLine(hx, inner.top, hx, inner.bottom, p)
        p.pathEffect = null

        // puntos
        fill(col(255, 255, 255, 220))
        for (x in listOf(hx, 0f, inner.left + inner.width() * 0.75f)) c.drawCircle(x, 0f, 3 * d, p)

        // diamantes
        for (i in 1..7) {
            if (i == 4) continue
            val x = inner.left + inner.width() * i / 8f
            c.drawCircle(x, outer.top + rail / 2, 2 * d, p)
            c.drawCircle(x, outer.bottom - rail / 2, 2 * d, p)
        }
        for (i in 1..3) {
            val y = inner.top + inner.height() * i / 4f
            c.drawCircle(outer.left + rail / 2, y, 2 * d, p)
            c.drawCircle(outer.right - rail / 2, y, 2 * d, p)
        }

        c.restore()

        if (editMode) {
            p.style = Paint.Style.FILL
            p.color = Color.WHITE
            p.textSize = 13 * d
            p.textAlign = Paint.Align.CENTER
            p.setShadowLayer(4f, 0f, 0f, Color.BLACK)
            c.drawText("EDITAR: 1 dedo = mover · 2 dedos = tamaño y giro", width / 2f, height - 16 * d, p)
            p.clearShadowLayer()
            p.textAlign = Paint.Align.LEFT
        }
    }

    private fun hit(x: Float, y: Float): Boolean {
        val a = Math.toRadians(-angle.toDouble())
        val dx = x - cx
        val dy = y - cy
        val lx = dx * cos(a) - dy * sin(a)
        val ly = dx * sin(a) + dy * cos(a)
        val m = 40 * d
        return abs(lx) <= tw / 2 + m && abs(ly) <= th / 2 + m
    }

    private fun dist(e: MotionEvent) = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
    private fun ang(e: MotionEvent) =
        Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editMode) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!hit(e.x, e.y)) return false
                dragging = true
                lastX = e.x
                lastY = e.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.pointerCount == 2) {
                    multi = true
                    dragging = false
                    startDist = dist(e).coerceAtLeast(1f)
                    startAng = ang(e)
                    baseW = tw
                    baseAngle = angle
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (multi && e.pointerCount >= 2) {
                    tw = (baseW * dist(e) / startDist).coerceIn(150 * d, width * 3f)
                    angle = baseAngle + (ang(e) - startAng)
                    invalidate()
                } else if (dragging && e.pointerCount == 1) {
                    cx += e.x - lastX
                    cy += e.y - lastY
                    lastX = e.x
                    lastY = e.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                multi = false
                val idx = if (e.actionIndex == 0) 1 else 0
                if (idx < e.pointerCount) {
                    lastX = e.getX(idx)
                    lastY = e.getY(idx)
                    dragging = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                multi = false
            }
        }
        return true
    }
}
