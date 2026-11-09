package com.focusinbox.app

import android.animation.ValueAnimator
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

object UiMotion {
    val ease=PathInterpolator(0.215f,0.61f,0.355f,1f)
    fun changeLayout(parent: ViewGroup,change: ()->Unit) {
        TransitionManager.endTransitions(parent)
        if(parent.isLaidOut && ValueAnimator.areAnimatorsEnabled()) {
            TransitionManager.beginDelayedTransition(parent,AutoTransition().apply { duration=180; interpolator=ease })
        }
        change()
    }
    fun settle(parent: ViewGroup) { TransitionManager.endTransitions(parent) }
    fun confirm(view: View) {
        view.animate().cancel(); view.animate().withEndAction(null); view.alpha=1f; view.scaleX=1f; view.scaleY=1f
        if(!ValueAnimator.areAnimatorsEnabled()) return
        view.scaleX=0.88f; view.scaleY=0.88f
        // Unit-mass spring: stiffness 380, damping 18. A small scale change keeps the overshoot subtle.
        val omega=sqrt(380.0-9.0*9.0)
        view.animate().scaleX(1f).scaleY(1f).setDuration(400).setInterpolator { fraction ->
            if(fraction>=1f) 1f else {
                val t=fraction*0.4
                (1-exp(-9*t)*(cos(omega*t)+9/omega*sin(omega*t))).toFloat()
            }
        }.start()
    }
}
