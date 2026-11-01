package com.focusinbox.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import kotlin.math.cos
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class DurationPickerTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private var sheet: DurationPicker?=null
    private var accessibilityFlags=0
    @Before fun launch() {
        activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val automation=instrumentation.uiAutomation
        val info=automation.serviceInfo; accessibilityFlags=info.flags
        info.flags=info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS; automation.serviceInfo=info
        instrumentation.waitForIdleSync()
    }
    @After fun close() {
        onMain { sheet?.dismiss(); if(!activity.isDestroyed) activity.finish() }
        val info=instrumentation.uiAutomation.serviceInfo; info.flags=accessibilityFlags; instrumentation.uiAutomation.serviceInfo=info
        instrumentation.waitForIdleSync()
    }
    @Suppress("UNCHECKED_CAST") private fun <T> onMain(action: ()->T): T {
        var value: T?=null; var error: Throwable?=null
        instrumentation.runOnMainSync { try { value=action() } catch(t: Throwable) { error=t } }
        error?.let { throw it }; return value as T
    }
    private fun activeNode(id: String): AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()
    private fun awaitNode(id: String): AccessibilityNodeInfo {
        val end=SystemClock.uptimeMillis()+3000
        while(SystemClock.uptimeMillis()<end) { activeNode(id)?.let { return it }; SystemClock.sleep(30) }
        throw AssertionError("Missing dialog control: $id")
    }
    @Test fun presetsAreDraftsAndOnlyExplicitConfirmationAppliesThem() {
        val selected=mutableListOf<Int>()
        val before=instrumentation.targetContext.getSharedPreferences("settings",0).all.toMap()
        onMain {
            val picker=DurationPicker(activity,25,"Duration") { selected.add(it) }.also { sheet=it; it.show() }
            picker.findViewById<ViewGroup>(R.id.duration_presets).findViewWithTag<Button>(60).performClick()
            assertEquals(60,picker.dial.minutes)
            assertTrue(picker.findViewById<ViewGroup>(R.id.duration_presets).findViewWithTag<Button>(60).isSelected)
            assertTrue(selected.isEmpty())
            picker.findViewById<Button>(R.id.duration_cancel).performClick(); assertTrue(selected.isEmpty())
            sheet=DurationPicker(activity,25,"Duration") { selected.add(it) }.also { it.show() }
            sheet!!.findViewById<ViewGroup>(R.id.duration_presets).findViewWithTag<Button>(45).performClick()
            sheet!!.findViewById<Button>(R.id.duration_confirm).performClick()
            assertEquals(listOf(45),selected); assertFalse(sheet!!.isShowing)
        }
        assertEquals(before,instrumentation.targetContext.getSharedPreferences("settings",0).all)
    }
    @Test fun accessibilityRangeAndExactNumericInputKeepOneMinutePrecision() {
        val selected=mutableListOf<Int>()
        onMain {
            sheet=DurationPicker(activity,25,"Duration") { selected.add(it) }.also { it.show() }
            val dial=sheet!!.dial
            val range=dial.createAccessibilityNodeInfo().rangeInfo
            assertEquals(1f,range.min,0f); assertEquals(180f,range.max,0f)
            assertTrue(dial.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,27f) }))
            assertEquals(27,dial.minutes)
            dial.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null); assertEquals(30,dial.minutes)
            dial.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,null); assertEquals(25,dial.minutes)
            dial.performClick()
        }
        val input=awaitNode("${instrumentation.targetContext.packageName}:id/duration_exact_input")
        assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"17") }))
        assertTrue(awaitNode("android:id/button1").performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
        onMain {
            assertEquals(17,sheet!!.dial.minutes); assertTrue(selected.isEmpty())
            sheet!!.findViewById<Button>(R.id.duration_confirm).performClick(); assertEquals(listOf(17),selected)
        }
    }
    @Test fun draggingThroughGapKeepsEndpointAndDoesNotOpenNumberInput() {
        onMain { sheet=DurationPicker(activity,180,"Duration") { }.also { it.show() } }
        instrumentation.waitForIdleSync()
        onMain {
            val dial=sheet!!.dial; val radius=minOf(dial.width,dial.height)/2f-25*activity.resources.displayMetrics.density
            val down=SystemClock.uptimeMillis()
            fun touch(action: Int,angle: Float,step: Int) {
                val radians=Math.toRadians(angle.toDouble())
                val event=MotionEvent.obtain(down,down+step,action,dial.width/2f+cos(radians).toFloat()*radius,dial.height/2f+sin(radians).toFloat()*radius,0)
                try { assertTrue(dial.dispatchTouchEvent(event)) } finally { event.recycle() }
            }
            touch(MotionEvent.ACTION_DOWN,60f,0)
            touch(MotionEvent.ACTION_MOVE,85f,20); touch(MotionEvent.ACTION_MOVE,115f,40); touch(MotionEvent.ACTION_MOVE,130f,60)
            touch(MotionEvent.ACTION_UP,130f,80)
            assertEquals(180,dial.minutes)
        }
        instrumentation.waitForIdleSync()
        assertNull(activeNode("${instrumentation.targetContext.packageName}:id/duration_exact_input"))
        onMain { sheet!!.dial.performClick() }
        awaitNode("${instrumentation.targetContext.packageName}:id/duration_exact_input")
        onMain { sheet!!.dismiss() }
        instrumentation.waitForIdleSync()
        assertNull(activeNode("${instrumentation.targetContext.packageName}:id/duration_exact_input"))
    }
}
