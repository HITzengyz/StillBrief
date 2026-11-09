package com.focusinbox.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** One optical size and stroke for navigation and small in-app actions. */
object UiSymbols {
    fun drawable(context: Context, name: String, color: Int, sizeDp: Int = 24): Drawable =
        Symbol(name, color, (sizeDp * context.resources.displayMetrics.density).toInt()).apply {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
        }

    private class Symbol(val name: String, color: Int, val size: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.7f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private fun Canvas.lines(vararg points: Float) {
            val path = Path().apply {
                moveTo(points[0], points[1]); for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
            }
            drawPath(path, paint)
        }
        override fun draw(canvas: Canvas) {
            canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
            with(canvas) {
                when (name) {
                    "focus" -> {
                        drawCircle(12f, 13.5f, 7.5f, paint); lines(9f, 2.5f, 15f, 2.5f)
                        lines(12f, 6f, 12f, 2.5f); lines(12f, 9.5f, 12f, 13.5f, 15f, 13.5f)
                    }
                    "notifications" -> {
                        drawPath(Path().apply {
                            moveTo(5f, 16.5f); lineTo(6.5f, 14f); lineTo(6.5f, 9f)
                            cubicTo(6.5f, 1.5f, 17.5f, 1.5f, 17.5f, 9f)
                            lineTo(17.5f, 14f); lineTo(19f, 16.5f); close()
                        }, paint)
                        drawArc(9.5f, 16.5f, 14.5f, 21.5f, 20f, 140f, false, paint)
                    }
                    "settings" -> {
                        lines(4f, 6f, 20f, 6f); lines(4f, 12f, 20f, 12f); lines(4f, 18f, 20f, 18f)
                        lines(9f, 3.5f, 9f, 8.5f); lines(15f, 9.5f, 15f, 14.5f); lines(9f, 15.5f, 9f, 20.5f)
                    }
                    "back" -> { lines(14f, 5f, 7f, 12f, 14f, 19f); lines(7f, 12f, 21f, 12f) }
                    "down" -> lines(6f, 9f, 12f, 15f, 18f, 9f)
                    "more" -> { paint.style=Paint.Style.FILL; for (y in listOf(5f,12f,19f)) drawCircle(12f,y,1.2f,paint); paint.style=Paint.Style.STROKE }
                    "chevron" -> lines(9f, 6f, 15f, 12f, 9f, 18f)
                    "arrow" -> { lines(5f, 12f, 19f, 12f); lines(13f, 6f, 19f, 12f, 13f, 18f) }
                    "edit" -> { lines(4f, 20f, 5f, 15f, 16f, 4f, 20f, 8f, 9f, 19f, 4f, 20f); lines(13.5f, 6.5f, 17.5f, 10.5f) }
                    "refresh" -> { drawArc(5f, 5f, 19f, 19f, 35f, 285f, false, paint); lines(14f, 3f, 18f, 7f, 13f, 8f) }
                    "check" -> lines(5f, 12f, 10f, 17f, 19f, 7f)
                    "close" -> { lines(6f, 6f, 18f, 18f); lines(6f, 18f, 18f, 6f) }
                }
            }
            canvas.restore()
        }
        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
        @Suppress("DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
