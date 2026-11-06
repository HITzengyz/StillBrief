package com.focusinbox.app

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FocusWidgetTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=ins.targetContext
    private fun dp(v: Int)=(v*c.resources.displayMetrics.density).toInt()
    private fun waitFor(message: String,condition: ()->Boolean) {
        val end=SystemClock.uptimeMillis()+5000
        while(SystemClock.uptimeMillis()<end) { if(condition()) return; SystemClock.sleep(40) }
        assertTrue(message,condition())
    }
    @Test fun setupStatusOpensSettingsEvenWithAnOldFailure() {
        val activity=ins.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        try {
            for(last in listOf("","FAILED")) {
                ins.runOnMainSync {
                    val state=WidgetState("IDLE",0,0,0,35,false,0,false,false,last)
                    val view=FocusWidgets.render(c,123,true,state).apply(activity,FrameLayout(activity))
                    view.findViewById<View>(R.id.widget_status).performClick()
                }
                ins.waitForIdleSync()
                waitFor("Setup status should open Settings") {
                    activity.findViewById<android.view.ViewGroup>(R.id.page_navigation).getChildAt(2).isSelected
                }
                ins.runOnMainSync { activity.findViewById<android.view.ViewGroup>(R.id.page_navigation).getChildAt(0).performClick() }
            }
        } finally { ins.runOnMainSync { activity.finish() } }
    }
    @Test fun directCommandsStayOnHostAndBothWidgetsShareTheSameSession() {
        // This suite runs in a separate application ID. Only synthetic credentials and
        // locally excluded status notifications are used; no AI or Feishu request is made.
        val settings=Settings(c)
        val saved=settings.snapshot(); val minutes=settings.minutes; val mode=settings.focusMode
        TimerEngine.rest(c)
        settings.saveBasic(Provider.DEEPSEEK,"widget-test-only","https://open.feishu.cn/open-apis/bot/v2/hook/widget-test-only",true,DeliveryTarget.FEISHU)
        settings.minutes=35; settings.focusMode=FocusMode.LOOP
        Store(c).use { it.clear() }
        val activity=ins.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val marker=TextView(activity)
        ins.runOnMainSync { marker.text="Widget test host"; activity.setContentView(marker) }
        fun send(action: WidgetAction,token: Long,id: Int=1) {
            val pending=FocusWidgets.command(c,id,action,token)
            if(Build.VERSION.SDK_INT>=31) assertTrue(pending.isBroadcast)
            pending.send()
        }
        fun unchangedHost() { ins.waitForIdleSync(); assertTrue(marker.isAttachedToWindow); assertFalse(activity.isFinishing) }
        try {
            val idle=FocusWidgets.snapshot(c)
            assertTrue(idle.ready)
            send(WidgetAction.START,idle.token)
            waitFor("Start must execute without opening an Activity") { FocusWidgets.snapshot(c).active }
            val first=Store(c).use { it.timer() }
            send(WidgetAction.START,idle.token,2)
            SystemClock.sleep(200)
            assertEquals(first,Store(c).use { it.timer() }); unchangedHost()
            val noise=Notice(key="widget-noise",pkg="android",app="Android",title="2台设备",body="正在共享WLAN网络",at=System.currentTimeMillis(),kind="unknown")
            assertTrue(NotificationCandidates.prepare(listOf(noise)).isEmpty())
            Store(c).use { assertTrue(it.collect(noise)) }
            send(WidgetAction.ANALYZE,first.token)
            waitFor("Analysis must finish without a network request") { Store(c).use { it.batches().any { b -> b.state=="EMPTY" } } }
            assertEquals(first,Store(c).use { it.timer() }); unchangedHost()
            val count=Store(c).use { it.batches().size }
            send(WidgetAction.ANALYZE,first.token,2)
            SystemClock.sleep(150)
            assertEquals(count,Store(c).use { it.batches().size })
            TimerEngine.pause(c)
            val paused=FocusWidgets.snapshot(c)
            assertEquals(WidgetAction.RESUME,paused.primary)
            send(WidgetAction.RESUME,paused.token,2)
            waitFor("Resume must preserve the round") { FocusWidgets.snapshot(c).timerState=="RUNNING" }
            val resumed=Store(c).use { it.timer() }
            assertEquals(first.roundId,resumed.roundId)
            send(WidgetAction.END,resumed.token)
            waitFor("End must stop the loop") { FocusWidgets.snapshot(c).timerState=="IDLE" }
            val stopped=FocusWidgets.snapshot(c)
            send(WidgetAction.START,stopped.token,2)
            waitFor("Another widget starts the same shared session") { FocusWidgets.snapshot(c).active }
            val next=Store(c).use { it.timer() }
            send(WidgetAction.END,resumed.token)
            SystemClock.sleep(200)
            assertEquals(next,Store(c).use { it.timer() }); unchangedHost()
        } finally {
            TimerEngine.rest(c)
            settings.saveBasic(saved.ai.provider,saved.ai.key,saved.webhook,saved.enabled,saved.target)
            settings.minutes=minutes; settings.focusMode=mode
            ins.runOnMainSync { activity.finish() }
        }
    }
    @Test fun remoteViewsRenderAllStatesAndLongDurationsInBothLanguages() {
        val settings=Settings(c); val old=settings.language
        val activity=ins.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val base=WidgetState("RUNNING",7,System.currentTimeMillis()+35*60_000,0,35,true,1,false,true)
        try {
            for(language in UiLanguage.entries) {
                ins.runOnMainSync { LocaleHelper.change(activity,language) }; ins.waitForIdleSync()
                for(wide in listOf(true,false)) for(state in listOf(base,base.copy(pending=0),base.copy(busy=true),base.copy(ready=false),base.copy(timerState="PAUSED",remaining=10_800_000),base.copy(timerState="IDLE",minutes=180))) {
                    ins.runOnMainSync {
                        val view=FocusWidgets.render(c,123,wide,state).apply(activity,FrameLayout(activity))
                        activity.setContentView(view)
                        val width=dp(if(wide) 250 else 150); val height=dp(if(wide) 64 else 150)
                        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); view.layout(0,0,width,height)
                        assertEquals(state.primaryEnabled,view.findViewById<Button>(R.id.widget_primary).isEnabled)
                        assertEquals(if(state.active) View.VISIBLE else View.GONE,view.findViewById<Button>(R.id.widget_secondary).visibility)
                        assertTrue(view.findViewById<View>(R.id.widget_time_area).height>0)
                        if(state.timerState!="RUNNING") {
                            val time=view.findViewById<TextView>(R.id.widget_time_static)
                            assertTrue("Long duration must fit horizontally",time.paint.measureText(time.text.toString())<=time.width)
                            assertTrue("Long duration must fit vertically",time.lineHeight<=time.height)
                        }
                        if(state.timerState!="RUNNING") assertEquals("180:00",view.findViewById<TextView>(R.id.widget_time_static).text.toString())
                    }
                }
            }
        } finally { ins.runOnMainSync { LocaleHelper.change(activity,old); activity.finish() } }
    }
    @Test fun launcherHostReceivesUpdatesAndRendersTwoActualProviders() {
        val settings=Settings(c); val old=settings.language
        val saved=settings.snapshot(); val oldMinutes=settings.minutes; val oldMode=settings.focusMode
        TimerEngine.rest(c)
        settings.saveBasic(Provider.DEEPSEEK,"widget-preview-only","https://open.feishu.cn/open-apis/bot/v2/hook/widget-preview-only",true,DeliveryTarget.FEISHU)
        settings.minutes=35; settings.focusMode=FocusMode.LOOP
        Store(c).use { it.clear() }
        TimerEngine.start(c)
        Store(c).use { it.collect(Notice(key="preview-status",pkg="android",app="Android",title="2台设备",body="正在共享WLAN网络",at=System.currentTimeMillis(),kind="unknown")) }
        settings.language=UiLanguage.CHINESE
        val activity=ins.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val host=AppWidgetHost(c,2300); val manager=AppWidgetManager.getInstance(c)
        val ids=mutableListOf<Int>()
        var wideView: AppWidgetHostView?=null; var squareView: AppWidgetHostView?=null
        try {
            ins.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            val infos=listOf(FocusWideWidget::class.java,FocusSquareWidget::class.java).map { type ->
                manager.installedProviders.first { it.provider==ComponentName(c,type) }
            }
            for(info in infos) {
                val id=host.allocateAppWidgetId(); ids.add(id)
                assertTrue("Provider must bind to launcher host",manager.bindAppWidgetIdIfAllowed(id,info.provider))
            }
            ins.runOnMainSync {
                val column=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(48),dp(20),dp(20)); setBackgroundColor(Color.rgb(244,247,241)) }
                column.addView(TextView(activity).apply { text="息间 · 桌面小组件"; textSize=24f; setTextColor(Color.rgb(36,67,54)) })
                column.addView(TextView(activity).apply { text="4 × 1"; textSize=16f; setPadding(0,dp(24),0,dp(8)) })
                wideView=host.createView(activity,ids[0],infos[0]); column.addView(wideView,LinearLayout.LayoutParams(-1,dp(80)))
                column.addView(TextView(activity).apply { text="2 × 2"; textSize=16f; setPadding(0,dp(24),0,dp(8)) })
                squareView=host.createView(activity,ids[1],infos[1]); column.addView(squareView,LinearLayout.LayoutParams(dp(190),dp(190)))
                column.addView(TextView(activity).apply { text="按钮直接执行，点击时间打开专注页。\n演示数据 · Android AppWidgetHost"; textSize=14f; setPadding(0,dp(24),0,0) })
                activity.setContentView(column); host.startListening()
            }
            FocusWidgets.updateAll(c)
            waitFor("Both hosts must render actual RemoteViews") { wideView?.findViewById<View>(R.id.widget_timer)!=null && squareView?.findViewById<View>(R.id.widget_timer)!=null }
            ins.waitForIdleSync(); SystemClock.sleep(1200)
            ins.runOnMainSync {
                assertEquals(wideView!!.findViewById<Chronometer>(R.id.widget_timer).text,squareView!!.findViewById<Chronometer>(R.id.widget_timer).text)
                assertTrue(wideView!!.findViewById<Button>(R.id.widget_primary).isEnabled)
                assertEquals(activity.getString(R.string.widget_analyze),wideView!!.findViewById<Button>(R.id.widget_primary).text)
                assertEquals(View.VISIBLE,wideView!!.findViewById<View>(R.id.widget_timer).visibility)
            }
            val bitmap=ins.uiAutomation.takeScreenshot()
            File(c.getExternalFilesDir(null),"widgets-preview.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
            // Click the real RemoteViews button in the host, not a synthetic Activity action.
            ins.runOnMainSync { wideView!!.findViewById<Button>(R.id.widget_secondary).performClick() }
            waitFor("Host button must stop the shared session") { FocusWidgets.snapshot(c).timerState=="IDLE" }
            waitFor("Second widget must update after stopping in the first") { squareView!!.findViewById<Button>(R.id.widget_secondary).visibility==View.GONE }
            assertTrue(wideView!!.isAttachedToWindow)
        } finally {
            host.stopListening(); ids.forEach { host.deleteAppWidgetId(it) }; host.deleteHost()
            ins.uiAutomation.dropShellPermissionIdentity(); settings.language=old
            TimerEngine.rest(c)
            settings.saveBasic(saved.ai.provider,saved.ai.key,saved.webhook,saved.enabled,saved.target)
            settings.minutes=oldMinutes; settings.focusMode=oldMode
            ins.runOnMainSync { activity.finish() }
        }
    }
    @Test fun quickLayoutsFitThreeWidthsAndBothLanguagesAcrossAllStates() {
        val activity=ins.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val language=Settings(c).language
        val now=System.currentTimeMillis()
        val base=WidgetState("RUNNING",7,now+10_800_000,10_800_000,180,true,0,false,true)
        try {
            for(lang in UiLanguage.entries) {
                ins.runOnMainSync { LocaleHelper.change(activity,lang) }; ins.waitForIdleSync()
                for(width in listOf(164,200,300)) for(state in listOf(base,base.copy(timerState="PAUSED"),base.copy(timerState="IDLE"),base.copy(ready=false))) {
                    ins.runOnMainSync {
                        val view=FocusWidgets.renderQuick(c,124,state,now).apply(activity,FrameLayout(activity))
                        activity.setContentView(view)
                        view.measure(View.MeasureSpec.makeMeasureSpec(dp(width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dp(64),View.MeasureSpec.EXACTLY))
                        view.layout(0,0,dp(width),dp(64))
                        assertEquals(if(!state.ready) View.VISIBLE else View.GONE,view.findViewById<View>(R.id.widget_quick_setup).visibility)
                        assertEquals(if(state.ready && !state.active) View.VISIBLE else View.GONE,view.findViewById<View>(R.id.widget_quick_presets).visibility)
                        assertEquals(if(state.ready && state.active) View.VISIBLE else View.GONE,view.findViewById<View>(R.id.widget_quick_active).visibility)
                        if(state.ready && !state.active) for(id in listOf(R.id.widget_preset_25,R.id.widget_preset_35,R.id.widget_preset_45)) {
                            val button=view.findViewById<Button>(id)
                            assertTrue(button.width>=dp(48)); assertTrue(button.height>=dp(48))
                        }
                        if(state.ready && state.timerState=="PAUSED") {
                            val time=view.findViewById<TextView>(R.id.widget_time_static)
                            assertEquals("180:00",time.text.toString())
                            assertTrue(time.paint.measureText(time.text.toString())<=time.width)
                            assertTrue(time.lineHeight<=time.height)
                        }
                        if(state.ready && state.active) assertEquals(activity.getString(if(state.timerState=="PAUSED") R.string.quick_resume else R.string.quick_pause),view.findViewById<Button>(R.id.widget_primary).text)
                    }
                }
            }
        } finally { ins.runOnMainSync { LocaleHelper.change(activity,language); activity.finish() } }
    }
    @Test fun actualQuickHostStartsPresetPausesAndResumesWithoutOpeningAppOrChangingDefault() {
        val settings=Settings(c); val saved=settings.snapshot(); val minutes=settings.minutes; val mode=settings.focusMode
        TimerEngine.rest(c); Store(c).use { it.clear() }
        settings.saveBasic(Provider.DEEPSEEK,"quick-widget-test-only","https://open.feishu.cn/open-apis/bot/v2/hook/quick-widget-test-only",true,DeliveryTarget.FEISHU)
        settings.minutes=90; settings.focusMode=FocusMode.LOOP
        val activity=ins.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val host=AppWidgetHost(c,2400); val manager=AppWidgetManager.getInstance(c)
        val ids=mutableListOf<Int>(); var quick: AppWidgetHostView?=null
        try {
            ins.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            val info=manager.installedProviders.first { it.provider==ComponentName(c,FocusQuickWidget::class.java) }
            val id=host.allocateAppWidgetId(); ids.add(id)
            assertTrue(manager.bindAppWidgetIdIfAllowed(id,info.provider))
            val marker=FrameLayout(activity)
            ins.runOnMainSync {
                quick=host.createView(activity,id,info)
                marker.addView(quick,FrameLayout.LayoutParams(dp(200),dp(90)))
                activity.setContentView(marker); host.startListening()
            }
            FocusWidgets.updateAll(c)
            for((buttonId,preset) in listOf(R.id.widget_preset_25 to 25,R.id.widget_preset_35 to 35,R.id.widget_preset_45 to 45)) {
                // initialLayout is already visible before the host receives live RemoteViews.
                // Wait for the actual command binding, not just the preview's preset row.
                waitFor("Preset $preset must receive its command binding") {
                    var bound=false
                    ins.runOnMainSync {
                        val button=quick?.findViewById<Button>(buttonId)
                        bound=quick?.findViewById<View>(R.id.widget_quick_presets)?.visibility==View.VISIBLE &&
                            button?.hasOnClickListeners()==true
                    }
                    bound
                }
                ins.runOnMainSync { assertTrue("Preset $preset click must dispatch",quick!!.findViewById<Button>(buttonId).performClick()) }
                waitFor("Preset $preset should start directly") { Store(c).use { it.timer().state=="RUNNING" && it.timer().interval==preset*60_000L } }
                val first=Store(c).use { it.timer() }
                assertEquals(90,settings.minutes)
                waitFor("Quick host should show running controls") { quick?.findViewById<View>(R.id.widget_quick_active)?.visibility==View.VISIBLE }
                // Repeated stale preset commands must not restart or resize this round.
                FocusWidgets.command(c,id,WidgetAction.START_45,first.token-1).send()
                assertEquals(first,Store(c).use { it.timer() })
                ins.runOnMainSync { assertTrue("Preset $preset pause must dispatch",quick!!.findViewById<Button>(R.id.widget_primary).performClick()) }
                waitFor("Pause should execute directly") { Store(c).use { it.timer().state=="PAUSED" } }
                val paused=Store(c).use { it.timer() }
                assertEquals(first.roundId,paused.roundId)
                waitFor("Host should display Resume") { quick?.findViewById<Button>(R.id.widget_primary)?.text==LocaleHelper.wrap(c).getString(R.string.quick_resume) }
                ins.runOnMainSync { assertTrue("Preset $preset resume must dispatch",quick!!.findViewById<Button>(R.id.widget_primary).performClick()) }
                waitFor("Resume should keep the preset interval") { Store(c).use { it.timer().state=="RUNNING" && it.timer().interval==preset*60_000L } }
                assertEquals(first.roundId,Store(c).use { it.timer().roundId })
                assertEquals(90,settings.minutes)
                ins.waitForIdleSync(); assertTrue(marker.isAttachedToWindow); assertFalse(activity.isFinishing)
                TimerEngine.rest(c)
                waitFor("Quick host should return to presets") { quick?.findViewById<View>(R.id.widget_quick_presets)?.visibility==View.VISIBLE }
            }
        } finally {
            host.stopListening(); ids.forEach { host.deleteAppWidgetId(it) }; host.deleteHost()
            ins.uiAutomation.dropShellPermissionIdentity(); TimerEngine.rest(c)
            settings.saveBasic(saved.ai.provider,saved.ai.key,saved.webhook,saved.enabled,saved.target)
            settings.minutes=minutes; settings.focusMode=mode
            ins.runOnMainSync { activity.finish() }
        }
    }

}
