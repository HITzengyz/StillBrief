package com.focusinbox.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings as AndroidSettings
import java.util.Locale

enum class LocalNoticeStatus { READY, PERMISSION_MISSING, APP_BLOCKED, CHANNEL_BLOCKED }

object LocalNotifier {
    const val CHANNEL_ID="summary_reminders_v1"
    const val ACTION_OPEN_BATCH="com.focusinbox.app.OPEN_BATCH"
    const val EXTRA_BATCH_ID="batch_id"
    const val BATCH_NOTIFICATION_ID=1
    const val TEST_NOTIFICATION_ID=2
    const val TEST_TAG="stillbrief.test"
    fun batchTag(batchId: Long)="stillbrief.batch.$batchId"
    private fun localized(context: Context,language: UiLanguage): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(language.tag))) })
    fun ensureChannel(context: Context) {
        val text=LocaleHelper.wrap(context)
        val channel=NotificationChannel(CHANNEL_ID,text.getString(R.string.local_channel_name),NotificationManager.IMPORTANCE_HIGH).apply {
            description=text.getString(R.string.local_channel_description)
            lockscreenVisibility=Notification.VISIBILITY_PRIVATE
            enableVibration(true)
        }
        // Existing user importance/sound/DND choices are retained by Android.
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    fun evaluate(permission: Boolean,appEnabled: Boolean,importance: Int): LocalNoticeStatus = when {
        !permission -> LocalNoticeStatus.PERMISSION_MISSING
        !appEnabled -> LocalNoticeStatus.APP_BLOCKED
        importance==NotificationManager.IMPORTANCE_NONE -> LocalNoticeStatus.CHANNEL_BLOCKED
        else -> LocalNoticeStatus.READY
    }
    fun status(context: Context): LocalNoticeStatus {
        ensureChannel(context)
        val manager=context.getSystemService(NotificationManager::class.java)
        val permission=Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
        return evaluate(permission,manager.areNotificationsEnabled(),manager.getNotificationChannel(CHANNEL_ID).importance)
    }
    fun openIntent(context: Context,batchId: Long): Intent = Intent(context,MainActivity::class.java)
        .setAction(ACTION_OPEN_BATCH).setData(Uri.parse("stillbrief://batch/$batchId")).putExtra(EXTRA_BATCH_ID,batchId)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    fun batchFromIntent(intent: Intent?): Long? {
        if(intent?.action!=ACTION_OPEN_BATCH || intent.data?.scheme!="stillbrief" || intent.data?.host!="batch") return null
        val id=intent.data?.lastPathSegment?.toLongOrNull() ?: return null
        return if(id>0 && intent.getLongExtra(EXTRA_BATCH_ID,0)==id) id else null
    }
    private fun notification(context: Context,batchId: Long?,message: String,language: UiLanguage): Notification {
        val text=localized(context,language)
        val open=if(batchId!=null) openIntent(context,batchId) else Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val click=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public=Notification.Builder(text,CHANNEL_ID).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text.getString(R.string.app_name)).setContentText(text.getString(R.string.local_public_text))
            .setVisibility(Notification.VISIBILITY_PUBLIC).build()
        return Notification.Builder(text,CHANNEL_ID).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text.getString(if(batchId==null) R.string.local_test_title else R.string.local_summary_title))
            .setContentText(message).setStyle(Notification.BigTextStyle().bigText(message))
            .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setOnlyAlertOnce(batchId!=null).setAutoCancel(true).setContentIntent(click).build()
    }
    /** Defaults perform a real post; injectable gates/post keep denied-path tests off user settings. */
    fun post(context: Context,batchId: Long?,message: String,language: UiLanguage,
        check: ()->LocalNoticeStatus={ status(context) },
        publish: (String,Int,Notification)->Unit={ tag,id,notice -> context.getSystemService(NotificationManager::class.java).notify(tag,id,notice) }): Boolean {
        if(message.isBlank()) return false
        demand(check()==LocalNoticeStatus.READY,ErrorCode.LOCAL_NOTIFICATIONS_DISABLED)
        val notice=notification(context,batchId,message,language)
        try { publish(if(batchId==null) TEST_TAG else batchTag(batchId),if(batchId==null) TEST_NOTIFICATION_ID else BATCH_NOTIFICATION_ID,notice) }
        catch(_: SecurityException) { throw AppFailure(ErrorCode.LOCAL_NOTIFICATIONS_DISABLED) }
        demand(check()==LocalNoticeStatus.READY,ErrorCode.LOCAL_NOTIFICATIONS_DISABLED)
        return true
    }
    fun sendTest(context: Context): Boolean {
        val language=Settings(context).language
        return post(context,null,localized(context,language).getString(R.string.local_test_body),language)
    }
    fun appSettings(context: Context)=Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE,context.packageName)
    fun channelSettings(context: Context)=Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE,context.packageName).putExtra(AndroidSettings.EXTRA_CHANNEL_ID,CHANNEL_ID)
    fun openDndSettings(start: (Intent)->Unit): Boolean {
        for(action in listOf("android.settings.ZEN_MODE_SETTINGS",AndroidSettings.ACTION_SOUND_SETTINGS,AndroidSettings.ACTION_SETTINGS)) {
            try { start(Intent(action)); return true }
            catch(_: ActivityNotFoundException) { }
            catch(_: SecurityException) { }
        }
        return false
    }
}
