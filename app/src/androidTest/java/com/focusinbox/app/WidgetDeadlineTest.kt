package com.focusinbox.app

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class WidgetDeadlineTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=ins.targetContext
    private fun dp(n: Int)=(n*c.resources.displayMetrics.density).toInt()
    private fun activity()=ins.startActivitySync(Intent(c,MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    private fun expected(end: Long)=LocaleHelper.wrap(c).getString(R.string.widget_check_at,
        SimpleDateFormat("HH:mm",Locale.ROOT).format(Date(end)))
    private fun waitFor(message: String,test: ()->Boolean) {
        val until=SystemClock.uptimeMillis()+6000
        while(SystemClock.uptimeMillis()<until) { if(test()) return; SystemClock.sleep(40) }
        assertTrue(message,test())
    }

    @Test fun allSizesKeepTheCheckTimeAfterDeadlineWithoutAnyAppRefresh() {
        val a=activity()
        val now=System.currentTimeMillis()
        val state=WidgetState("RUNNING",7,now+800,0,35,true,1,false,true,"FAILED")
        val displayed=mutableListOf<View>()
        try {
            ins.runOnMainSync {
                val column=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
                listOf(FocusWidgets.render(c,1,true,state,now),FocusWidgets.render(c,2,false,state,now),FocusWidgets.renderQuick(c,3,state,now)).forEach {
                    val view=it.apply(a,column); displayed.add(view)
                    column.addView(view,LinearLayout.LayoutParams(dp(300),dp(150)))
                }
                a.setContentView(column)
            }
            ins.waitForIdleSync()
            // Deliberately pass the deadline without a producer update, as when the app is suspended.
            SystemClock.sleep(2400)
            ins.runOnMainSync {
                displayed.forEach { view ->
                    assertEquals(expected(state.end),view.findViewById<TextView>(R.id.widget_time_static).text.toString())
                    assertEquals(View.VISIBLE,view.findViewById<View>(R.id.widget_time_static).visibility)
                    assertEquals(View.GONE,view.findViewById<View>(R.id.widget_timer).visibility)
                }
            }
        } finally { ins.runOnMainSync { a.finish() } }
    }

    @Test fun upgradeReapplyStopsAnAlreadyNegativeLauncherChronometer() {
        val a=activity(); val old=Settings(c).appearance
        Settings(c).appearance=AppearanceMode.SYSTEM
        try {
            lateinit var view: View
            ins.runOnMainSync {
                val legacy=RemoteViews(c.packageName,R.layout.widget_wide).apply {
                    setViewVisibility(R.id.widget_timer,View.VISIBLE)
                    setViewVisibility(R.id.widget_time_static,View.GONE)
                    setChronometerCountDown(R.id.widget_timer,true)
                    setChronometer(R.id.widget_timer,SystemClock.elapsedRealtime()-1_490_000,null,true)
                }
                view=legacy.apply(a,FrameLayout(a)); a.setContentView(view)
                val oldText=view.findViewById<Chronometer>(R.id.widget_timer).text.toString()
                assertTrue("Fixture must reproduce the negative countdown",oldText.contains('-') || oldText.contains('\u2212'))
                val now=System.currentTimeMillis()
                val next=WidgetState("RUNNING",8,now+35*60_000L,0,35,true,0,false,true)
                FocusWidgets.render(c,1,true,next,now).reapply(a,view)
                assertEquals(expected(next.end),view.findViewById<TextView>(R.id.widget_time_static).text.toString())
                assertEquals(View.GONE,view.findViewById<View>(R.id.widget_timer).visibility)
                // Making the retained legacy child visible must not restart its ticking.
                view.findViewById<View>(R.id.widget_timer).visibility=View.VISIBLE
            }
            ins.waitForIdleSync()
            var frozen=""
            ins.runOnMainSync { frozen=view.findViewById<Chronometer>(R.id.widget_timer).text.toString() }
            SystemClock.sleep(1600)
            ins.runOnMainSync { assertEquals(frozen,view.findViewById<Chronometer>(R.id.widget_timer).text.toString()) }
        } finally { Settings(c).appearance=old; ins.runOnMainSync { a.finish() } }
    }

    @Test fun labelsFitSmallWidgetsAndRespectLocaleTimezonePauseAndExpiry() {
        val a=activity(); val s=Settings(c); val language=s.language; val timezone=TimeZone.getDefault()
        try {
            val now=System.currentTimeMillis()
            val running=WidgetState("RUNNING",7,now+35*60_000L,0,35,true,0,false,true)
            for(zone in listOf("Asia/Shanghai","America/New_York")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for(lang in UiLanguage.entries) {
                    ins.runOnMainSync { LocaleHelper.change(a,lang) }; ins.waitForIdleSync()
                    for(shape in 0..2) for(state in listOf(running,running.copy(end=now-1),running.copy(timerState="PAUSED",remaining=10_800_000))) {
                        ins.runOnMainSync {
                            val remote=when(shape) { 0 -> FocusWidgets.render(c,1,true,state,now); 1 -> FocusWidgets.render(c,2,false,state,now); else -> FocusWidgets.renderQuick(c,3,state,now) }
                            val v=remote.apply(a,FrameLayout(a)); a.setContentView(v)
                            val w=dp(when(shape) { 0 -> 250; 1 -> 150; else -> 164 }); val h=dp(if(shape==1) 150 else 64)
                            v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY)); v.layout(0,0,w,h)
                            val label=v.findViewById<TextView>(R.id.widget_time_static)
                            val expected=when { state.timerState=="PAUSED" -> "180:00"; state.end<=now -> a.getString(R.string.widget_check_due); else -> expected(state.end) }
                            assertEquals(expected,label.text.toString())
                            val available=label.width-label.compoundPaddingLeft-label.compoundPaddingRight
                            assertTrue("$lang shape=$shape '$expected' must fit without truncation",label.paint.measureText(expected)<=available)
                            assertEquals("No ellipsis may replace the check time",0,label.layout.getEllipsisCount(0))
                            assertTrue(label.lineHeight<=label.height)
                        }
                    }
                }
            }
        } finally { TimeZone.setDefault(timezone); ins.runOnMainSync { LocaleHelper.change(a,language); a.finish() } }
    }

    @Test fun refreshRecoversMissedLoopDeadlineAndOldFailureDoesNotBlockNewRound() {
        val a=activity(); val host=AppWidgetHost(c,2510); val manager=AppWidgetManager.getInstance(c)
        val ids=mutableListOf<Int>(); val views=mutableListOf<AppWidgetHostView>()
        TimerEngine.rest(c); Store(c).use { it.clear() }
        try {
            ins.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            val providers=listOf(FocusWideWidget::class.java,FocusSquareWidget::class.java)
            val column=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
            for(type in providers) {
                val info=manager.installedProviders.first { it.provider==ComponentName(c,type) }
                val id=host.allocateAppWidgetId(); ids.add(id)
                assertTrue(manager.bindAppWidgetIdIfAllowed(id,info.provider))
                ins.runOnMainSync {
                    val view=host.createView(a,id,info); views.add(view); column.addView(view,LinearLayout.LayoutParams(dp(300),dp(170)))
                }
            }
            ins.runOnMainSync { a.setContentView(column); host.startListening() }
            FocusWidgets.updateAll(c); ins.waitForIdleSync()
            val now=System.currentTimeMillis()
            lateinit var expired: Store.Timer
            var failed=0L
            // No TimerEngine.start: deliberately omit the alarm to reproduce a missed background wake-up.
            synchronized(TimerEngine) {
                Store(c).use { db ->
                    db.stopFocus(); expired=db.startFocus(35,FocusMode.LOOP,now-60*60_000L)
                    assertTrue(db.insert(Notice(key="failed-round",pkg="chat",app="Chat",title="Friend",body="synthetic only",at=expired.start+1000,kind="private")))
                    failed=db.createBatch(expired.start+2000)!!
                    assertNotNull(db.claimAnalysis(failed)); assertTrue(db.transition(failed,"ANALYZING","FAILED",error="@error:CLOUD_DISABLED"))
                }
                FocusWidgets.updateAll(c)
            }
            val current=Store(c).use { it.timer() }
            assertEquals("RUNNING",current.state); assertTrue(current.token>expired.token)
            assertEquals(expired.end,current.start); assertEquals(expired.end+35*60_000L,current.end)
            waitFor("Both hosts must display the new round deadline") {
                var match=false
                ins.runOnMainSync { match=views.all { it.findViewById<TextView>(R.id.widget_time_static)?.text?.toString()==expected(current.end) } }
                match
            }
            TimerEngine.finish(c,expired.token)
            assertEquals(current,Store(c).use { it.timer() })
            assertEquals("FAILED",Store(c).use { it.get(failed)!!.state })
        } finally {
            host.stopListening(); ids.forEach { host.deleteAppWidgetId(it) }; host.deleteHost()
            ins.uiAutomation.dropShellPermissionIdentity(); TimerEngine.rest(c); Store(c).use { it.clear() }
            ins.runOnMainSync { a.finish() }
        }
    }
}
