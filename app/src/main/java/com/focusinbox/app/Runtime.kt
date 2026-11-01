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
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) { super.onConfigurationChanged(newConfig); LocaleHelper.current(this) }
    override fun onCreate() {
        super.onCreate()
        LocaleHelper.initialize(this)
        Store(this).use { it.recover(); it.prune() }
        TimerEngine.restore(this)
        BatchEngine.resume(this)
    }
}
object BatchEngine {
    fun trigger(c: Context) {
        val id=Store(c).use { it.createBatch() } ?: return
        enqueue(c,id)
    }
    fun enqueue(c: Context,id: Long,retry: Boolean=false) {
        WorkManager.getInstance(c).enqueueUniqueWork("batch-$id",if(retry) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BatchWorker>().setInputData(workDataOf("id" to id)).build())
    }
    fun resume(c: Context) { Store(c).use { it.batches().filter { b -> Policy.mayClaim(b.state) }.forEach { b -> enqueue(c,b.id) } } }
    fun retry(c: Context,id: Long) { if(Store(c).use { it.retry(id) }) enqueue(c,id,true) }
}
class BatchWorker(c: Context,p: WorkerParameters) : Worker(c,p) {
    override fun doWork(): Result {
        val id=inputData.getLong("id",0)
        val s=Settings(applicationContext)
        var frozen: RunConfig?=null
        Store(applicationContext).use { db ->
            var b=db.get(id)?:return Result.success()
            val claimed=if(b.state=="QUEUED") db.claimAnalysis(id) else null
            if(claimed!=null) {
                try {
                    val selected=if(claimed.isEmpty()) Selection(emptySet(),"") else {
                        val config=s.snapshot(); frozen=config
                        demand(config.enabled,ErrorCode.CLOUD_DISABLED)
                        Network.analyze(config.ai,Policy.merge(claimed))
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
                    val config=frozen ?: s.snapshot()
                    demand(s.enabled && config.enabled,ErrorCode.CLOUD_DISABLED)
                    Network.send(config,b.message)
                    db.transition(id,"SENDING","DONE")
                } catch(e: Exception) {
                    val uncertain=e !is IllegalArgumentException && e !is RemoteFailure
                    db.transition(id,"SENDING",if(uncertain) "UNCERTAIN" else "SEND_FAILED",error=when {
                        uncertain -> ErrorCode.SEND_UNCERTAIN.encoded()
                        e is RemoteFailure -> e.code.encoded(e.number)
                        e is AppFailure -> e.code.encoded(e.number)
                        else -> ErrorCode.SEND_CONFIG.encoded()
                    })
                }
            }
        }
        return Result.success()
    }
}
object TimerEngine {
    private fun pending(c: Context,token: Long=0)=PendingIntent.getBroadcast(c,1,Intent(c,TimerReceiver::class.java).setData(android.net.Uri.parse("focusinbox://timer/$token")).putExtra("token",token),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun exact(c: Context): Boolean = android.os.Build.VERSION.SDK_INT<31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    @Synchronized fun start(c: Context) {
        val t=Store(c).use { it.timer() }
        if(t.state=="RUNNING") return
        val remaining=if(t.state=="PAUSED") t.remaining else Settings(c).minutes*60_000L
        val now=System.currentTimeMillis()
        val token=maxOf(now,t.token+1)
        Store(c).use { it.setTimer("RUNNING",now+remaining,remaining,token) }
        restore(c)
    }
    @Synchronized fun pause(c: Context) {
        Store(c).use { db -> val t=db.timer(); if(t.state=="RUNNING") db.setTimer("PAUSED",0,(t.end-System.currentTimeMillis()).coerceAtLeast(0),t.token) }
        cancel(c)
    }
    @Synchronized fun rest(c: Context) {
        cancel(c)
        Store(c).use { db -> db.setTimer("IDLE",0,0,System.currentTimeMillis()) }
        BatchEngine.trigger(c)
    }
    private fun cancel(c: Context) { val token=Store(c).use { it.timer().token }; c.getSystemService(AlarmManager::class.java).cancel(pending(c,token)); WorkManager.getInstance(c).cancelUniqueWork("focus-timer") }
    @Synchronized fun adjustRemaining(c: Context,minutes: Int): Boolean {
        cancel(c)
        val changed=Store(c).use { it.adjustRemaining(minutes) } ?: return false
        if(changed.state=="RUNNING") restore(c)
        return true
    }
    @Synchronized fun restore(c: Context) {
        val t=Store(c).use { it.timer() }
        if(t.state!="RUNNING") return
        val delay=(t.end-System.currentTimeMillis()).coerceAtLeast(0)
        if(delay==0L) { finish(c,t.token); return }
        val alarm=c.getSystemService(AlarmManager::class.java)
        try {
            if(exact(c)) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,t.end,pending(c,t.token))
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,t.end,pending(c,t.token))
        } catch(_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,t.end,pending(c,t.token)) }
        WorkManager.getInstance(c).enqueueUniqueWork("focus-timer",ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<TimerWorker>().setInitialDelay(delay,TimeUnit.MILLISECONDS).setInputData(workDataOf("token" to t.token)).build())
    }
    @Synchronized fun finish(c: Context,token: Long) {
        Store(c).use { it.finishAndSnapshot(token) }
        BatchEngine.resume(c)
    }
}
class TimerWorker(c: Context,p: WorkerParameters): Worker(c,p) {
    override fun doWork(): Result { TimerEngine.finish(applicationContext,inputData.getLong("token",0)); return Result.success() }
}
class TimerReceiver : BroadcastReceiver() { override fun onReceive(c: Context,i: Intent) { TimerEngine.finish(c,i.getLongExtra("token",0)) } }
class BootReceiver : BroadcastReceiver() { override fun onReceive(c: Context,i: Intent) { if(i.action==Intent.ACTION_BOOT_COMPLETED) { TimerEngine.restore(c); BatchEngine.resume(c) } } }
