package com.focusinbox.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.SpinnerAdapter
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpinnerLocaleTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private lateinit var originalLanguage: UiLanguage
    private var automationFlags=0
    @Before fun launch() {
        originalLanguage=LocaleHelper.current(instrumentation.targetContext)
        val info=instrumentation.uiAutomation.serviceInfo; automationFlags=info.flags
        info.flags=info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS; instrumentation.uiAutomation.serviceInfo=info
        activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        instrumentation.waitForIdleSync()
    }
    @After fun close() {
        onMain { LocaleHelper.change(activity,originalLanguage); if(!activity.isDestroyed) activity.finish() }
        val info=instrumentation.uiAutomation.serviceInfo; info.flags=automationFlags; instrumentation.uiAutomation.serviceInfo=info
        instrumentation.waitForIdleSync()
    }
    @Suppress("UNCHECKED_CAST") private fun <T> onMain(action: ()->T): T {
        var result: T?=null; var error: Throwable?=null
        instrumentation.runOnMainSync { try { result=action() } catch(t: Throwable) { error=t } }
        error?.let { throw it }; return result as T
    }
    private fun await(condition: ()->Boolean) {
        val end=SystemClock.uptimeMillis()+4000
        while(!condition() && SystemClock.uptimeMillis()<end) SystemClock.sleep(25)
        assertTrue("UI did not settle",condition())
    }
    private fun views(view: View): Sequence<View> = sequence {
        yield(view); if(view is ViewGroup) for(index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
    }
    private fun content(): ViewGroup { val host=activity.findViewById<FrameLayout>(R.id.page_host); return host.getChildAt(host.childCount-1) as ViewGroup }
    private fun pickers()=listOf(R.string.language_title,R.string.provider_label,R.string.delivery_label).map { label ->
        views(content()).filterIsInstance<Spinner>().first { it.contentDescription==activity.getString(label) }
    }
    private fun edits()=listOf(activity.findViewById<EditText>(R.id.api_key_input)) +
        listOf(R.string.webhook_label,R.string.endpoint_label,R.string.model_label,R.string.signing_label,R.string.prompt_label).map { label ->
            views(content()).filterIsInstance<EditText>().first { it.contentDescription==activity.getString(label) }
        }
    private fun scroll()=views(content()).filterIsInstance<ScrollView>().first()
    private fun openSettings() {
        onMain { (activity.findViewById<ViewGroup>(R.id.page_navigation).getChildAt(2) as Button).performClick() }
        await { onMain { val host=activity.findViewById<FrameLayout>(R.id.page_host); host.childCount==1 && host.getChildAt(0).alpha==1f } }
    }
    private fun chooseLanguage(language: UiLanguage) {
        onMain { pickers()[0].setSelection(UiLanguage.entries.indexOf(language),false) }
        await { onMain { activity.resources.configuration.locales[0].language==language.tag && Settings(activity).language==language } }
    }
    private fun mint(color: Int) { assertTrue("Expected mint/green rather than OEM accent",Color.green(color)>Color.red(color) && Color.green(color)>Color.blue(color)) }
    private fun activeNode(id: String): AccessibilityNodeInfo?=instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()
    private fun awaitNode(id: String): AccessibilityNodeInfo {
        val end=SystemClock.uptimeMillis()+3000
        while(SystemClock.uptimeMillis()<end) { activeNode(id)?.let { return it }; SystemClock.sleep(30) }
        throw AssertionError("Missing control: $id")
    }
    @Test fun allSettingsSpinnersUseMintRowsAndDoNotStyleMeasurementParents() {
        openSettings()
        onMain {
            val all=views(content()).filterIsInstance<Spinner>().toList(); assertEquals(4,all.size); assertTrue(all.all { it is MintSpinner })
            all.forEach { spinner ->
                assertEquals(Spinner::class.java.name,spinner.createAccessibilityNodeInfo().className.toString())
                val adapter=spinner.adapter as SpinnerAdapter
                assertTrue(adapter.getView(0,null,FrameLayout(activity)) is LinearLayout)
                val popup=ListView(activity).apply { layout(0,0,400,200) }
                val row=adapter.getDropDownView(spinner.selectedItemPosition,null,popup)
                assertTrue(row is TextView); assertTrue(popup.clipToOutline)
                row.isPressed=true; mint((row.background.current as ColorDrawable).color)
                row.isPressed=false; row.isActivated=true; mint((row.background.current as ColorDrawable).color)
                popup.selector.state=intArrayOf(android.R.attr.state_pressed); mint((popup.selector.current as ColorDrawable).color)
                // Drawing sets the new background bounds, just as the attached popup does.
                val bitmap=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888)
                popup.draw(Canvas(bitmap)); bitmap.recycle()
                val outline=Outline(); popup.outlineProvider.getOutline(popup,outline); assertTrue(outline.radius>0f)
                val attributes=spinner.popupContext.obtainStyledAttributes(intArrayOf(android.R.attr.colorControlHighlight))
                try { mint(attributes.getColor(0,Color.MAGENTA)) } finally { attributes.recycle() }
                val temporary=FrameLayout(activity); val sentinel=ColorDrawable(Color.RED); temporary.background=sentinel
                adapter.getDropDownView(0,null,temporary)
                assertSame(sentinel,temporary.background); assertFalse(temporary.clipToOutline)
            }
        }
    }
    @Test fun languageSwitchKeepsWindowDraftsPageAndScrollAndIgnoresStaleSelection() {
        openSettings(); chooseLanguage(UiLanguage.CHINESE)
        onMain { pickers()[1].setSelection(1,false); pickers()[2].setSelection(1,false) }
        instrumentation.waitForIdleSync()
        val before=instrumentation.targetContext.getSharedPreferences("settings",0).all.filterKeys { it!="language" }
        val values=listOf("unsaved-locale-key","https://open.feishu.cn/open-apis/bot/v2/hook/draft","https://example.com/draft","draft-model","draft-sign","保留我的草稿")
        var oldWindow: android.view.Window?=null
        lateinit var stalePicker: Spinner
        var staleListener: android.widget.AdapterView.OnItemSelectedListener?=null
        onMain {
            oldWindow=activity.window
            edits().forEachIndexed { index,field -> field.setText(values[index]) }
            views(content()).filterIsInstance<Button>().first { it.text.toString()==activity.getString(R.string.advanced_expand) }.performClick()
            stalePicker=pickers()[0]; staleListener=stalePicker.onItemSelectedListener
        }
        instrumentation.waitForIdleSync()
        onMain { scroll().scrollTo(0,280) }
        chooseLanguage(UiLanguage.ENGLISH)
        await { onMain { scroll().scrollY==280 } }
        onMain {
            assertFalse(activity.isDestroyed); assertSame(oldWindow,activity.window)
            assertEquals("StillBrief",(activity.findViewById<ViewGroup>(R.id.page_header).getChildAt(0) as TextView).text.toString())
            val nav=activity.findViewById<ViewGroup>(R.id.page_navigation)
            assertEquals("Settings",(nav.getChildAt(2) as Button).text.toString()); assertTrue(nav.getChildAt(2).isSelected)
            assertEquals(values,edits().map { it.text.toString() })
            assertEquals(1,pickers()[1].selectedItemPosition); assertEquals(1,pickers()[2].selectedItemPosition)
            assertNull(stalePicker.onItemSelectedListener)
            staleListener?.onItemSelected(stalePicker,null,0,0)
            assertEquals(UiLanguage.ENGLISH,Settings(activity).language)
        }
        assertEquals(before,instrumentation.targetContext.getSharedPreferences("settings",0).all.filterKeys { it!="language" })
    }
    @Test fun systemLocaleChangeUpdatesRealDialogResourcesAndClosesOwnedDialogs() {
        Assume.assumeTrue(Build.VERSION.SDK_INT>=33)
        openSettings(); chooseLanguage(UiLanguage.ENGLISH)
        val window=onMain { activity.window }
        onMain { (activity.findViewById<ViewGroup>(R.id.page_navigation).getChildAt(0) as Button).performClick() }
        instrumentation.waitForIdleSync()
        val needsChoice=Store(instrumentation.targetContext).use { it.timer().state in setOf("RUNNING","PAUSED") }
        onMain { activity.findViewById<View>(R.id.focus_clock).performClick() }
        if(needsChoice) {
            val title=activity.getString(R.string.duration_next_only)
            await { instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(title)?.isNotEmpty()==true }
            instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(title).first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        val dial=awaitNode("${instrumentation.targetContext.packageName}:id/duration_dial")
        assertTrue(dial.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        val exactTitle=onMain { activity.getString(R.string.duration_exact_title) }
        await { instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(exactTitle)?.isNotEmpty()==true }
        assertEquals("Enter exact minutes",exactTitle)
        onMain { LocaleHelper.change(activity,UiLanguage.CHINESE) }
        await { onMain { activity.resources.configuration.locales[0].language=="zh" && (activity.findViewById<ViewGroup>(R.id.page_header).getChildAt(0) as TextView).text.toString()=="息间" } }
        onMain {
            assertSame(window,activity.window); assertFalse(activity.isDestroyed)
            assertEquals("专注",(activity.findViewById<ViewGroup>(R.id.page_navigation).getChildAt(0) as Button).text.toString())
        }
        instrumentation.waitForIdleSync()
        assertNull(activeNode("${instrumentation.targetContext.packageName}:id/duration_exact_input"))
        assertNull(activeNode("${instrumentation.targetContext.packageName}:id/duration_dial"))
        // Even a same-language configuration callback refreshes resource density/configuration.
        onMain {
            val update=Configuration(activity.resources.configuration).apply { densityDpi+=1 }
            activity.onConfigurationChanged(update)
            assertEquals(update.densityDpi,activity.resources.configuration.densityDpi)
        }
    }
}
