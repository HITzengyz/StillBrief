package com.focusinbox.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The remaining part of the round. The accessible timer and edit action live above this view. */
class FocusProgressView(context: Context) : View(context) {
    var progress: Float = 1f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    private val palette = Palette(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = RectF()
    private val density = resources.displayMetrics.density

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - 12f * density
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 4f * density
        paint.color = palette.track
        canvas.drawCircle(cx, cy, radius, paint)
        if (progress > 0f) {
            paint.color = palette.accent
            canvas.drawArc(arc, -90f, 360f * progress, false, paint)
            val angle = Math.toRadians((-90f + 360f * progress).toDouble())
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx + cos(angle).toFloat() * radius, cy + sin(angle).toFloat() * radius, 4f * density, paint)
        }
    }
}
