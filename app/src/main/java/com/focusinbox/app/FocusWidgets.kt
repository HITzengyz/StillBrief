package com.focusinbox.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import java.util.concurrent.Executors

/** The launcher owns the ticking Chronometer; only real state changes refresh RemoteViews. */
object FocusWidgets {
    const val OPEN_ACTION="com.focusinbox.app.widget.OPEN"
    const val COMMAND_ACTION="com.focusinbox.app.widget.COMMAND"
    private val handler=Handler(Looper.getMainLooper())
    private var pendingRefresh=false
    private val executor=Executors.newSingleThreadExecutor()
    fun execute(task: ()->Unit) { executor.execute(task) }
    fun refresh(context: Context) {
        synchronized(this) {
            if(pendingRefresh) return
            pendingRefresh=true
        }
        val app=context.applicationContext
        handler.postDelayed({
            synchronized(this) { pendingRefresh=false }
            execute { updateAll(app) }
        },150)
    }
    fun snapshot(context: Context): WidgetState {
        val s=Settings(context)
        // A corrupted/revoked local credential should expose setup, not break the home screen.
        val ready=try {
            val config=s.snapshot()
            config.enabled && config.ai.key.isNotBlank() && config.ai.model.isNotBlank() && Network.validUrl(config.ai.endpoint) &&
                if(config.target==DeliveryTarget.FEISHU) Network.validUrl(config.webhook,true) else LocalNotifier.status(context)==LocalNoticeStatus.READY
        } catch(_: Exception) { false }
        return Store(context).use { db ->
            val t=db.timer()
            val recent=db.batches().filter { it.instantKey.isEmpty() }
            val busy=recent.any { it.state in setOf("QUEUED","ANALYZING","READY","SENDING") }
            WidgetState(t.state,t.token,t.end,t.remaining,s.minutes,
                (if(t.state=="IDLE") s.focusMode else t.mode)==FocusMode.LOOP,db.pendingCount(),busy,ready,recent.firstOrNull()?.state.orEmpty())
        }
    }
    fun openIntent(context: Context,page: Int=0)=Intent(context,MainActivity::class.java)
        .setAction(OPEN_ACTION).setData(Uri.parse("stillbrief://widget/open/$page"))
        .putExtra("widget_page",page).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    private fun open(context: Context,page: Int)=PendingIntent.getActivity(context,page,openIntent(context,page),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun command(context: Context,id: Int,action: WidgetAction,token: Long): PendingIntent = PendingIntent.getBroadcast(context,id,
        Intent(context,WidgetActionReceiver::class.java).setAction(COMMAND_ACTION)
            .setData(Uri.parse("stillbrief://widget/$id/${action.name}/$token"))
            .putExtra("widget_action",action.name).putExtra("widget_token",token),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun perform(context: Context,action: WidgetAction,token: Long) {
        // Serialize the deadline catch-up, stale-button check and action with in-app timer controls.
        synchronized(TimerEngine) {
            TimerEngine.restore(context)
            if(snapshot(context).allows(action,token)) when(action) {
                WidgetAction.START,WidgetAction.RESUME -> TimerEngine.start(context)
                WidgetAction.START_25 -> TimerEngine.start(context,25)
                WidgetAction.START_35 -> TimerEngine.start(context,35)
                WidgetAction.START_45 -> TimerEngine.start(context,45)
                WidgetAction.PAUSE -> TimerEngine.pause(context,token)
                WidgetAction.ANALYZE -> BatchEngine.trigger(context,token)
                WidgetAction.END -> TimerEngine.rest(context,token)
                WidgetAction.SETUP -> Unit
            }
        }
        updateAll(context)
    }
    fun updateAll(context: Context) {
        val manager=AppWidgetManager.getInstance(context)
        val wide=manager.getAppWidgetIds(ComponentName(context,FocusWideWidget::class.java))
        val square=manager.getAppWidgetIds(ComponentName(context,FocusSquareWidget::class.java))
        val quick=manager.getAppWidgetIds(ComponentName(context,FocusQuickWidget::class.java))
        if(wide.isEmpty() && square.isEmpty() && quick.isEmpty()) return
        val state=snapshot(context)
        for(id in wide) manager.updateAppWidget(id,render(context,id,true,state))
        for(id in square) manager.updateAppWidget(id,render(context,id,false,state))
        for(id in quick) manager.updateAppWidget(id,renderQuick(context,id,state))
    }
    fun render(context: Context,id: Int,wide: Boolean,state: WidgetState,now: Long=System.currentTimeMillis(),elapsed: Long=SystemClock.elapsedRealtime()): RemoteViews {
        val c=LocaleHelper.wrap(context)
        val views=RemoteViews(context.packageName,if(wide) R.layout.widget_wide else R.layout.widget_square)
        bindTime(context,views,state,now,elapsed)
        views.setTextViewText(R.id.widget_mode,c.getString(if(state.loop) R.string.widget_loop else R.string.widget_single))
        if(!wide) views.setTextViewText(R.id.widget_brand,c.getString(R.string.app_name))
        val failed=state.lastState in setOf("FAILED","SEND_FAILED","UNCERTAIN")
        val status=when {
            !state.ready -> R.string.widget_setup_hint
            state.busy -> R.string.widget_analyzing
            failed -> R.string.widget_failed
            state.timerState=="PAUSED" -> R.string.widget_paused
            state.timerState=="RUNNING" -> R.string.widget_focusing
            state.lastState=="DONE" -> R.string.widget_delivered
            state.lastState=="EMPTY" -> R.string.widget_quiet
            else -> R.string.widget_idle
        }
        views.setTextViewText(R.id.widget_status,c.getString(status))
        val statusPage=when(status) { R.string.widget_setup_hint -> 2; R.string.widget_failed -> 1; else -> 0 }
        views.setOnClickPendingIntent(R.id.widget_status,open(context,statusPage))
        val primary=when(state.primary) {
            WidgetAction.START -> R.string.widget_start
            WidgetAction.RESUME -> R.string.widget_resume
            WidgetAction.ANALYZE -> if(state.busy) R.string.widget_analyzing else R.string.widget_analyze
            else -> R.string.widget_setup
        }
        views.setTextViewText(R.id.widget_primary,c.getString(primary))
        views.setBoolean(R.id.widget_primary,"setEnabled",state.primaryEnabled)
        views.setFloat(R.id.widget_primary,"setAlpha",if(state.primaryEnabled) 1f else 0.45f)
        views.setOnClickPendingIntent(R.id.widget_primary,if(state.primary==WidgetAction.SETUP) open(context,2) else command(context,id,state.primary,state.token))
        views.setViewVisibility(R.id.widget_secondary,if(state.active) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_secondary,c.getString(if(wide) R.string.widget_end_compact else R.string.widget_end))
        views.setOnClickPendingIntent(R.id.widget_secondary,command(context,id,WidgetAction.END,state.token))
        return views
    }
    private fun bindTime(context: Context,views: RemoteViews,state: WidgetState,now: Long,elapsed: Long) {
        val c=LocaleHelper.wrap(context)
        val left=state.remainingAt(now)
        val ticking=state.timerState=="RUNNING" && left>0
        views.setViewVisibility(R.id.widget_timer,if(ticking) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_time_static,if(ticking) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_time_static,WidgetState.time(left))
        views.setChronometerCountDown(R.id.widget_timer,true)
        views.setChronometer(R.id.widget_timer,elapsed+left,null,ticking)
        views.setOnClickPendingIntent(R.id.widget_time_area,open(context,0))
        views.setContentDescription(R.id.widget_time_area,c.getString(R.string.widget_open_focus))
    }
    fun renderQuick(context: Context,id: Int,state: WidgetState,now: Long=System.currentTimeMillis(),elapsed: Long=SystemClock.elapsedRealtime()): RemoteViews {
        val c=LocaleHelper.wrap(context)
        val views=RemoteViews(context.packageName,R.layout.widget_quick)
        views.setViewVisibility(R.id.widget_quick_presets,if(state.ready && !state.active) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_quick_active,if(state.ready && state.active) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_quick_setup,if(state.ready) View.GONE else View.VISIBLE)
        for((viewId,minutes,action) in listOf(
            Triple(R.id.widget_preset_25,25,WidgetAction.START_25),
            Triple(R.id.widget_preset_35,35,WidgetAction.START_35),
            Triple(R.id.widget_preset_45,45,WidgetAction.START_45))) {
            views.setContentDescription(viewId,c.getString(R.string.quick_preset_description,minutes))
            views.setOnClickPendingIntent(viewId,command(context,id,action,state.token))
        }
        bindTime(context,views,state,now,elapsed)
        val paused=state.timerState=="PAUSED"
        views.setTextViewText(R.id.widget_primary,c.getString(if(paused) R.string.quick_resume else R.string.quick_pause))
        views.setOnClickPendingIntent(R.id.widget_primary,command(context,id,if(paused) WidgetAction.RESUME else WidgetAction.PAUSE,state.token))
        views.setTextViewText(R.id.widget_quick_setup,c.getString(R.string.quick_setup))
        views.setOnClickPendingIntent(R.id.widget_quick_setup,open(context,2))
        return views
    }

}

open class FocusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) {
        val result=goAsync()
        FocusWidgets.execute { try { TimerEngine.restore(context); FocusWidgets.updateAll(context) } finally { result.finish() } }
    }
    override fun onAppWidgetOptionsChanged(context: Context,manager: AppWidgetManager,id: Int,options: Bundle) { FocusWidgets.refresh(context) }
}
class FocusWideWidget : FocusWidgetProvider()
class FocusSquareWidget : FocusWidgetProvider()
class FocusQuickWidget : FocusWidgetProvider()

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val action=if(intent.action==FocusWidgets.COMMAND_ACTION)
            WidgetAction.entries.firstOrNull { it.name==intent.getStringExtra("widget_action") } ?: return else null
        if(action==null && intent.action !in setOf(Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_LOCALE_CHANGED,Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val result=goAsync()
        FocusWidgets.execute {
            try {
                if(action!=null) FocusWidgets.perform(context,action,intent.getLongExtra("widget_token",-1))
                else { TimerEngine.restore(context); FocusWidgets.updateAll(context) }
            } finally { result.finish() }
        }
    }
}
