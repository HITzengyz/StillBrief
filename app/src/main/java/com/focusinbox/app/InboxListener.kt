package com.focusinbox.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class InboxListener : NotificationListenerService() {
    companion object { @Volatile var connected = false; const val MARKER = NotificationParser.MARKER }
    override fun onListenerConnected() { connected=true }
    override fun onListenerDisconnected() { connected=false; requestRebind(android.content.ComponentName(this,InboxListener::class.java)) }
    override fun onDestroy() { connected=false; super.onDestroy() }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { Store(this).use { it.removeLatest(sbn.key) } }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val settings=Settings(this)
        if(sbn.packageName==packageName || sbn.packageName in settings.excluded) return
        val parsed=NotificationParser.parse(sbn.notification,sbn.packageName,android.provider.Telephony.Sms.getDefaultSmsPackage(this)) ?: return
        val label=try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName,0)).toString() } catch(_: android.content.pm.PackageManager.NameNotFoundException) { sbn.packageName }
        Store(this).use { it.insert(Notice(key=sbn.key,pkg=sbn.packageName,app=label,title=parsed.title,body=parsed.body,at=System.currentTimeMillis(),kind=parsed.kind,hidden=parsed.hidden,summary=parsed.summary,sourceTime=sbn.postTime)) }
    }
}
