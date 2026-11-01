package com.focusinbox.app

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageTransitionTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    @Before fun launch() {
        activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        instrumentation.waitForIdleSync()
    }
    @After fun close() { if(!activity.isDestroyed) onMain { activity.finish() }; instrumentation.waitForIdleSync() }
    @Suppress("UNCHECKED_CAST")
    private fun <T> onMain(action: ()->T): T {
        var result: T?=null; var failure: Throwable?=null
        instrumentation.runOnMainSync { try { result=action() } catch(error: Throwable) { failure=error } }
        failure?.let { throw it }
        return result as T
    }
    private fun views(root: View): Sequence<View> = sequence {
        yield(root)
        if(root is ViewGroup) for(index in 0 until root.childCount) yieldAll(views(root.getChildAt(index)))
    }
    private fun await(condition: ()->Boolean) {
        val deadline=SystemClock.uptimeMillis()+4000
        while(!condition() && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(25)
        assertTrue("UI did not settle",condition())
    }
    @Test fun rapidTabsKeepLatestSettingsDraftAndStationaryChrome() {
        val prefs=instrumentation.targetContext.getSharedPreferences("settings",0)
        val before=prefs.all.toMap()
        val synthetic="unsaved-motion-key-${System.nanoTime()}"
        onMain {
            val header=activity.findViewById<View>(R.id.page_header)
            val nav=activity.findViewById<ViewGroup>(R.id.page_navigation)
            val host=activity.findViewById<FrameLayout>(R.id.page_host)
            (nav.getChildAt(2) as Button).performClick()
            val oldLayer=host.getChildAt(host.childCount-1)
            val api=views(oldLayer).filterIsInstance<EditText>().first()
            api.setText(synthetic)
            (nav.getChildAt(0) as Button).performClick()
            assertFalse(api.isEnabled)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,oldLayer.importantForAccessibility)
            (nav.getChildAt(1) as Button).performClick()
            (nav.getChildAt(2) as Button).performClick()
            assertTrue(nav.getChildAt(2).isSelected)
            assertSame(header,activity.findViewById<View>(R.id.page_header))
            assertSame(nav,activity.findViewById<ViewGroup>(R.id.page_navigation))
            val current=host.getChildAt(host.childCount-1)
            assertEquals(synthetic,views(current).filterIsInstance<EditText>().first().text.toString())
        }
        await { onMain { val host=activity.findViewById<FrameLayout>(R.id.page_host); host.childCount==1 && host.getChildAt(0).alpha==1f } }
        onMain {
            val host=activity.findViewById<FrameLayout>(R.id.page_host)
            assertEquals(synthetic,views(host).filterIsInstance<EditText>().first().text.toString())
        }
        assertEquals(before,prefs.all)
    }
    @Test fun pauseSettlesLatestPageAndDestroyLeavesNoLayers() {
        lateinit var host: FrameLayout
        onMain {
            host=activity.findViewById(R.id.page_host)
            val nav=activity.findViewById<ViewGroup>(R.id.page_navigation)
            (nav.getChildAt(2) as Button).performClick()
            (nav.getChildAt(1) as Button).performClick()
            instrumentation.callActivityOnPause(activity)
            assertEquals(1,host.childCount); assertEquals(1f,host.getChildAt(0).alpha,0f)
            assertEquals(0f,host.getChildAt(0).translationX,0f)
            instrumentation.callActivityOnResume(activity)
            (nav.getChildAt(2) as Button).performClick()
            activity.finish()
        }
        await { onMain { activity.isDestroyed } }
        onMain { assertEquals(0,host.childCount) }
        SystemClock.sleep(300)
        onMain { assertEquals(0,host.childCount) }
    }
    @Test fun disabledAnimationReplacesImmediatelyWithoutLeavingOldContent() {
        onMain {
            val host=FrameLayout(activity)
            val transition=PageTransition(host) { false }
            val first=Button(activity); val latest=Button(activity)
            transition.replace(first,false)
            transition.replace(latest,true,PageTransition.Direction.FORWARD)
            assertFalse(transition.isAnimating); assertEquals(1,host.childCount)
            assertSame(latest.parent,host.getChildAt(0))
            assertEquals(1f,host.getChildAt(0).alpha,0f); assertEquals(0f,host.getChildAt(0).translationX,0f)
            transition.clear(); assertEquals(0,host.childCount)
        }
    }
}
