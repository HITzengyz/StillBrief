package com.focusinbox.app

import kotlin.math.abs
import kotlin.math.roundToInt

/** A bounded 300-degree knob. Continuous angles prevent wrapping from 180 back to 1. */
object DurationGeometry {
    const val MIN=1
    const val MAX=180
    const val START=120f
    const val SWEEP=300f
    fun angle(minutes: Int)=START+(minutes.coerceIn(MIN,MAX)-MIN).toFloat()/(MAX-MIN)*SWEEP
    fun snap(raw: Float): Int {
        val clamped=raw.coerceIn(MIN.toFloat(),MAX.toFloat())
        val tick=((clamped/5).roundToInt()*5).coerceIn(5,MAX)
        return if(abs(clamped-MIN)<=abs(clamped-tick)) MIN else tick
    }
    fun nextTick(minutes: Int,direction: Int): Int = if(direction>0) ((minutes/5+1)*5).coerceAtMost(MAX)
        else if(minutes<=5) MIN else (((minutes-1)/5)*5).coerceAtLeast(MIN)
    fun remainingMinutes(millis: Long): Int = ((millis.coerceAtLeast(0)+59999)/60000).coerceIn(MIN.toLong(),MAX.toLong()).toInt()
    private fun offset(angle: Float)=((angle-START)%360+360)%360
    class Drag(angle: Float,previousMinutes: Int) {
        private var last=angle
        private var continuous=offset(angle).let { value ->
            if(value<=SWEEP) value else {
                val endDistance=value-SWEEP; val startDistance=360-value
                if(endDistance<startDistance || endDistance==startDistance && previousMinutes>90) value else value-360
            }
        }
        fun value()=snap(MIN+continuous.coerceIn(0f,SWEEP)/SWEEP*(MAX-MIN))
        fun move(angle: Float): Int {
            val delta=((angle-last+540)%360)-180
            continuous+=delta; last=angle
            return value()
        }
    }
}
