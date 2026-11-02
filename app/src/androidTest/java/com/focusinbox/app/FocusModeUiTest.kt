package com.focusinbox.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FocusModeUiTest {
    @Test fun modeMenuChangesActiveRoundWithoutResettingClock() {
        val ins=InstrumentationRegistry.getInstrumentation()
        val c=ins.targetContext
        val settings=Settings(c)
        val previousMode=settings.focusMode
        val enabled=settings.enabled
        val minutes=settings.minutes
        settings.enabled=false; settings.minutes=35
        TimerEngine.rest(c); TimerEngine.setMode(c,FocusMode.SINGLE)
        val activity=ins.startActivitySync(Intent(c,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        fun capture(name: String) {
            ins.waitForIdleSync(); SystemClock.sleep(350)
            val bitmap=ins.uiAutomation.takeScreenshot()
            File(c.getExternalFilesDir(null),"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        fun choose(text: String) {
            val end=SystemClock.uptimeMillis()+3000
            var clicked=false
            while(!clicked && SystemClock.uptimeMillis()<end) {
                clicked=ins.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)
                    ?.any { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }==true
                if(!clicked) SystemClock.sleep(50)
            }
            assertTrue("Mode choice was not accessible: $text",clicked)
            ins.waitForIdleSync()
        }
        try {
            capture("focus-mode-single")
            ins.runOnMainSync { activity.findViewById<Button>(R.id.focus_mode_menu).performClick() }
            capture("focus-mode-menu")
            choose(activity.getString(R.string.focus_mode_loop_option,35))
            assertEquals(FocusMode.LOOP,settings.focusMode)
            TimerEngine.start(c)
            val first=Store(c).use { it.timer() }
            ins.runOnMainSync { activity.findViewById<Button>(R.id.focus_mode_menu).performClick() }
            choose(activity.getString(R.string.focus_mode_single_option))
            assertEquals(first.copy(mode=FocusMode.SINGLE),Store(c).use { it.timer() })
            ins.runOnMainSync { activity.findViewById<Button>(R.id.focus_mode_menu).performClick() }
            choose(activity.getString(R.string.focus_mode_loop_option,35))
            assertEquals(first,Store(c).use { it.timer() })
            capture("focus-mode-loop")
            // Open the actual recents confirmation, then cancel it without changing that setting.
            fun views(v: View): Sequence<View> = sequence {
                yield(v); if(v is ViewGroup) for(i in 0 until v.childCount) yieldAll(views(v.getChildAt(i)))
            }
            ins.runOnMainSync {
                activity.findViewById<ViewGroup>(R.id.page_navigation).getChildAt(2).performClick()
            }
            ins.waitForIdleSync()
            ins.runOnMainSync {
                views(activity.window.decorView).filterIsInstance<android.widget.Switch>()
                    .first { it.text==activity.getString(R.string.recents_hide) }.performClick()
            }
            capture("mint-confirmation")
            choose(activity.getString(R.string.cancel))
        } finally {
            TimerEngine.rest(c); settings.focusMode=previousMode; settings.enabled=enabled; settings.minutes=minutes
            ins.runOnMainSync { activity.finish() }
        }
    }
}
