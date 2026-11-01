package com.focusinbox.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalNotifierTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    @Before fun grantPermissionOnDedicatedTestDevice() {
        // Test fixture only; production requests permission exclusively after a user button click.
        if(Build.VERSION.SDK_INT>=33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.POST_NOTIFICATIONS)
        LocalNotifier.ensureChannel(context)
    }
    @Test @Suppress("DEPRECATION") fun notificationPermissionIsDeclaredWithoutDndPolicyAccess() {
        val permissions=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(permissions.contains(Manifest.permission.POST_NOTIFICATIONS))
        assertFalse(permissions.contains(Manifest.permission.ACCESS_NOTIFICATION_POLICY))
    }
    @Test fun missingPermissionAppBlockAndChannelBlockAllPreventPosting() {
        val cases=listOf(
            LocalNotifier.evaluate(false,true,NotificationManager.IMPORTANCE_HIGH) to LocalNoticeStatus.PERMISSION_MISSING,
            LocalNotifier.evaluate(true,false,NotificationManager.IMPORTANCE_HIGH) to LocalNoticeStatus.APP_BLOCKED,
            LocalNotifier.evaluate(true,true,NotificationManager.IMPORTANCE_NONE) to LocalNoticeStatus.CHANNEL_BLOCKED)
        cases.forEach { (actual,expected) ->
            assertEquals(expected,actual); var calls=0
            val error=assertThrows(AppFailure::class.java) {
                LocalNotifier.post(context,5,"Synthetic summary",UiLanguage.ENGLISH,{ actual },{ _,_,_ -> calls++ })
            }
            assertEquals(ErrorCode.LOCAL_NOTIFICATIONS_DISABLED,error.code); assertEquals(0,calls)
        }
    }
    @Test fun emptySummaryDoesNotEvenReadPermissionOrPublish() {
        var checks=0; var posts=0
        assertFalse(LocalNotifier.post(context,5,"  ",UiLanguage.ENGLISH,{ checks++; LocalNoticeStatus.READY },{ _,_,_ -> posts++ }))
        assertEquals(0,checks); assertEquals(0,posts)
    }
    @Test fun sameBatchUpdatesOneIdentityAndProtectsLockScreenContent() {
        val sent=mutableListOf<Triple<String,Int,Notification>>()
        repeat(2) { LocalNotifier.post(context,4294967297,"Private synthetic summary",UiLanguage.ENGLISH,{ LocalNoticeStatus.READY },{ tag,id,n -> sent.add(Triple(tag,id,n)) }) }
        assertEquals(2,sent.size); assertEquals(sent[0].first,sent[1].first); assertEquals(sent[0].second,sent[1].second)
        val notice=sent.first().third
        assertEquals(Notification.VISIBILITY_PRIVATE,notice.visibility)
        assertTrue(notice.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals("Private synthetic summary",notice.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
        assertNotNull(notice.publicVersion)
        assertFalse(notice.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Private synthetic"))
        assertEquals(LocalNotifier.CHANNEL_ID,notice.channelId)
    }
    @Test fun testNotificationsCanAlertOnEveryClickAndUseSeparateIdentity() {
        var captured: Notification?=null; var tag=""; var id=0
        LocalNotifier.post(context,null,"Synthetic test",UiLanguage.ENGLISH,{ LocalNoticeStatus.READY },{ t,i,n -> tag=t; id=i; captured=n })
        assertEquals(LocalNotifier.TEST_TAG,tag); assertEquals(LocalNotifier.TEST_NOTIFICATION_ID,id)
        assertFalse(captured!!.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertNotEquals(LocalNotifier.batchTag(1),tag)
    }
    @Test fun longBatchIdsDoNotCollideAndOpenTheMatchingDetail() {
        val a=1L; val b=1L+(1L shl 32)
        assertNotEquals(LocalNotifier.batchTag(a),LocalNotifier.batchTag(b))
        val first=LocalNotifier.openIntent(context,a); val second=LocalNotifier.openIntent(context,b)
        assertFalse(first.filterEquals(second))
        assertEquals(a,LocalNotifier.batchFromIntent(first)); assertEquals(b,LocalNotifier.batchFromIntent(second))
        assertTrue(second.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(second.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertNull(LocalNotifier.batchFromIntent(Intent(second).putExtra(LocalNotifier.EXTRA_BATCH_ID,a)))
        assertNull(LocalNotifier.batchFromIntent(Intent(second).setAction(Intent.ACTION_VIEW)))
    }
    @Test fun permissionRevokedDuringPostDoesNotReportSuccess() {
        var check=0; var posts=0
        val error=assertThrows(AppFailure::class.java) {
            LocalNotifier.post(context,9,"Synthetic",UiLanguage.ENGLISH,{ if(check++==0) LocalNoticeStatus.READY else LocalNoticeStatus.PERMISSION_MISSING },{ _,_,_ -> posts++ })
        }
        assertEquals(1,posts); assertEquals(ErrorCode.LOCAL_NOTIFICATIONS_DISABLED,error.code)
    }
    @Test fun actualNotificationIsPublishedAndOwnPackageCannotBeCollected() {
        val manager=context.getSystemService(NotificationManager::class.java)
        val batch=9223372036854000000L
        val tag=LocalNotifier.batchTag(batch)
        try {
            assertEquals(LocalNoticeStatus.READY,LocalNotifier.status(context))
            assertTrue(LocalNotifier.post(context,batch,"First synthetic reminder",UiLanguage.ENGLISH))
            assertTrue(LocalNotifier.post(context,batch,"Actual synthetic local reminder",UiLanguage.ENGLISH))
            val deadline=android.os.SystemClock.uptimeMillis()+3000
            var active=manager.activeNotifications.filter { it.tag==tag && it.id==LocalNotifier.BATCH_NOTIFICATION_ID }
            while((active.size!=1 || active.first().notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()!="Actual synthetic local reminder") && android.os.SystemClock.uptimeMillis()<deadline) {
                android.os.SystemClock.sleep(30)
                active=manager.activeNotifications.filter { it.tag==tag && it.id==LocalNotifier.BATCH_NOTIFICATION_ID }
            }
            val posted=active.single()
            assertEquals(context.packageName,posted.packageName)
            assertEquals("Actual synthetic local reminder",posted.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
            assertFalse(InboxListener.shouldCollectPackage(posted.packageName,context.packageName))
            assertTrue(InboxListener.shouldCollectPackage("other.app",context.packageName))
        } finally { manager.cancel(tag,LocalNotifier.BATCH_NOTIFICATION_ID) }
    }
    @Test fun dndSettingsFallbackIsStandardSoundThenSystemSettings() {
        val actions=mutableListOf<String?>()
        assertTrue(LocalNotifier.openDndSettings { intent ->
            actions.add(intent.action)
            when(actions.size) { 1 -> throw ActivityNotFoundException("Synthetic"); 2 -> throw SecurityException("Synthetic"); else -> Unit }
        })
        assertEquals(listOf("android.settings.ZEN_MODE_SETTINGS","android.settings.SOUND_SETTINGS","android.settings.SETTINGS"),actions)
    }
}
