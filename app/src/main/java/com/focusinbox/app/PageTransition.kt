package com.focusinbox.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout

/** Owns only presentation; callers construct and commit the next page synchronously. */
class PageTransition(private val host: FrameLayout,private val animationsEnabled: ()->Boolean = { ValueAnimator.areAnimatorsEnabled() }) {
    enum class Direction { NONE, FORWARD, BACK }
    private var current: PageLayer?=null
    private var animation: AnimatorSet?=null
    private var generation=0
    val isAnimating: Boolean get()=animation!=null

    fun replace(content: View,animate: Boolean,direction: Direction=Direction.NONE) {
        finish()
        val previous=current
        val next=PageLayer(host.context).apply { addView(content,FrameLayout.LayoutParams(-1,-1)) }
        current=next
        previous?.blockOutgoing()
        host.addView(next,FrameLayout.LayoutParams(-1,-1))
        if(previous==null || !animate || !animationsEnabled()) { finish(); return }

        val token=generation
        val shift=when(direction) { Direction.NONE -> 0f; Direction.FORWARD -> 8f; Direction.BACK -> -8f }*host.resources.displayMetrics.density
        next.alpha=0f; next.translationX=shift; next.interactive=false
        val out=AnimatorSet().apply {
            playTogether(ObjectAnimator.ofFloat(previous,View.ALPHA,1f,0f),ObjectAnimator.ofFloat(previous,View.TRANSLATION_X,0f,-shift/2))
            duration=80; interpolator=PathInterpolator(0.4f,0f,1f,1f)
            addListener(object: AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { if(token==generation) host.removeView(previous) }
            })
        }
        val incoming=AnimatorSet().apply {
            playTogether(ObjectAnimator.ofFloat(next,View.ALPHA,0f,1f),ObjectAnimator.ofFloat(next,View.TRANSLATION_X,shift,0f))
            duration=180; interpolator=UiMotion.ease
            addListener(object: AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) { if(token==generation) next.interactive=true }
            })
        }
        animation=AnimatorSet().apply {
            playSequentially(out,incoming)
            addListener(object: AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { if(token==generation) finish() }
            })
            start()
        }
    }
    /** Cancel stale callbacks and settle only the latest page, including when paused mid-transition. */
    fun finish() {
        generation++
        val running=animation; animation=null
        running?.removeAllListeners(); running?.cancel()
        for(index in host.childCount-1 downTo 0) if(host.getChildAt(index)!==current) host.removeViewAt(index)
        current?.apply { alpha=1f; translationX=0f; interactive=true }
    }
    fun clear() { finish(); host.removeAllViews(); current=null }

    private class PageLayer(context: Context): FrameLayout(context) {
        var interactive=true
            set(value) {
                field=value
                importantForAccessibility=if(value) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                descendantFocusability=if(value) FOCUS_AFTER_DESCENDANTS else FOCUS_BLOCK_DESCENDANTS
                if(!value) { clearFocus(); cancelPendingInputEvents() }
            }
        override fun dispatchTouchEvent(event: MotionEvent): Boolean = if(interactive) super.dispatchTouchEvent(event) else true
        fun blockOutgoing() {
            interactive=false
            fun disable(view: View) {
                view.isEnabled=false
                if(view is ViewGroup) for(index in 0 until view.childCount) disable(view.getChildAt(index))
            }
            disable(this)
        }
    }
}
