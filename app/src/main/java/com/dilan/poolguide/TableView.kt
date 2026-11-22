package com.dillan.poolguide

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class TableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Área jugable de la mesa (ajustable con botones +/- y flechas)
    var tableRect = RectF(220f, 140f, 1700f, 940f)
        set(value) {
            field = value
            updatePockets()
            clampCueBall()
            invalidate()
        }

    // Posición de la bola blanca y del punto de mira/objetivo
    var cueBall = PointF(680f, 540f)
    var aimPoint = PointF(1220f, 540f)

    // Opciones configurables desde la barra flotante
    var maxBounces = 2          // 0 = sin rebotes, 1 = 1 banda, 2 = 2 bandas
    var pocketRadius = 56f      // Radio de detección de tronera
    var showGhostBall = true

    private var pockets: List<PointF> = emptyList()
    private var activeTarget: TargetMode = TargetMode.AIM

    private enum class TargetMode { NONE, CUE_BALL, AIM }

    // --- Estilos estilo neón ---
    private val tableBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 0, 255, 163)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val neonGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(135, 0, 240, 255)
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }

    private val neonCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.2f
        strokeCap = Paint.Cap.ROUND
    }

    private val bounceGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 200, 0)
        style = Paint.Style.STROKE
        strokeWidth = 11f
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(22f, 14f), 0f)
        maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
    }

    private val bounceCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 235, 120)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(22f, 14f), 0f)
    }

    private val cueBallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val pocketNormalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(130, 255, 82, 82)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val pocketConfirmedGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 0, 255, 120)
        style = Paint.Style.STROKE
        strokeWidth = 16f
        maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
    }

    private val pocketConfirmedCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 110, 255, 170)
        style = Paint.Style.FILL
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // Necesario para BlurMaskFilter en trazos
        updatePockets()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && oldw == 0 && oldh == 0) {
            val padX = w * 0.12f
            val padY = h * 0.14f
            tableRect = RectF(padX, padY, w - padX, h - padY)
            cueBall = PointF(tableRect.left + tableRect.width() * 0.28f, tableRect.centerY())
            aimPoint = PointF(tableRect.left + tableRect.width() * 0.65f, tableRect.centerY())
            updatePockets()
        }
    }

    private fun updatePockets() {
        val midX = tableRect.centerX()
        pockets = listOf(
            PointF(tableRect.left, tableRect.top),          // Esquina superior izquierda
            PointF(midX, tableRect.top),                    // Tronera central superior
            PointF(tableRect.right, tableRect.top),         // Esquina superior derecha
            PointF(tableRect.left, tableRect.bottom),       // Esquina inferior izquierda
            PointF(midX, tableRect.bottom),                 // Tronera central inferior
            PointF(tableRect.right, tableRect.bottom)       // Esquina inferior derecha
        )
    }

    private fun clampCueBall() {
        cueBall.x = cueBall.x.coerceIn(tableRect.left, tableRect.right)
        cueBall.y = cueBall.y.coerceIn(tableRect.top, tableRect.bottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Borde de referencia de la mesa
        canvas.drawRect(tableRect, tableBorderPaint)

        // 2. Calcular segmentos de trayectoria con rebotes en bandas
        val segments = computeTrajectorySegments()
        val confirmedPocket = findConfirmedPocket(segments)

        // 3. Dibujar troneras (verde brillante si la trayectoria apunta dentro del radio)
        for (pocket in pockets) {
            val isConfirmed = confirmedPocket != null &&
                hypot(pocket.x - confirmedPocket.x, pocket.y - confirmedPocket.y) < 1f
            if (isConfirmed) {
                canvas.drawCircle(pocket.x, pocket.y, pocketRadius * 1.15f, pocketConfirmedGlowPaint)
                canvas.drawCircle(pocket.x, pocket.y, pocketRadius * 0.38f, pocketConfirmedCorePaint)
            } else {
                canvas.drawCircle(pocket.x, pocket.y, pocketRadius * 0.75f, pocketNormalPaint)
            }
        }

        // 4. Dibujar líneas estilo neón (línea directa en cian/blanco, rebotes en ámbar punteado)
        segments.forEachIndexed { index, seg ->
            if (index == 0) {
                canvas.drawLine(seg.start.x, seg.start.y, seg.end.x, seg.end.y, neonGlowPaint)
                canvas.drawLine(seg.start.x, seg.start.y, seg.end.x, seg.end.y, neonCorePaint)
            } else {
                canvas.drawLine(seg.start.x, seg.start.y, seg.end.x, seg.end.y, bounceGlowPaint)
                canvas.drawLine(seg.start.x, seg.start.y, seg.end.x, seg.end.y, bounceCorePaint)
            }
        }

        // 5. Bola blanca y punto de mira / bola fantasma
        canvas.drawCircle(cueBall.x, cueBall.y, 22f, cueBallPaint)
        if (showGhostBall) {
            canvas.drawCircle(aimPoint.x, aimPoint.y, 20f, cueBallPaint)
        }
    }

    private data class Segment(val start: PointF, val end: PointF)

    private fun computeTrajectorySegments(): List<Segment> {
        val result = mutableListOf<Segment>()
        var dx = aimPoint.x - cueBall.x
        var dy = aimPoint.y - cueBall.y
        val len = hypot(dx, dy)
        if (len < 1f) return result

        var vx = dx / len
        var vy = dy / len
        var current = PointF(cueBall.x, cueBall.y)

        // Primer tramo hasta el punto de mira (o hasta tocar banda si aimPoint está más lejos)
        val firstHit = intersectTable(current, vx, vy)
        val distToAim = hypot(aimPoint.x - current.x, aimPoint.y - current.y)
        val distToWall = firstHit?.let { hypot(it.point.x - current.x, it.point.y - current.y) } ?: Float.MAX_VALUE

        if (distToWall < distToAim && firstHit != null) {
            result.add(Segment(current, firstHit.point))
            current = firstHit.point
            if (firstHit.hitHorizontal) vx = -vx
            if (firstHit.hitVertical) vy = -vy
        } else {
            result.add(Segment(current, PointF(aimPoint.x, aimPoint.y)))
            current = PointF(aimPoint.x, aimPoint.y)
        }

        // Rebotes adicionales en bandas (bank shots)
        for (b in 0 until maxBounces) {
            val hit = intersectTable(current, vx, vy) ?: break
            result.add(Segment(current, hit.point))
            current = hit.point
            if (hit.hitHorizontal) vx = -vx
            if (hit.hitVertical) vy = -vy
        }

        return result
    }

    private data class WallHit(
        val point: PointF,
        val hitHorizontal: Boolean,
        val hitVertical: Boolean
    )

    private fun intersectTable(origin: PointF, vx: Float, vy: Float): WallHit? {
        var bestT = Float.MAX_VALUE
        var hitH = false
        var hitV = false

        if (vx < -1e-4f) {
            val t = (tableRect.left - origin.x) / vx
            if (t > 0.5f && t < bestT) {
                val y = origin.y + vy * t
                if (y >= tableRect.top - 1f && y <= tableRect.bottom + 1f) {
                    bestT = t; hitH = true; hitV = false
                }
            }
        } else if (vx > 1e-4f) {
            val t = (tableRect.right - origin.x) / vx
            if (t > 0.5f && t < bestT) {
                val y = origin.y + vy * t
                if (y >= tableRect.top - 1f && y <= tableRect.bottom + 1f) {
                    bestT = t; hitH = true; hitV = false
                }
            }
        }

        if (vy < -1e-4f) {
            val t = (tableRect.top - origin.y) / vy
            if (t > 0.5f && t < bestT) {
                val x = origin.x + vx * t
                if (x >= tableRect.left - 1f && x <= tableRect.right + 1f) {
                    bestT = t; hitH = false; hitV = true
                }
            }
        } else if (vy > 1e-4f) {
            val t = (tableRect.bottom - origin.y) / vy
            if (t > 0.5f && t < bestT) {
                val x = origin.x + vx * t
                if (x >= tableRect.left - 1f && x <= tableRect.right + 1f) {
                    bestT = t; hitH = false; hitV = true
                }
            }
        }

        if (bestT == Float.MAX_VALUE) return null
        return WallHit(
            point = PointF(origin.x + vx * bestT, origin.y + vy * bestT),
            hitHorizontal = hitH,
            hitVertical = hitV
        )
    }

    private fun findConfirmedPocket(segments: List<Segment>): PointF? {
        for (pocket in pockets) {
            for (seg in segments) {
                if (distanceToSegment(pocket, seg.start, seg.end) <= pocketRadius) {
                    return pocket
                }
            }
        }
        return null
    }

    private fun distanceToSegment(p: PointF, a: PointF, b: PointF): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lenSq = abx * abx + aby * aby
        if (lenSq < 1e-4f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq).coerceIn(0f, 1f)
        val projX = a.x + t * abx
        val projY = a.y + t * aby
        return hypot(p.x - projX, p.y - projY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val dCue = hypot(event.x - cueBall.x, event.y - cueBall.y)
                val dAim = hypot(event.x - aimPoint.x, event.y - aimPoint.y)
                activeTarget = when {
                    dCue <= 85f && dCue <= dAim -> TargetMode.CUE_BALL
                    dAim <= 110f -> TargetMode.AIM
                    else -> TargetMode.AIM
                }
                moveTarget(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                moveTarget(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeTarget = TargetMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun moveTarget(x: Float, y: Float) {
        when (activeTarget) {
            TargetMode.CUE_BALL -> {
                cueBall.x = x.coerceIn(tableRect.left, tableRect.right)
                cueBall.y = y.coerceIn(tableRect.top, tableRect.bottom)
            }
            TargetMode.AIM -> {
                aimPoint.x = x
                aimPoint.y = y
            }
            TargetMode.NONE -> Unit
        }
        invalidate()
    }
}
