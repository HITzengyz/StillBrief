package com.focusinbox.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class InboxListener : NotificationListenerService() {
    companion object { @Volatile var connected = false; const val MARKER = NotificationParser.MARKER
        fun shouldCollectPackage(source: String,own: String) = source!=own
    }
    override fun onListenerConnected() { connected=true }
    override fun onListenerDisconnected() { connected=false; requestRebind(android.content.ComponentName(this,InboxListener::class.java)) }
    override fun onDestroy() { connected=false; super.onDestroy() }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { Store(this).use { it.removeLatest(sbn.key) } }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if(!shouldCollectPackage(sbn.packageName,packageName)) return
        var timerChanged=false
        Store(this).use { db ->
            val before=db.timer()
            val settings=Settings(this)
            if(before.state=="IDLE" && !settings.codesActive) return
            val echoes=if(NotificationParser.isFeishu(sbn.packageName)) db.feishuEchoes() else emptyList()
            val parsed=NotificationParser.parse(sbn.notification,sbn.packageName,android.provider.Telephony.Sms.getDefaultSmsPackage(this),echoes) ?: return
            val label=try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName,0)).toString() } catch(_: android.content.pm.PackageManager.NameNotFoundException) { sbn.packageName }
            val notice=Notice(key=sbn.key,pkg=sbn.packageName,app=label,title=parsed.title,body=parsed.body,at=System.currentTimeMillis(),kind=parsed.kind,hidden=parsed.hidden,summary=parsed.summary,sourceTime=sbn.postTime,ongoing=sbn.isOngoing,channelId=sbn.notification.channelId.orEmpty())
            val latest=if(settings.codesActive) NotificationParser.parse(sbn.notification,sbn.packageName,android.provider.Telephony.Sms.getDefaultSmsPackage(this),echoes,latestOnly=true) else null
            val codeNotice=latest?.let { notice.copy(title=it.title,body=it.body,hidden=it.hidden,sourceTime=it.sourceTime.takeIf { time -> time>0 } ?: sbn.postTime) }
            val code=if(codeNotice!=null && settings.codesActive && !codeNotice.hidden && !codeNotice.summary && VerificationCodes.fresh(codeNotice.sourceTime,notice.at)) VerificationCodes.extract(codeNotice.title,codeNotice.body) else null
            val immediate=if(code!=null && codeNotice!=null) db.queueCode(codeNotice,code,settings.language) else null
            if(immediate!=null) BatchEngine.enqueue(this,immediate) else db.collect(notice)
            timerChanged=db.timer().token!=before.token
        }
        if(timerChanged) TimerEngine.restore(this)
        FocusWidgets.refresh(this)
    }
}
