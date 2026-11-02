package com.focusinbox.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VerificationCodeTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName="codes-test.db"
    private val prefsName="codes-test"
    private lateinit var db: Store
    private lateinit var settings: Settings
    @Before fun setup() {
        context.deleteDatabase(dbName); context.deleteSharedPreferences(prefsName)
        db=Store(context,dbName,emptySet()); settings=Settings(context,prefsName)
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(dbName); context.deleteSharedPreferences(prefsName) }
    private fun notice(at: Long=2000)=Notice(key="sms1",pkg="sms",app="短信",title="106900",body="【示例服务】验证码：123456，5分钟内有效",at=at,kind="sms",sourceTime=at)
    private fun enable() { settings.saveBasic(Provider.DEEPSEEK,"","https://open.feishu.cn/open-apis/bot/v2/hook/example",false,DeliveryTarget.FEISHU,true) }
    @Test fun immediateBatchWorksOutsideFocusAndDoesNotEnterLaterSummary() {
        val before=db.timer()
        val id=db.queueCode(notice(),"123456",UiLanguage.CHINESE)!!
        val batch=db.get(id)!!
        assertEquals(before,db.timer()); assertEquals("READY",batch.state)
        assertEquals(DeliveryTarget.FEISHU,batch.target); assertTrue(batch.message.contains("123456"))
        assertEquals(1,db.notices(id).size)
        db.startFocus(35,FocusMode.LOOP,3000)
        assertEquals(0L,db.pendingCount()); assertNull(db.createBatch(4000))
        assertEquals(1,db.batches().size)
    }
    @Test fun focusedAndPausedTimersAreUnchangedAndDuplicatesStayOneBatch() {
        val before=db.startFocus(35,FocusMode.LOOP,1000)
        val id=db.queueCode(notice(),"123456",UiLanguage.CHINESE)!!
        assertEquals(before,db.timer()); assertEquals(0L,db.pendingCount())
        db.transition(id,"READY","SENDING"); db.transition(id,"SENDING","DONE")
        assertEquals(id,db.queueCode(notice(3000).copy(key="updated"),"123456",UiLanguage.CHINESE))
        assertEquals(1,db.batches().size)
        val paused=db.pauseFocus(5000)
        db.queueCode(notice(6000).copy(body="验证码654321"),"654321",UiLanguage.CHINESE)
        assertEquals(paused,db.timer()); assertEquals(0L,db.pendingCount())
        assertTrue(db.feishuEchoes().single().contains("123456"))
        db.transition(id,"DONE","SEND_FAILED")
        assertFalse(db.retry(id,DeliveryTarget.LOCAL))
    }
    @Test fun filtersAndHiddenOrSummaryNotificationsDoNotGetForwarded() {
        assertNull(db.queueCode(notice().copy(hidden=true),"123456",UiLanguage.CHINESE))
        assertNull(db.queueCode(notice().copy(summary=true),"123456",UiLanguage.CHINESE))
        db.addFilter("app","sms","短信")
        assertNull(db.queueCode(notice(),"123456",UiLanguage.CHINESE)); assertTrue(db.batches().isEmpty())
    }
    @Test fun sendsWithoutAiKeyButSavedLocalDestinationStopsQueuedDelivery() {
        enable()
        assertFalse(settings.enabled); assertTrue(Settings(context,prefsName).forwardCodes)
        val batch=db.get(db.queueCode(notice(),"123456",UiLanguage.CHINESE)!!)!!
        var sends=0
        val sender: (RunConfig,String)->Unit={ config,message ->
            sends++; assertEquals(DeliveryTarget.FEISHU,config.target); assertEquals("",config.ai.key)
            assertTrue(message.contains("123456"))
        }
        context.getSharedPreferences(prefsName,0).edit().putString("provider.deepseek.key","unreadable-key").commit()
        CodeDelivery.send(batch,settings,3000,sender); assertEquals(1,sends)
        settings.saveBasic(Provider.DEEPSEEK,"","",false,DeliveryTarget.LOCAL)
        try { CodeDelivery.send(batch,settings,4000,sender); fail("Local must not send") }
        catch(e: AppFailure) { assertEquals(ErrorCode.CODES_DISABLED,e.code) }
        assertEquals(1,sends)
        enable(); settings.forwardCodes=false
        try { CodeDelivery.send(batch,settings,5000,sender); fail("Disabled must not send") }
        catch(e: AppFailure) { assertEquals(ErrorCode.CODES_DISABLED,e.code) }
        enable()
        try { CodeDelivery.send(batch,settings,602000,sender); fail("Expired must not send") }
        catch(e: AppFailure) { assertEquals(ErrorCode.CODE_EXPIRED,e.code) }
        assertEquals(1,sends)
        settings.clearSecrets(); assertFalse(settings.forwardCodes)
    }
    @Test fun delayedArrivalDoesNotExtendExpiryAndActiveNotificationRefreshDoesNotResend() {
        enable()
        val id=db.queueCode(notice(541000).copy(sourceTime=1000),"123456",UiLanguage.CHINESE)!!
        assertEquals(1000L,db.get(id)!!.created)
        try { CodeDelivery.send(db.get(id)!!,settings,601000) { _,_ -> fail("Must not send expired code") }; fail("Expected expiry") }
        catch(e: AppFailure) { assertEquals(ErrorCode.CODE_EXPIRED,e.code) }
        db.startFocus(35,FocusMode.LOOP,602000)
        assertEquals(id,db.queueCode(notice(603000),"123456",UiLanguage.CHINESE))
        db.removeLatest("sms1")
        assertNotEquals(id,db.queueCode(notice(604000),"123456",UiLanguage.CHINESE))
    }
    @Test fun messagingHistoryUsesOnlyNewestMessageForImmediateCode() {
        val n=android.app.Notification().apply {
            extras=android.os.Bundle().apply {
                putString(android.app.Notification.EXTRA_TITLE,"106900")
                putString(android.app.Notification.EXTRA_BIG_TEXT,"验证码123456\n验证码654321")
                putParcelableArray(android.app.Notification.EXTRA_MESSAGES,arrayOf(
                    android.os.Bundle().apply { putCharSequence("text","验证码123456") },
                    android.os.Bundle().apply { putCharSequence("text","验证码654321") }
                ))
            }
        }
        val regular=NotificationParser.parse(n,"sms","sms")!!
        assertTrue(regular.body.contains("123456")); assertTrue(regular.body.contains("654321"))
        val latest=NotificationParser.parse(n,"sms","sms",latestOnly=true)!!
        assertEquals("654321",VerificationCodes.extract(latest.title,latest.body))
        assertFalse(latest.body.contains("123456"))
        n.extras.putParcelableArray(android.app.Notification.EXTRA_MESSAGES,arrayOf(
            android.os.Bundle().apply { putCharSequence("text","验证码123456") },
            android.os.Bundle().apply { putCharSequence("text","登录成功") }
        ))
        val success=NotificationParser.parse(n,"sms","sms",latestOnly=true)!!
        assertNull(VerificationCodes.extract(success.title,success.body))
    }
    @Test fun upgradeFromV4PreservesRunningRoundAndHistory() {
        val original=db.startFocus(35,FocusMode.LOOP,1000)
        db.insert(notice()); val history=db.createBatch(3000)!!
        db.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path,null,SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE code_notifications")
            it.execSQL("ALTER TABLE batches RENAME TO batches_v5")
            it.execSQL("CREATE TABLE batches(id INTEGER PRIMARY KEY AUTOINCREMENT,state TEXT NOT NULL,created INTEGER NOT NULL,message TEXT NOT NULL DEFAULT '',error TEXT NOT NULL DEFAULT '',ids TEXT NOT NULL DEFAULT '',target TEXT NOT NULL DEFAULT 'FEISHU',roundId INTEGER NOT NULL DEFAULT 0)")
            it.execSQL("INSERT INTO batches SELECT id,state,created,message,error,ids,target,roundId FROM batches_v5")
            it.execSQL("DROP TABLE batches_v5"); it.version=4
        }
        db=Store(context,dbName,emptySet())
        assertEquals(5,db.readableDatabase.version); assertEquals(original,db.timer())
        assertEquals("QUEUED",db.get(history)!!.state); assertEquals("",db.get(history)!!.instantKey)
        assertEquals(1,db.notices(history).size)
        assertNotNull(db.queueCode(notice(4000),"123456",UiLanguage.CHINESE))
    }
}
