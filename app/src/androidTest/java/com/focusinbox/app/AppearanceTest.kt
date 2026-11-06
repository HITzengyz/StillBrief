package com.focusinbox.app

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SpinnerAdapter
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@RunWith(AndroidJUnit4::class)
class AppearanceTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=ins.targetContext
    private lateinit var activity: MainActivity
    private lateinit var originalMode: AppearanceMode
    private var startedTimer=false
    private var sheet: DurationPicker?=null
    private var dialog: AlertDialog?=null

    @Before fun launch() {
        originalMode=Settings(c).appearance
        activity=ins.startActivitySync(Intent(c,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        ins.waitForIdleSync()
    }
    @After fun close() {
        onMain {
            dialog?.dismiss(); sheet?.dismiss()
            Settings(c).appearance=originalMode
            if(!activity.isDestroyed) activity.finish()
        }
        if(startedTimer) TimerEngine.rest(c)
        FocusWidgets.refresh(c)
        ins.waitForIdleSync()
    }
    @Suppress("UNCHECKED_CAST") private fun <T> onMain(action: ()->T): T {
        var value: T?=null; var error: Throwable?=null
        ins.runOnMainSync { try { value=action() } catch(t: Throwable) { error=t } }
        error?.let { throw it }; return value as T
    }
    private fun await(message: String,condition: ()->Boolean) {
        val end=SystemClock.uptimeMillis()+4000
        while(!condition() && SystemClock.uptimeMillis()<end) SystemClock.sleep(25)
        assertTrue(message,condition())
    }
    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if(view is ViewGroup) for(i in 0 until view.childCount) yieldAll(views(view.getChildAt(i)))
    }
    private fun content(): View {
        val host=activity.findViewById<FrameLayout>(R.id.page_host)
        return host.getChildAt(host.childCount-1)
    }
    private fun scroll()=views(content()).filterIsInstance<ScrollView>().first()
    private fun settingsPage() {
        onMain { activity.findViewById<ViewGroup>(R.id.page_navigation).getChildAt(2).performClick() }
        await("Settings transition must settle") { onMain {
            activity.findViewById<FrameLayout>(R.id.page_host).childCount==1 &&
                activity.findViewById<View>(R.id.appearance_picker)!=null && content().alpha==1f
        } }
    }
    private fun choose(mode: AppearanceMode) {
        onMain { activity.findViewById<MintSpinner>(R.id.appearance_picker).setSelection(AppearanceMode.entries.indexOf(mode),false) }
        await("Appearance selection must rebuild Settings") { onMain {
            Settings(c).appearance==mode &&
                activity.findViewById<MintSpinner>(R.id.appearance_picker).selectedItemPosition==AppearanceMode.entries.indexOf(mode) &&
                activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==when(mode) {
                    AppearanceMode.DARK -> Configuration.UI_MODE_NIGHT_YES
                    AppearanceMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
                    AppearanceMode.SYSTEM -> activity.baseContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                }
        } }
        ins.waitForIdleSync()
    }
    private fun night(night: Boolean)=Configuration(activity.baseContext.resources.configuration).apply {
        uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if(night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }
    private fun assertNight(expected: Boolean) {
        assertEquals(if(expected) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO,
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        val wrapped=Appearance.wrap(activity)
        assertEquals(expected,wrapped.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES)
        assertEquals(wrapped.getColor(R.color.sb_surface),Appearance.palette(activity).surface)
        if(android.os.Build.VERSION.SDK_INT>=30) {
            val flags=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            assertEquals(if(expected) 0 else flags,activity.window.insetsController!!.systemBarsAppearance and flags)
        }
    }
    private fun contrast(a: Int,b: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(v: Int): Double { val x=v/255.0; return if(x<=0.04045) x/12.92 else ((x+0.055)/1.055).pow(2.4) }
            return .2126*channel(Color.red(color))+.7152*channel(Color.green(color))+.0722*channel(Color.blue(color))
        }
        val x=luminance(a); val y=luminance(b)
        return (max(x,y)+.05)/(min(x,y)+.05)
    }
    private fun screenshot(name: String) {
        ins.waitForIdleSync()
        val bitmap=ins.uiAutomation.takeScreenshot() ?: throw AssertionError("No screenshot")
        try { File(c.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
        finally { bitmap.recycle() }
    }

    @Test fun defaultSystemAndExplicitModesPersistWithoutChangingSystemConfiguration() {
        val prefs="appearance-test-only"
        c.deleteSharedPreferences(prefs)
        try {
            assertEquals(AppearanceMode.SYSTEM,Settings(c,prefs).appearance)
            assertEquals(AppearanceMode.SYSTEM,AppearanceMode.fromStored("unknown"))
            for(mode in AppearanceMode.entries) {
                Settings(c,prefs).appearance=mode
                assertEquals(mode,Settings(c,prefs).appearance)
            }
            val source=night(true)
            val original=source.uiMode
            assertEquals(Configuration.UI_MODE_NIGHT_NO,Appearance.configuration(source,AppearanceMode.LIGHT).uiMode and Configuration.UI_MODE_NIGHT_MASK)
            assertEquals(original,source.uiMode)
        } finally { c.deleteSharedPreferences(prefs) }
    }

    @Test fun switchingKeepsWindowDraftScrollTabAndTimerAndCapturesBothAppearances() {
        // The runner uses an isolated application ID; no saved credentials or network work is needed.
        val before=Store(c).use { db ->
            val t=db.timer()
            if(t.state=="IDLE") { startedTimer=true; db.startFocus(35,FocusMode.SINGLE) } else t
        }
        settingsPage(); choose(AppearanceMode.LIGHT)
        val window=activity.window; val decor=window.decorView
        val prefs=c.getSharedPreferences("settings",0)
        val savedKey=prefs.getString("provider.${Settings(c).provider.id}.key",null)
        val draft="synthetic-unsaved-appearance-key"
        onMain {
            // Every editable field is sanitized before screenshots, including collapsed advanced fields.
            views(content()).filterIsInstance<EditText>().forEach { it.setText("synthetic-preview-only") }
            activity.findViewById<EditText>(R.id.api_key_input).setText(draft)
            scroll().scrollTo(0,160)
        }
        await("Settings must scroll before switching") { onMain { scroll().scrollY>0 } }
        val y=onMain { scroll().scrollY }
        screenshot("appearance-light-settings.png")
        choose(AppearanceMode.DARK)
        await("Scroll position must survive appearance change") { onMain { scroll().scrollY==y } }
        onMain {
            assertSame(window,activity.window); assertSame(decor,activity.window.decorView)
            assertTrue(activity.findViewById<ViewGroup>(R.id.page_navigation).getChildAt(2).isSelected)
            assertEquals(draft,activity.findViewById<EditText>(R.id.api_key_input).text.toString())
            assertNight(true)
        }
        assertEquals(before,Store(c).use { it.timer() })
        assertEquals(savedKey,prefs.getString("provider.${Settings(c).provider.id}.key",null))
        screenshot("appearance-dark-settings.png")
        choose(AppearanceMode.LIGHT)
        onMain { assertSame(window,activity.window); assertNight(false) }
        assertEquals(before,Store(c).use { it.timer() })
    }

    @Test fun systemFollowsConfigurationEventsWhileFixedModesIgnoreThem() {
        settingsPage()
        for(mode in AppearanceMode.entries) {
            choose(mode)
            val window=activity.window
            for(systemNight in listOf(false,true,false)) {
                onMain { activity.onConfigurationChanged(night(systemNight)) }
                ins.waitForIdleSync()
                onMain {
                    assertSame(window,activity.window)
                    assertNight(when(mode) { AppearanceMode.SYSTEM -> systemNight; AppearanceMode.DARK -> true; AppearanceMode.LIGHT -> false })
                    assertTrue(activity.findViewById<ViewGroup>(R.id.page_navigation).getChildAt(2).isSelected)
                }
            }
        }
    }

    @Test fun nativeDialogsAndDropdownRowsUseReadablePaletteInBothModes() {
        settingsPage()
        for(mode in listOf(AppearanceMode.LIGHT,AppearanceMode.DARK)) {
            choose(mode)
            onMain {
                val p=Appearance.palette(activity)
                val picker=activity.findViewById<MintSpinner>(R.id.appearance_picker)
                val popup=ListView(activity)
                val row=(picker.adapter as SpinnerAdapter).getDropDownView(picker.selectedItemPosition,null,popup) as TextView
                assertEquals(p.text,row.currentTextColor)
                row.isActivated=false; row.isSelected=false; row.isPressed=false
                assertEquals(p.surface,(row.background.current as ColorDrawable).color)
                assertTrue(contrast(row.currentTextColor,p.surface)>=4.5)
                row.isActivated=true
                assertEquals(p.selected,(row.background.current as ColorDrawable).color)
                assertTrue(contrast(row.currentTextColor,p.selected)>=4.5)
                dialog=MintDialogBuilder(activity).setTitle("Synthetic dialog").setMessage("Preview message")
                    .setNegativeButton("Cancel",null).setPositiveButton("Confirm",null).show()
                val shown=dialog!!
                assertEquals(p.surface,(shown.window!!.decorView.background as GradientDrawable).color!!.defaultColor)
                assertEquals(p.onAccent,shown.getButton(AlertDialog.BUTTON_POSITIVE).currentTextColor)
                assertEquals(p.accent,shown.getButton(AlertDialog.BUTTON_NEGATIVE).currentTextColor)
                assertTrue(contrast(p.text,p.surface)>=4.5)
                assertTrue(contrast(p.onAccent,p.accent)>=4.5)
                shown.dismiss(); dialog=null
            }
        }
    }

    @Test fun durationExactInputRemainsReadableAndValidatesWithoutApplyingDraft() {
        settingsPage()
        for(mode in listOf(AppearanceMode.LIGHT,AppearanceMode.DARK)) {
            choose(mode)
            var confirmations=0
            onMain {
                sheet=DurationPicker(activity,35,"Synthetic duration") { confirmations++ }.also { it.show() }
                sheet!!.dial.performClick()
            }
            // AlertDialog posts OnShow, which installs the validation click listener.
            ins.waitForIdleSync()
            onMain {
                // Inspect the actual child window without adding a production-only test accessor.
                val field=DurationPicker::class.java.getDeclaredField("numericDialog").apply { isAccessible=true }
                val exact=field.get(sheet) as AlertDialog
                val input=exact.findViewById<EditText>(R.id.duration_exact_input)
                val p=Appearance.palette(activity)
                assertEquals(p.text,input.currentTextColor)
                assertEquals(p.secondary,input.currentHintTextColor)
                assertTrue(contrast(input.currentTextColor,p.surface)>=4.5)
                input.setText("181"); exact.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                assertTrue(exact.isShowing); assertNotNull(input.error); assertEquals(35,sheet!!.dial.minutes)
                input.setText("17"); exact.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                assertFalse(exact.isShowing); assertEquals(17,sheet!!.dial.minutes); assertEquals(0,confirmations)
                sheet!!.dismiss(); sheet=null; assertEquals(0,confirmations)
            }
        }
    }
}
