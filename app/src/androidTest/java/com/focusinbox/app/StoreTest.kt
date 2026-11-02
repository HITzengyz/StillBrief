package com.focusinbox.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class StoreTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: Store
    @Before fun setUp() { context.deleteDatabase("inbox-instrumentation-test.db"); db=Store(context,"inbox-instrumentation-test.db",emptySet()) { DeliveryTarget.FEISHU }; db.startFocus(25,FocusMode.SINGLE) }
    @After fun tearDown() { db.close(); context.deleteDatabase("inbox-instrumentation-test.db") }
    private fun n(body: String)=Notice(key="key",pkg="p",app="应用",title="标题",body=body,at=System.currentTimeMillis())
    @Test fun dedupUpdateAndRepostLifecycle() {
        assertTrue(db.insert(n("A"))); assertFalse(db.insert(n("A")))
        assertTrue(db.insert(n("B"))); assertEquals(2,db.pendingCount().toInt())
        db.removeLatest("key"); assertTrue(db.insert(n("B")))
        assertEquals(3,db.pendingCount().toInt())
    }
    @Test fun concurrentCreateAndClaimOnlyOnce() {
        db.insert(n("A")); val pool=Executors.newFixedThreadPool(4); val done=CountDownLatch(12)
        val ids=java.util.Collections.synchronizedSet(mutableSetOf<Long>())
        repeat(12) { pool.execute { try { ids.add(db.createBatch()!!) } finally { done.countDown() } } }
        assertTrue(done.await(10,java.util.concurrent.TimeUnit.SECONDS)); pool.shutdown()
        assertEquals(1,ids.size); val id=ids.first()
        assertTrue(db.transition(id,"QUEUED","ANALYZING")); assertFalse(db.transition(id,"QUEUED","ANALYZING"))
    }
    @Test fun arrivalsDuringAnalysisBelongToNextBatch() {
        db.insert(n("A")); val id=db.createBatch()!!; db.transition(id,"QUEUED","ANALYZING")
        db.insert(n("B")); assertEquals(listOf("A"),db.notices(id).map { it.body }); assertEquals(1L,db.pendingCount())
        val next=db.createBatch()!!
        db.transition(id,"ANALYZING","EMPTY")
        assertNotEquals(id,next); assertEquals(listOf("B"),db.notices(next).map { it.body })
    }
    @Test fun sendRetryPreservesSummaryAndRecoveryRequiresManualAction() {
        db.insert(n("A")); val id=db.createBatch()!!
        db.transition(id,"QUEUED","ANALYZING"); db.transition(id,"ANALYZING","READY","摘要",ids="1")
        db.transition(id,"READY","SENDING"); db.recover()
        assertEquals("UNCERTAIN",db.get(id)!!.state); assertFalse(Policy.mayClaim(db.get(id)!!.state))
        assertTrue(db.retry(id)); assertEquals("READY",db.get(id)!!.state); assertEquals("摘要",db.get(id)!!.message)
        db.transition(id,"READY","SENDING"); db.transition(id,"SENDING","SEND_FAILED")
        assertTrue(db.retry(id)); assertEquals("READY",db.get(id)!!.state); assertEquals("摘要",db.get(id)!!.message)
        db.transition(id,"READY","SENDING"); db.transition(id,"SENDING","DONE"); assertFalse(db.retry(id))
    }
    @Test fun staleOrPrematureTimerCannotFinishNewSession() {
        val t=db.timer()
        assertFalse(db.advanceTimer(t.token-1,t.end)); assertFalse(db.advanceTimer(t.token,t.end-1))
        assertTrue(db.advanceTimer(t.token,t.end)); assertFalse(db.advanceTimer(t.token,t.end))
    }
    @Test fun sourceTimeDistinguishesRealRepeatFromDuplicateCallback() {
        val first=n("好的").copy(sourceTime=100)
        assertTrue(db.insert(first)); assertFalse(db.insert(first.copy(at=System.currentTimeMillis())))
        val second=first.copy(sourceTime=200)
        assertTrue(db.insert(second)); assertFalse(db.insert(second))
        assertEquals(2L,db.pendingCount())
        assertEquals(listOf(200L,100L),db.recent().map { it.sourceTime })
    }
    @Test fun sameTextInNewBatchIsNotLostWhenNotificationWasNeverRemoved() {
        val first=n("好的").copy(sourceTime=100)
        assertTrue(db.insert(first)); val firstId=db.createBatch()!!
        assertTrue(db.transition(firstId,"QUEUED","ANALYZING"))
        assertTrue(db.transition(firstId,"ANALYZING","EMPTY"))
        assertFalse(db.insert(first))
        assertTrue(db.insert(first.copy(sourceTime=200)))
        val secondId=db.createBatch()!!
        assertNotEquals(firstId,secondId)
        assertEquals(100L,db.notices(firstId).single().sourceTime)
        assertEquals(200L,db.notices(secondId).single().sourceTime)
        assertEquals("好的",db.notices(secondId).single().body)
    }

    @Test fun ongoingStatusIgnoresTimeButCommunicationDoesNot() {
        val status=n("正在共享WLAN网络").copy(ongoing=true,sourceTime=100,channelId="network")
        assertTrue(db.insert(status)); assertFalse(db.insert(status.copy(sourceTime=200,at=System.currentTimeMillis())))
        assertEquals(1L,db.pendingCount())
        val chat=n("好的").copy(key="chat",kind="private",ongoing=true,sourceTime=100)
        assertTrue(db.insert(chat)); assertTrue(db.insert(chat.copy(sourceTime=200)))
        val call=n("来电").copy(key="call",kind="call",ongoing=true,sourceTime=100)
        assertTrue(db.insert(call)); assertTrue(db.insert(call.copy(sourceTime=200)))
        val mail=n("邮件").copy(key="mail",kind="mail",ongoing=true,sourceTime=100)
        assertTrue(db.insert(mail)); assertTrue(db.insert(mail.copy(sourceTime=200)))
    }
    @Test fun exactBodyRuleIgnoresTitleButNotOtherAppsOrBodies() {
        val wifi=n("正在共享WLAN网络").copy(title="2台设备",sourceTime=100)
        val real=n("需要回复").copy(key="real",sourceTime=100)
        assertTrue(db.insert(wifi)); assertTrue(db.insert(real))
        val id=db.createBatch()!!
        assertTrue(db.addFilter("body",wifi.pkg,wifi.app,wifi.body))
        assertEquals(listOf("需要回复"),db.recent().map { it.body })
        assertEquals(listOf("需要回复"),db.visibleNotices(id).map { it.body })
        assertFalse(db.insert(wifi.copy(title="3台设备",sourceTime=200)))
        assertTrue(db.insert(wifi.copy(key="other",pkg="another.app",sourceTime=200)))
        assertEquals(listOf("需要回复"),db.claimAnalysis(id)!!.map { it.body })
        assertEquals(2,db.notices(id).size) // immutable raw audit stays complete
    }
    @Test fun removingRuleRestoresFutureCollectionAndUnassignedCache() {
        val wifi=n("状态").copy(ongoing=true,sourceTime=100)
        db.insert(wifi); db.addFilter("body",wifi.pkg,wifi.app,wifi.body)
        assertEquals(0L,db.pendingCount()); assertNull(db.createBatch()); assertTrue(db.recent().isEmpty())
        db.removeFilter(db.filters().single().id)
        assertEquals(1L,db.pendingCount()); assertEquals(1,db.recent().size)
        assertTrue(db.insert(wifi.copy(sourceTime=200))) // cancellation clears status fingerprint
    }
    @Test fun channelRuleIsScopedToPackageAndChannel() {
        db.addFilter("channel","p","应用","status")
        assertFalse(db.insert(n("A").copy(channelId="status")))
        assertTrue(db.insert(n("A").copy(channelId="messages")))
        assertTrue(db.insert(n("A").copy(pkg="other",channelId="status")))
    }
    @Test fun rulesAddedAfterClaimDoNotMutateClaimedSnapshot() {
        val a=n("原消息"); db.insert(a); val id=db.createBatch()!!
        val snapshot=db.claimAnalysis(id)!!
        db.addFilter("app",a.pkg,a.app)
        assertEquals(listOf("原消息"),snapshot.map { it.body })
        assertEquals("ANALYZING",db.get(id)!!.state)
        assertFalse(db.insert(a.copy(sourceTime=200)))
        assertEquals(1,db.notices(id).size)
    }
    @Test fun remainingAdjustmentKeepsRunningOrPausedAndRejectsOldEvent() {
        val now=System.currentTimeMillis(); val old=db.timer()
        val running=db.adjustRemaining(15,now)!!
        assertEquals("RUNNING",running.state); assertEquals(now+900000,running.end)
        assertTrue(running.token>old.token); assertFalse(db.advanceTimer(old.token,now)); assertFalse(db.advanceTimer(running.token,now))
        db.pauseFocus(now+1)
        val paused=db.adjustRemaining(35,now+2)!!
        assertEquals("PAUSED",paused.state); assertEquals(0L,paused.end); assertEquals(2100000L,paused.remaining)
        assertTrue(paused.token>running.token); assertFalse(db.advanceTimer(running.token,now+2))
        db.stopFocus(now+3)
        assertNull(db.adjustRemaining(25,now+4)); assertEquals("IDLE",db.timer().state)
    }
    @Test fun legacyAppExclusionsImportOnlyOnce() {
        val name="inbox-legacy-filters-test.db"
        context.deleteDatabase(name)
        try {
            Store(context,name,setOf("blocked.app")).use { migrated ->
                assertEquals("blocked.app",migrated.filters().single().pkg); migrated.startFocus(25,FocusMode.SINGLE)
                assertFalse(migrated.insert(n("通知").copy(pkg="blocked.app")))
                migrated.removeFilter(migrated.filters().single().id)
            }
            Store(context,name,setOf("blocked.app")).use { reopened ->
                assertTrue(reopened.filters().isEmpty()); reopened.startFocus(25,FocusMode.SINGLE)
                assertTrue(reopened.insert(n("通知").copy(pkg="blocked.app")))
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun v1MigrationPreservesHistoryAndStopsUnattributedLegacyTimer() {
        val name="inbox-v1-migration-test.db"
        context.deleteDatabase(name)
        try {
            android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use { old ->
                old.execSQL("CREATE TABLE notices(id INTEGER PRIMARY KEY AUTOINCREMENT,nkey TEXT NOT NULL,pkg TEXT NOT NULL,app TEXT NOT NULL,title TEXT NOT NULL,body TEXT NOT NULL,at INTEGER NOT NULL,kind TEXT NOT NULL,hidden INTEGER NOT NULL,summary INTEGER NOT NULL,sourceTime INTEGER NOT NULL DEFAULT 0,fingerprint TEXT NOT NULL,batch INTEGER)")
                old.execSQL("CREATE INDEX notices_batch ON notices(batch)")
                old.execSQL("CREATE TABLE latest(nkey TEXT PRIMARY KEY,fingerprint TEXT NOT NULL)")
                old.execSQL("CREATE TABLE batches(id INTEGER PRIMARY KEY AUTOINCREMENT,state TEXT NOT NULL,created INTEGER NOT NULL,message TEXT NOT NULL DEFAULT '',error TEXT NOT NULL DEFAULT '',ids TEXT NOT NULL DEFAULT '')")
                old.execSQL("CREATE TABLE timer(id INTEGER PRIMARY KEY CHECK(id=1),state TEXT NOT NULL,endAt INTEGER NOT NULL,remaining INTEGER NOT NULL,token INTEGER NOT NULL)")
                old.execSQL("INSERT INTO notices VALUES(1,'k','p','应用','旧标题','旧私聊',1,'private',0,0,0,'old',1)")
                old.execSQL("INSERT INTO batches VALUES(1,'DONE',1,'旧摘要','','1')")
                old.execSQL("INSERT INTO timer VALUES(1,'PAUSED',0,60000,123)")
                old.version=1
            }
            Store(context,name,emptySet()).use { upgraded ->
                assertEquals(5,upgraded.readableDatabase.version)
                assertEquals(DeliveryTarget.FEISHU,upgraded.get(1)!!.target)
                val notice=upgraded.notices(1).single()
                assertEquals("旧私聊",notice.body); assertEquals(0L,notice.sourceTime); assertFalse(notice.ongoing); assertEquals("",notice.channelId)
                assertEquals("旧摘要",upgraded.get(1)!!.message); assertEquals("DONE",upgraded.get(1)!!.state)
                assertEquals("IDLE",upgraded.timer().state); assertEquals(0L,upgraded.timer().remaining); assertTrue(upgraded.timer().token>123)
                upgraded.startFocus(25,FocusMode.SINGLE)
                assertTrue(upgraded.insert(n("升级后通知").copy(channelId="new",ongoing=true)))
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun failedAnalysisUsesNewRulesOnManualRetry() {
        val a=n("不再需要的消息"); db.insert(a); val id=db.createBatch()!!
        assertEquals(1,db.claimAnalysis(id)!!.size)
        db.transition(id,"ANALYZING","FAILED")
        db.addFilter("body",a.pkg,a.app,a.body)
        assertTrue(db.retry(id)); assertTrue(db.claimAnalysis(id)!!.isEmpty())
        assertEquals(1,db.notices(id).size)
    }

}
