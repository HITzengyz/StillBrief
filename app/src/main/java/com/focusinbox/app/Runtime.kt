package com.focusinbox.app

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import java.util.concurrent.TimeUnit

class FocusApp : Application() {
    override fun attachBaseContext(base: Context) { super.attachBaseContext(LocaleHelper.wrap(base)) }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) { super.onConfigurationChanged(newConfig); LocaleHelper.current(this); FocusWidgets.refresh(this) }
    override fun onCreate() {
        super.onCreate()
        LocaleHelper.initialize(this)
        LocalNotifier.ensureChannel(this)
        Store(this).use { it.recover(); it.prune() }
        TimerEngine.restore(this)
        BatchEngine.resume(this)
    }
}
object BatchEngine {
    fun trigger(c: Context,expectedToken: Long?=null) {
        Store(c).use { it.createBatch(expectedToken=expectedToken) }
        TimerEngine.restore(c)
    }
    fun enqueue(c: Context,id: Long,retry: Boolean=false) {
        val request=OneTimeWorkRequestBuilder<BatchWorker>().setInputData(workDataOf("id" to id))
        if(android.os.Build.VERSION.SDK_INT>=31 && Store(c).use { it.get(id)?.instantKey?.isNotEmpty()==true })
            request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        WorkManager.getInstance(c).enqueueUniqueWork("batch-$id",if(retry) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,request.build())
        FocusWidgets.refresh(c)
    }
    fun resume(c: Context) { Store(c).use { it.claimableBatches().forEach { b -> enqueue(c,b.id) } } }
    fun retry(c: Context,id: Long,target: DeliveryTarget?=null) { if(Store(c).use { it.retry(id,target) }) enqueue(c,id,true) }
}
class BatchWorker(c: Context,p: WorkerParameters) : Worker(c,p) {
    override fun doWork(): Result {
        val id=inputData.getLong("id",0)
        val s=Settings(applicationContext)
        var frozen: RunConfig?=null
        try { Store(applicationContext).use { db ->
            var b=db.get(id)?:return Result.success()
            val claimed=if(b.state=="QUEUED") db.claimAnalysis(id) else null
            if(claimed!=null) {
                FocusWidgets.refresh(applicationContext)
                try {
                    val candidates=NotificationCandidates.prepare(claimed)
                    val selected=if(candidates.isEmpty()) Selection(emptySet(),"") else {
                        val config=s.snapshot(b.target); frozen=config
                        demand(config.enabled,ErrorCode.CLOUD_DISABLED)
                        Network.analyze(config.ai,candidates)
                    }
                    db.transition(id,"ANALYZING",if(selected.ids.isEmpty()) "EMPTY" else "READY",selected.message,ids=selected.ids.joinToString(","))
                } catch(e: Exception) {
                    // No exception body or server response is logged or persisted (it may contain content/secrets).
                    val message=when(e) { is AppFailure -> e.code.encoded(e.number); is RemoteFailure -> e.code.encoded(e.number); else -> ErrorCode.ANALYSIS_FAILED.encoded() }
                    db.transition(id,"ANALYZING","FAILED",error=message)
                    return Result.success()
                }
            }
            b=db.get(id)?:return Result.success()
            if(b.state=="READY" && db.transition(id,"READY","SENDING")) {
                try {
                    val sent=if(b.instantKey.isNotEmpty()) {
                        CodeDelivery.send(b,s)
                        true
                    } else {
                        val config=frozen ?: s.snapshot(b.target)
                        demand(s.enabled && config.enabled,ErrorCode.CLOUD_DISABLED)
                        DeliveryDispatcher.send(b,config,
                            local={ batchId,message,language -> LocalNotifier.post(applicationContext,batchId,message,language) },
                            feishu={ snapshot,message -> Network.send(snapshot,message) })
                    }
                    db.transition(id,"SENDING",if(sent) "DONE" else "EMPTY")
                } catch(e: Exception) {
                    val uncertain=DeliveryDispatcher.failureState(b.target,e)=="UNCERTAIN"
                    db.transition(id,"SENDING",if(uncertain) "UNCERTAIN" else "SEND_FAILED",error=when {
                        uncertain -> ErrorCode.SEND_UNCERTAIN.encoded()
                        e is RemoteFailure -> e.code.encoded(e.number)
                        e is AppFailure -> e.code.encoded(e.number)
                        b.target==DeliveryTarget.LOCAL -> ErrorCode.LOCAL_SEND_FAILED.encoded()
                        else -> ErrorCode.SEND_CONFIG.encoded()
                    })
                }
            }
        }
        } finally { FocusWidgets.refresh(applicationContext) }
        return Result.success()
    }
}
object TimerEngine {
    private fun pending(c: Context,token: Long=0)=PendingIntent.getBroadcast(c,1,Intent(c,TimerReceiver::class.java).setData(android.net.Uri.parse("focusinbox://timer/$token")).putExtra("token",token),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun exact(c: Context): Boolean = android.os.Build.VERSION.SDK_INT<31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    private fun cancel(c: Context,token: Long) {
        c.getSystemService(AlarmManager::class.java).cancel(pending(c,token))
        WorkManager.getInstance(c).cancelUniqueWork("focus-timer-$token")
    }
    @Synchronized fun start(c: Context,durationMinutes: Int?=null) {
        val old=Store(c).use { it.timer() }
        val s=Settings(c)
        val t=Store(c).use { it.startFocus(durationMinutes ?: s.minutes,s.focusMode) }
        if(old.token!=t.token) cancel(c,old.token)
        schedule(c,t); BatchEngine.resume(c); FocusWidgets.refresh(c)
    }
    @Synchronized fun setMode(c: Context,mode: FocusMode) {
        Settings(c).focusMode=mode
        val old=Store(c).use { it.timer() }
        val current=Store(c).use { it.changeFocusMode(mode) }
        if(old.token!=current.token) cancel(c,old.token)
        schedule(c,current); BatchEngine.resume(c); FocusWidgets.refresh(c)
    }
    @Synchronized fun pause(c: Context,expectedToken: Long?=null) {
        val old=Store(c).use { it.timer() }
        Store(c).use { it.pauseFocus(expectedToken=expectedToken) }
        cancel(c,old.token)
        schedule(c,Store(c).use { it.timer() }); BatchEngine.resume(c); FocusWidgets.refresh(c)
    }
    @Synchronized fun rest(c: Context,expectedToken: Long?=null) {
        val old=Store(c).use { it.timer() }
        Store(c).use { it.stopFocus(expectedToken=expectedToken) }
        cancel(c,old.token)
        schedule(c,Store(c).use { it.timer() }); BatchEngine.resume(c); FocusWidgets.refresh(c)
    }
    @Synchronized fun adjustRemaining(c: Context,minutes: Int): Boolean {
        val old=Store(c).use { it.timer() }
        val changed=Store(c).use { it.adjustRemaining(minutes) }
        cancel(c,old.token)
        schedule(c,Store(c).use { it.timer() }); BatchEngine.resume(c); FocusWidgets.refresh(c)
        return changed!=null
    }
    @Synchronized fun restore(c: Context) {
        val old=Store(c).use { it.timer() }
        Store(c).use { it.advanceTimer() }
        val current=Store(c).use { it.timer() }
        if(old.token!=current.token) cancel(c,old.token)
        // Pre-2.0 used this one shared timer work name. It is never used for new rounds.
        WorkManager.getInstance(c).cancelUniqueWork("focus-timer")
        schedule(c,current); BatchEngine.resume(c); FocusWidgets.refresh(c)
    }
    private fun schedule(c: Context,t: Store.Timer) {
        if(t.state!="RUNNING") return
        val delay=(t.end-System.currentTimeMillis()).coerceAtLeast(0)
        val alarm=c.getSystemService(AlarmManager::class.java)
        try {
            if(exact(c)) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,t.end,pending(c,t.token))
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,t.end,pending(c,t.token))
        } catch(_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,t.end,pending(c,t.token)) }
        WorkManager.getInstance(c).enqueueUniqueWork("focus-timer-${t.token}",ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<TimerWorker>().setInitialDelay(delay,TimeUnit.MILLISECONDS).setInputData(workDataOf("token" to t.token)).build())
    }
    @Synchronized fun finish(c: Context,token: Long) {
        val changed=Store(c).use { it.advanceTimer(token) }
        if(!changed) return
        // The next round has a different unique work name; enqueueing it never cancels this worker.
        schedule(c,Store(c).use { it.timer() })
        BatchEngine.resume(c); FocusWidgets.refresh(c)
    }
}
class TimerWorker(c: Context,p: WorkerParameters): Worker(c,p) {
    override fun doWork(): Result { TimerEngine.finish(applicationContext,inputData.getLong("token",0)); return Result.success() }
}
class TimerReceiver : BroadcastReceiver() { override fun onReceive(c: Context,i: Intent) { TimerEngine.finish(c,i.getLongExtra("token",0)) } }
class BootReceiver : BroadcastReceiver() { override fun onReceive(c: Context,i: Intent) { if(i.action==Intent.ACTION_BOOT_COMPLETED) { TimerEngine.restore(c); BatchEngine.resume(c) } } }
