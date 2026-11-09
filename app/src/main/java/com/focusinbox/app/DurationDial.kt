package com.focusinbox.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class DurationDial(baseContext: Context): View(Appearance.wrap(baseContext)) {
    var minutes=25
        private set
    var onValueChanged: ((Int)->Unit)?=null
    var onExactInput: (() ->Unit)?=null
    private val palette=Appearance.palette(context)
    private val green=palette.accent
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc=RectF()
    private val scalePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { fontFeatureSettings="'tnum'"; textAlign=Paint.Align.CENTER; typeface=UiStyle.face(context) }
    private fun scaleTextSize()=sp(11f)
    // A circle enclosing the largest label keeps every angle clear of the outer ticks.
    private val scaleLabelExtent: Float get() {
        scalePaint.textSize=scaleTextSize()
        val metrics=scalePaint.fontMetrics
        return hypot(scalePaint.measureText("180")/2f,(metrics.descent-metrics.ascent)/2f)
    }
    private val scaleLabelOffset: Float get()=dp(17f)+scaleLabelExtent+dp(5f)
    private val outerMargin: Float get()=scaleLabelOffset+scaleLabelExtent+dp(5f)
    private var drag: DurationGeometry.Drag?=null
    private var centerTap=false
    private var downX=0f
    private var downY=0f
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    private fun dp(value: Float)=value*resources.displayMetrics.density
    private fun sp(value: Float)=value*resources.displayMetrics.scaledDensity
    private val radius: Float get()=(min(width,height)/2f-outerMargin).coerceAtLeast(dp(20f))
    init { isFocusable=true; isClickable=true; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES }
    fun setMinutes(value: Int,haptic: Boolean=false) {
        val next=value.coerceIn(DurationGeometry.MIN,DurationGeometry.MAX)
        if(next==minutes) return
        minutes=next
        if(haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onValueChanged?.invoke(next); invalidate()
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
    }
    override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
        val w=resolveSize(dp(304f).toInt(),widthMeasureSpec)
        setMeasuredDimension(w,resolveSize(min(w,dp(304f).toInt()),heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx=width/2f; val cy=height/2f; val r=radius
        arc.set(cx-r,cy-r,cx+r,cy+r)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=dp(10f); paint.strokeCap=Paint.Cap.ROUND
        paint.color=palette.track; canvas.drawArc(arc,DurationGeometry.START,DurationGeometry.SWEEP,false,paint)
        paint.color=green; canvas.drawArc(arc,DurationGeometry.START,DurationGeometry.angle(minutes)-DurationGeometry.START,false,paint)
        paint.strokeWidth=dp(1f)
        for(value in 1..180) if(value==1 || value%5==0) {
            val angle=Math.toRadians(DurationGeometry.angle(value).toDouble())
            val major=value==1 || value%15==0
            val inner=r+dp(11f); val outer=inner+dp(if(major) 6f else 3f)
            paint.color=if(value<=minutes) palette.tickActive else palette.tick
            canvas.drawLine(cx+cos(angle).toFloat()*inner,cy+sin(angle).toFloat()*inner,cx+cos(angle).toFloat()*outer,cy+sin(angle).toFloat()*outer,paint)
        }
        scalePaint.textSize=scaleTextSize(); scalePaint.color=palette.secondary
        val metrics=scalePaint.fontMetrics
        for(value in listOf(30,60,90,120,150,180)) {
            val labelAngle=Math.toRadians(DurationGeometry.angle(value).toDouble())
            canvas.drawText(value.toString(),cx+cos(labelAngle).toFloat()*(r+scaleLabelOffset),
                cy+sin(labelAngle).toFloat()*(r+scaleLabelOffset)-(metrics.ascent+metrics.descent)/2f,scalePaint)
        }
        val angle=Math.toRadians(DurationGeometry.angle(minutes).toDouble())
        val x=cx+cos(angle).toFloat()*r; val y=cy+sin(angle).toFloat()*r
        paint.style=Paint.Style.FILL; paint.color=palette.surface; canvas.drawCircle(x,y,dp(10f),paint)
        paint.color=green; canvas.drawCircle(x,y,dp(6f),paint)
        paint.textAlign=Paint.Align.CENTER; paint.fontFeatureSettings="'tnum'"
        val entries=listOf(
            Triple(minutes.toString(),sp(62f),UiStyle.face(context,500)),
            Triple(context.getString(R.string.duration_minutes_unit),sp(15f),UiStyle.face(context)),
            Triple(context.getString(R.string.duration_exact_hint),sp(11f),UiStyle.face(context)))
        val sizes=entries.mapIndexed { index,(text,size,face) ->
            paint.typeface=face; paint.textSize=size
            min(size,size*(r*if(index==0) 1.35f else 1.65f)/paint.measureText(text).coerceAtLeast(1f))
        }
        val heights=entries.indices.map { index -> paint.typeface=entries[index].third; paint.textSize=sizes[index]; paint.fontMetrics.let { it.descent-it.ascent } }
        val gaps=dp(5f)+dp(9f)
        val scale=min(1f,(r*1.4f-gaps)/heights.sum())
        var top=cy-(heights.sum()*scale+gaps)/2
        entries.forEachIndexed { index,(text,_,face) ->
            paint.typeface=face; paint.textSize=sizes[index]*scale
            paint.color=when(index) { 0 -> palette.text; 1 -> palette.secondary; else -> palette.secondary }
            val metrics=paint.fontMetrics
            canvas.drawText(text,cx,top-metrics.ascent,paint)
            top+=metrics.descent-metrics.ascent+if(index==0) dp(5f) else dp(9f)
        }
    }
    private fun angle(x: Float,y: Float)=Math.toDegrees(atan2((y-height/2f).toDouble(),(x-width/2f).toDouble())).toFloat().let { (it+360)%360 }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(!isEnabled) return false
        val distance=hypot(event.x-width/2f,event.y-height/2f)
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX=event.x; downY=event.y
                centerTap=distance<radius*0.62f
                if(!centerTap && (distance<radius-dp(34f) || distance>radius+outerMargin)) return false
                if(!centerTap) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    drag=DurationGeometry.Drag(angle(event.x,event.y),minutes)
                    setMinutes(drag!!.value(),true)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if(hypot(event.x-downX,event.y-downY)>slop) centerTap=false
                if(distance>dp(12f)) drag?.let { setMinutes(it.move(angle(event.x,event.y)),true) }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val click=centerTap && drag==null
                cancelGesture()
                if(click) performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { cancelGesture(); return true }
        }
        return super.onTouchEvent(event)
    }
    fun cancelGesture() { drag=null; centerTap=false; parent?.requestDisallowInterceptTouchEvent(false) }
    override fun performClick(): Boolean { super.performClick(); onExactInput?.invoke(); return true }
    override fun onDetachedFromWindow() { cancelGesture(); super.onDetachedFromWindow() }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className="android.widget.SeekBar"
        info.contentDescription=context.getString(R.string.duration_dial_description)
        info.text=context.getString(R.string.minutes_value,minutes)
        info.rangeInfo=AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,1f,180f,minutes.toFloat())
        info.isScrollable=true
        if(minutes<180) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        if(minutes>1) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
    }
    override fun performAccessibilityAction(action: Int,arguments: Bundle?): Boolean = when(action) {
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { setMinutes(DurationGeometry.nextTick(minutes,1)); true }
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { setMinutes(DurationGeometry.nextTick(minutes,-1)); true }
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
            val value=arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,Float.NaN) ?: Float.NaN
            if(value.isNaN() || value.isInfinite()) false else { setMinutes(kotlin.math.round(value).toInt()); true }
        }
        else -> super.performAccessibilityAction(action,arguments)
    }
}
