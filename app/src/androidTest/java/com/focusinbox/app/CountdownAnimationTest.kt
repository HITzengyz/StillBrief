package com.focusinbox.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.animation.ValueAnimator
import android.provider.Settings.Global
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CountdownAnimationTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=ins.targetContext
    private fun main(block: ()->Unit)=ins.runOnMainSync(block)
    private fun launch()=ins.startActivitySync(Intent(c,MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    private fun await(condition: ()->Boolean) {
        val end=SystemClock.uptimeMillis()+3000
        var done=false
        while(!done && SystemClock.uptimeMillis()<end) { main { done=condition() }; if(!done) SystemClock.sleep(16) }
        assertTrue("View did not reach expected state",done)
    }
    // A separate view in a real hardware window lets us exercise exact digit changes without the app ticker competing.
    private fun clock(a: MainActivity): CountdownTextView {
        lateinit var clock: CountdownTextView
        main {
            clock=CountdownTextView(a).apply { textSize=60f; gravity=Gravity.CENTER; UiStyle.text(this,600,numbers=true); setCountdown("35:00") }
            a.setContentView(FrameLayout(a).apply { addView(clock,FrameLayout.LayoutParams(-1,240,Gravity.CENTER)) })
        }
        await { clock.isLaidOut && clock.hasWindowFocus() }
        return clock
    }

    @Test fun currentTextUpdatesImmediatelyAndRapidChangesSettleToLatest() {
        val a=launch()
        try {
            val clock=clock(a)
            main {
                assertFalse(clock.isTransitionRunning)
                clock.setCountdown("35:00"); assertFalse(clock.isTransitionRunning)
                clock.setCountdown("34:59"); assertEquals("34:59",clock.text.toString()); assertTrue(clock.isTransitionRunning)
                clock.setCountdown("34:58"); assertFalse(clock.isTransitionRunning)
            }
            await { !clock.isTransitionRunning }
            main { assertEquals("34:58",clock.text.toString()); assertEquals(1f,clock.alpha,0f) }
            // Resizing and large jumps snap to the current value instead of rolling the whole timer.
            main { clock.setCountdown("100:00",false); clock.setCountdown("99:59"); assertFalse(clock.isTransitionRunning); clock.setCountdown("20:00"); assertFalse(clock.isTransitionRunning) }
            await { !clock.isTransitionRunning }
            main { assertEquals("20:00",clock.text.toString()) }
        } finally { main { a.finish() } }
    }

    @Test fun rollingDoesNotBlankTheChangingDigitOrMoveItsNeighbors() {
        val a=launch()
        try {
            val clock=clock(a)
            main { clock.setTextColor(Color.BLACK); clock.setCountdown("34:59",false) }
            ins.waitForIdleSync()
            lateinit var before: Bitmap
            lateinit var animation: ValueAnimator
            var boundary=0
            main {
                before=Bitmap.createBitmap(clock.width,clock.height,Bitmap.Config.ARGB_8888).also { clock.draw(Canvas(it)) }
                boundary=(clock.compoundPaddingLeft+clock.layout.getPrimaryHorizontal(4)).toInt()
                clock.setCountdown("34:58")
                animation=CountdownTextView::class.java.getDeclaredField("animator").apply { isAccessible=true }.get(clock) as ValueAnimator
                animation.pause()
            }
            ins.waitForIdleSync()
            main {
                fun ink(bitmap: Bitmap): Int {
                    var count=0
                    for(y in 0 until bitmap.height) for(x in boundary until bitmap.width) if(Color.alpha(bitmap.getPixel(x,y))>=192) count++
                    return count
                }
                val reference=ink(before)
                assertTrue(reference>0)
                for(phase in listOf(.25f,.4f,.5f,.75f)) {
                    animation.setCurrentFraction(phase)
                    val frame=Bitmap.createBitmap(clock.width,clock.height,Bitmap.Config.ARGB_8888)
                    try {
                        clock.draw(Canvas(frame))
                        assertTrue("Changing digit must stay visible at $phase",ink(frame)>reference/4)
                        for(y in 0 until frame.height) for(x in 0 until boundary-2) {
                            assertEquals("Unchanged digit moved at $phase ($x,$y)",before.getPixel(x,y),frame.getPixel(x,y))
                        }
                    } finally { frame.recycle() }
                }
                animation.end(); before.recycle()
            }
        } finally { main { a.finish() } }
    }

    @Test fun lossOfFocusDetachAndExplicitSnapCancelAnimation() {
        val a=launch()
        try {
            val clock=clock(a)
            main {
                clock.setCountdown("34:59"); clock.onWindowFocusChanged(false)
                assertFalse(clock.isTransitionRunning); assertEquals("34:59",clock.text.toString())
                clock.onWindowFocusChanged(true)
                clock.setCountdown("34:58"); assertTrue(clock.isTransitionRunning)
                clock.setCountdown("34:58",false); assertFalse(clock.isTransitionRunning)
                clock.setCountdown("34:57"); assertTrue(clock.isTransitionRunning)
                a.setContentView(FrameLayout(a)); assertFalse(clock.isTransitionRunning)
                clock.setCountdown("34:56"); assertFalse(clock.isTransitionRunning)
            }
        } finally { main { a.finish() } }
    }

    @Test fun resumingFocusShowsDeadlineImmediatelyWithoutReplayingOldDigits() {
        val settings=Settings(c); val enabled=settings.enabled; val minutes=settings.minutes
        settings.enabled=false; settings.minutes=35; TimerEngine.rest(c); TimerEngine.start(c)
        val a=launch()
        try {
            await { a.findViewById<View>(R.id.focus_clock).hasWindowFocus() }
            main {
                val clock=a.findViewById<CountdownTextView>(R.id.focus_clock)
                ins.callActivityOnPause(a)
                assertFalse(clock.isTransitionRunning)
                TimerEngine.adjustRemaining(c,20)
                ins.callActivityOnResume(a)
                assertTrue(clock.text.toString().startsWith("20:"))
                assertFalse(clock.isTransitionRunning)
            }
        } finally { main { a.finish() }; TimerEngine.rest(c); settings.enabled=enabled; settings.minutes=minutes }
    }

    @Test fun disablingSystemAnimationsUpdatesWithoutTransition() {
        val a=launch()
        val old=Global.getFloat(c.contentResolver,Global.ANIMATOR_DURATION_SCALE,1f)
        ins.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        try {
            val clock=clock(a)
            Global.putFloat(c.contentResolver,Global.ANIMATOR_DURATION_SCALE,0f)
            await { !ValueAnimator.areAnimatorsEnabled() }
            main {
                clock.setCountdown("34:59")
                assertEquals("34:59",clock.text.toString()); assertFalse(clock.isTransitionRunning)
            }
        } finally {
            Global.putFloat(c.contentResolver,Global.ANIMATOR_DURATION_SCALE,old)
            await { ValueAnimator.areAnimatorsEnabled()==(old!=0f) }
            ins.uiAutomation.dropShellPermissionIdentity()
            main { a.finish() }
        }
    }

    // Opt-in fixture for recording the real focus screen; never calls an AI provider.
    @Test fun captureFocusAnimation() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("captureCountdown")=="true")
        val settings=Settings(c); val old=settings.appearance; val minutes=settings.minutes; val enabled=settings.enabled
        settings.appearance=if(args.getString("appearance")=="dark") AppearanceMode.DARK else AppearanceMode.LIGHT
        settings.minutes=35; settings.enabled=false; TimerEngine.rest(c); TimerEngine.start(c)
        val a=launch()
        try { SystemClock.sleep(1000); TimerEngine.adjustRemaining(c,35); SystemClock.sleep(8000) }
        finally { main { a.finish() }; TimerEngine.rest(c); settings.appearance=old; settings.minutes=minutes; settings.enabled=enabled }
    }
}
