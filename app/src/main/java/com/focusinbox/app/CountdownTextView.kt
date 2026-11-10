package com.focusinbox.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.TextView

/** Text is always current. Only consecutive one-second ticks roll, with no opacity or blur changes. */
class CountdownTextView @JvmOverloads constructor(context: Context,attrs: AttributeSet?=null) : TextView(context,attrs) {
    private var previous: String?=null
    private var animator: ValueAnimator?=null
    private var fraction=1f
    private val glyphPaint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val digitBounds=Rect()
    internal val isTransitionRunning get()=animator!=null

    fun setCountdown(value: String,animate: Boolean=true) {
        if(text.toString()==value) {
            if(!animate || !ValueAnimator.areAnimatorsEnabled()) settle()
            return
        }
        val old=text.toString()
        val interrupted=animator!=null
        settle()
        text=value
        // Adjustments, missed ticks and a change in digit count should show the correct time immediately.
        if(!animate || interrupted || old.length!=value.length || !isNextTick(old,value) ||
            !isLaidOut || !isShown || !hasWindowFocus() || !ValueAnimator.areAnimatorsEnabled()) return
        previous=old
        fraction=0f
        animator=ValueAnimator.ofFloat(0f,1f).apply {
            duration=160
            interpolator=UiMotion.ease
            addUpdateListener { fraction=it.animatedValue as Float; invalidate() }
            addListener(object: AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if(animator===animation) { animator=null; clearTransition() }
                }
            })
        }
        animator?.start()
    }

    private fun isNextTick(old: String,value: String): Boolean {
        fun seconds(text: String): Int? {
            val parts=text.split(':')
            if(parts.size!=2) return null
            val minutes=parts[0].toIntOrNull() ?: return null
            val seconds=parts[1].toIntOrNull() ?: return null
            return minutes*60+seconds
        }
        val before=seconds(old) ?: return false
        val after=seconds(value) ?: return false
        return before-after==1
    }

    fun settle() {
        val running=animator
        animator=null
        running?.cancel()
        clearTransition()
    }

    private fun clearTransition() { previous=null; fraction=1f; invalidate() }

    override fun onDraw(canvas: Canvas) {
        if(previous!=null && !ValueAnimator.areAnimatorsEnabled()) settle()
        val old=previous
        val current=text.toString()
        val textLayout=layout
        if(old==null || textLayout==null || textLayout.lineCount!=1) { super.onDraw(canvas); return }
        glyphPaint.set(paint); glyphPaint.color=currentTextColor
        val left=compoundPaddingLeft.toFloat()
        val y=baseline.toFloat()
        val save=canvas.save()
        for(i in current.indices) if(old[i]!=current[i]) {
            canvas.clipOutRect(left+textLayout.getPrimaryHorizontal(i),0f,left+textLayout.getPrimaryHorizontal(i+1),height.toFloat())
        }
        super.onDraw(canvas)
        canvas.restoreToCount(save)

        // A tight digit window gives the incoming and outgoing glyphs a continuous, opaque handover.
        // Include localized digits in the measured bounds, with a small antialiasing margin.
        glyphPaint.getTextBounds(old+current,0,old.length+current.length,digitBounds)
        val margin=resources.displayMetrics.density
        val top=y+digitBounds.top-margin
        val bottom=y+digitBounds.bottom+margin
        val travel=bottom-top
        for(i in current.indices) if(old[i]!=current[i]) {
            val x=left+textLayout.getPrimaryHorizontal(i)
            val end=left+textLayout.getPrimaryHorizontal(i+1)
            val clip=canvas.save()
            canvas.clipRect(x,top,end,bottom)
            canvas.drawText(old,i,i+1,x,y+travel*fraction,glyphPaint)
            canvas.drawText(current,i,i+1,x,y-travel*(1f-fraction),glyphPaint)
            canvas.restoreToCount(clip)
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if(!hasWindowFocus) settle() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if(visibility!=View.VISIBLE) settle() }
    override fun onDetachedFromWindow() { settle(); super.onDetachedFromWindow() }
}
