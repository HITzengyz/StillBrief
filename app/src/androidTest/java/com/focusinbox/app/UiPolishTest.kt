package com.focusinbox.app

import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UiPolishTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=ins.targetContext
    private fun launch()=ins.startActivitySync(Intent(c,MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    private fun main(block: ()->Unit)=ins.runOnMainSync(block)
    private fun navigate(a: MainActivity,page: Int) {
        main { a.findViewById<ViewGroup>(R.id.page_navigation).getChildAt(page).performClick() }
        val end=SystemClock.uptimeMillis()+3000
        var settled=false
        while(!settled && SystemClock.uptimeMillis()<end) {
            ins.waitForIdleSync()
            main { val host=a.findViewById<FrameLayout>(R.id.page_host); settled=host.childCount==1 && host.getChildAt(0).alpha==1f }
            if(!settled) SystemClock.sleep(30)
        }
        assertTrue("Page must settle",settled)
    }
    private fun views(v: View): Sequence<View> = sequence {
        yield(v); if(v is ViewGroup) for(i in 0 until v.childCount) yieldAll(views(v.getChildAt(i)))
    }
    private fun screenshot(name: String) {
        ins.waitForIdleSync(); SystemClock.sleep(250)
        val bitmap=ins.uiAutomation.takeScreenshot()!!
        File(c.getExternalFilesDir(null),"polish-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }

    @Test fun copyFeedbackExpiresAndEditingOrLeavingCancelsOldFeedback() {
        val a=launch()
        try {
            navigate(a,2)
            lateinit var copy: ImageButton
            lateinit var key: EditText
            main {
                key=a.findViewById(R.id.api_key_input); key.setText("synthetic-copy-preview")
                copy=a.findViewById(R.id.api_key_copy); copy.performClick()
                assertTrue(copy.isActivated)
            }
            SystemClock.sleep(2900); ins.waitForIdleSync()
            main {
                assertFalse(copy.isActivated); assertEquals(1f,copy.alpha,0.01f)
                copy.performClick(); assertTrue(copy.isActivated)
                key.setText("replacement-draft"); assertFalse(copy.isActivated)
                assertEquals(a.getString(R.string.api_key_copy),copy.contentDescription)
                copy.performClick()
            }
            navigate(a,0)
            main { assertFalse(copy.isActivated) }
            SystemClock.sleep(2700)
            navigate(a,2)
            main {
                assertEquals("replacement-draft",a.findViewById<EditText>(R.id.api_key_input).text.toString())
                assertFalse(a.findViewById<ImageButton>(R.id.api_key_copy).isActivated)
            }
        } finally {
            main { c.getSystemService(ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("","")); a.finish() }
        }
    }

    @Test fun rapidFoldingAndPageChangesKeepDraftAndTimer() {
        val a=launch(); val timer=Store(c).use { it.timer() }
        try {
            navigate(a,2)
            main {
                a.findViewById<EditText>(R.id.api_key_input).setText("folding-draft")
                val toggle=a.findViewById<Button>(R.id.advanced_toggle)
                repeat(10) { toggle.performClick() }
                assertEquals(View.GONE,a.findViewById<View>(R.id.advanced_fields).visibility)
                toggle.performClick()
            }
            navigate(a,0); navigate(a,2)
            main {
                assertEquals("folding-draft",a.findViewById<EditText>(R.id.api_key_input).text.toString())
                assertEquals(View.VISIBLE,a.findViewById<View>(R.id.advanced_fields).visibility)
                a.findViewById<Button>(R.id.advanced_toggle).performClick()
            }
            SystemClock.sleep(650); ins.waitForIdleSync()
            main { assertEquals(View.GONE,a.findViewById<View>(R.id.advanced_fields).visibility) }
            assertEquals(timer,Store(c).use { it.timer() })
        } finally { main { a.finish() } }
    }

    @Test fun captureBothAppearancesAndVerifyTimerDigitsHaveEqualWidths() {
        val settings=Settings(c); val old=settings.appearance
        try {
            for(mode in listOf(AppearanceMode.LIGHT,AppearanceMode.DARK)) {
                settings.appearance=mode
                val a=launch(); var sheet: DurationPicker?=null
                try {
                    main {
                        val clock=a.findViewById<TextView>(R.id.focus_clock)
                        assertEquals(clock.paint.measureText("11:11"),clock.paint.measureText("88:88"),0.5f)
                    }
                    screenshot("${mode.name.lowercase()}-focus")
                    main { sheet=DurationPicker(a,35,a.getString(R.string.duration_default_title)) {}.also { it.show() } }
                    screenshot("${mode.name.lowercase()}-duration")
                    main { sheet!!.dismiss(); sheet=null }
                    navigate(a,2)
                    main {
                        views(a.window.decorView).filterIsInstance<EditText>().forEach { it.setText("") }
                        val key=a.findViewById<EditText>(R.id.api_key_input)
                        key.setText("synthetic-preview-only"); key.requestFocus()
                        val scroll=views(a.window.decorView).filterIsInstance<ScrollView>().first()
                        // Center the provider and API key rather than photographing the language controls.
                        val row=key.parent as View
                        scroll.scrollTo(0,(row.top-180*a.resources.displayMetrics.density).toInt().coerceAtLeast(0))
                    }
                    screenshot("${mode.name.lowercase()}-settings")
                } finally { main { sheet?.dismiss(); a.finish() } }
            }
        } finally { settings.appearance=old }
    }
}
