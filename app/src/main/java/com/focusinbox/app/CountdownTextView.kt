package com.focusinbox.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import kotlin.math.ceil

/** Text always holds the current time; only the presentation of changed digits is animated. */
class CountdownTextView @JvmOverloads constructor(context: Context,attrs: AttributeSet?=null) : TextView(context,attrs) {
    private var previous: String?=null
    private var animator: ValueAnimator?=null
    private var fraction=1f
    private val glyphPaint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var blur: GlyphBlur?=null
    internal val isTransitionRunning get()=animator!=null

    fun setCountdown(value: String,animate: Boolean=true) {
        if(text.toString()==value) {
            if(!animate || !ValueAnimator.areAnimatorsEnabled()) settle()
            return
        }
        val old=text.toString()
        settle()
        text=value
        if(!animate || old.isEmpty() || !isLaidOut || !isShown || !hasWindowFocus() || !ValueAnimator.areAnimatorsEnabled()) return
        previous=old
        fraction=0f
        animator=ValueAnimator.ofFloat(0f,1f).apply {
            duration=280
            interpolator=LinearInterpolator()
            addUpdateListener { fraction=it.animatedValue as Float; invalidate() }
            addListener(object: AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if(animator===animation) { animator=null; clearTransition() }
                }
            })
        }
        animator?.start()
    }

    fun settle() {
        val running=animator
        animator=null
        running?.cancel()
        clearTransition()
    }

    private fun clearTransition() {
        previous=null; fraction=1f
        if(Build.VERSION.SDK_INT>=31) blur?.release()
        blur=null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if(previous!=null && !ValueAnimator.areAnimatorsEnabled()) settle()
        val old=previous
        val current=text.toString()
        val textLayout=layout
        if(old==null || textLayout==null || textLayout.lineCount!=1) { super.onDraw(canvas); return }
        glyphPaint.set(paint); glyphPaint.color=currentTextColor
        val outgoing=fraction<0.4f
        val phase=UiMotion.ease.getInterpolation(if(outgoing) fraction/0.4f else (fraction-0.4f)/0.6f)
        val opacity=if(outgoing) 1f-phase else phase
        val radius=3f*resources.displayMetrics.density*(if(outgoing) phase else 1f-phase)
        val left=compoundPaddingLeft.toFloat()
        val y=baseline.toFloat()
        // A change in minute digit count fades the whole number around the same center.
        if(old.length!=current.length) {
            val value=if(outgoing) old else current
            val center=left+(textLayout.getLineLeft(0)+textLayout.getLineRight(0))/2f
            drawGlyph(canvas,value,0,value.length,center-glyphPaint.measureText(value)/2f,y,opacity,radius)
            return
        }
        val save=canvas.save()
        for(i in current.indices) if(old[i]!=current[i]) {
            canvas.clipOutRect(left+textLayout.getPrimaryHorizontal(i),0f,left+textLayout.getPrimaryHorizontal(i+1),height.toFloat())
        }
        super.onDraw(canvas)
        canvas.restoreToCount(save)
        val value=if(outgoing) old else current
        for(i in current.indices) if(old[i]!=current[i]) {
            drawGlyph(canvas,value,i,i+1,left+textLayout.getPrimaryHorizontal(i),y,opacity,radius)
        }
    }

    private fun drawGlyph(canvas: Canvas,value: String,start: Int,end: Int,x: Float,y: Float,opacity: Float,radius: Float) {
        if(Build.VERSION.SDK_INT>=31 && canvas.isHardwareAccelerated) {
            val renderer=blur ?: GlyphBlur().also { blur=it }
            renderer.draw(canvas,value,start,end,x,y,glyphPaint,opacity,radius,resources.displayMetrics.density*10f)
        } else {
            val alpha=glyphPaint.alpha
            glyphPaint.alpha=(alpha*opacity).toInt()
            canvas.drawText(value,start,end,x,y,glyphPaint)
            glyphPaint.alpha=alpha
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if(!hasWindowFocus) settle() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if(visibility!=View.VISIBLE) settle() }
    override fun onDetachedFromWindow() { settle(); super.onDetachedFromWindow() }

    @TargetApi(31)
    private class GlyphBlur {
        // Each changed digit needs its own display list within the same frame.
        private val nodes=mutableMapOf<Int,RenderNode>()
        fun draw(canvas: Canvas,value: String,start: Int,end: Int,x: Float,y: Float,paint: Paint,opacity: Float,radius: Float,pad: Float) {
            val node=nodes.getOrPut(start) { RenderNode("countdown-$start") }
            val width=ceil(paint.measureText(value,start,end)+2*pad).toInt()
            val height=ceil(paint.descent()-paint.ascent()+2*pad).toInt()
            node.setPosition(0,0,width,height)
            val recording=node.beginRecording()
            recording.drawText(value,start,end,pad,pad-paint.ascent(),paint)
            node.endRecording()
            node.alpha=opacity
            node.setRenderEffect(if(radius>0.01f) RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.DECAL) else null)
            val save=canvas.save()
            canvas.translate(x-pad,y+paint.ascent()-pad)
            canvas.drawRenderNode(node)
            canvas.restoreToCount(save)
        }
        fun release() { nodes.values.forEach { it.discardDisplayList() }; nodes.clear() }
    }
}
