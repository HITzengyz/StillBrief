package com.focusinbox.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DeliveryStoreTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="inbox-delivery-test.db"
    private val selected=AtomicReference(DeliveryTarget.LOCAL)
    private lateinit var db: Store
    @Before fun before() { context.deleteDatabase(name); selected.set(DeliveryTarget.LOCAL); db=Store(context,name,emptySet()) { selected.get() }; db.startFocus(25,FocusMode.SINGLE) }
    @After fun after() { db.close(); context.deleteDatabase(name) }
    private fun add(text: String="Synthetic")=db.insert(Notice(key="k",pkg="p",app="App",title="Title",body=text,at=System.currentTimeMillis()))
    private fun ready(target: DeliveryTarget): Long {
        selected.set(target); add(); val id=db.createBatch()!!
        db.transition(id,"QUEUED","ANALYZING"); db.transition(id,"ANALYZING","READY","Existing summary",ids="1")
        return id
    }
    @Test fun newBatchKeepsItsTargetWhileFutureBatchesUseTheNewChoice() {
        add("First"); val first=db.createBatch()!!
        selected.set(DeliveryTarget.FEISHU)
        add("Second"); val second=db.createBatch()!!
        assertNotEquals(first,second)
        assertEquals(DeliveryTarget.LOCAL,db.get(first)!!.target)
        db.transition(first,"QUEUED","ANALYZING"); db.transition(first,"ANALYZING","EMPTY")
        assertEquals(DeliveryTarget.FEISHU,db.get(second)!!.target)
        assertEquals(DeliveryTarget.LOCAL,db.get(first)!!.target)
    }
    @Test fun concurrentTriggersReadDestinationOnlyWhenCreatingOneBatch() {
        db.close(); val calls=AtomicInteger()
        db=Store(context,name,emptySet()) { calls.incrementAndGet(); selected.get() }
        add()
        val pool=Executors.newFixedThreadPool(4); val done=CountDownLatch(20)
        val ids=java.util.Collections.synchronizedSet(mutableSetOf<Long>())
        repeat(20) { index -> pool.execute { try { selected.set(if(index%2==0) DeliveryTarget.LOCAL else DeliveryTarget.FEISHU); ids.add(db.createBatch()!!) } finally { done.countDown() } } }
        assertTrue(done.await(10,TimeUnit.SECONDS)); pool.shutdown()
        assertEquals(1,ids.size); assertEquals(1,calls.get())
        val frozen=db.get(ids.first())!!.target
        repeat(5) { selected.set(if(frozen==DeliveryTarget.LOCAL) DeliveryTarget.FEISHU else DeliveryTarget.LOCAL); assertEquals(ids.first(),db.createBatch()); assertEquals(frozen,db.get(ids.first())!!.target) }
    }
    @Test fun explicitFailedSendSwitchPreservesSummaryAndRequiresRetry() {
        val id=ready(DeliveryTarget.FEISHU)
        db.transition(id,"READY","SENDING"); db.transition(id,"SENDING","SEND_FAILED")
        selected.set(DeliveryTarget.LOCAL)
        assertEquals(DeliveryTarget.FEISHU,db.get(id)!!.target)
        assertTrue(db.retry(id,DeliveryTarget.LOCAL))
        assertEquals(DeliveryTarget.LOCAL,db.get(id)!!.target); assertEquals("READY",db.get(id)!!.state)
        assertEquals("Existing summary",db.get(id)!!.message); assertEquals("1",db.get(id)!!.ids)
    }
    @Test fun activeOrCompletedBatchesRejectRetargeting() {
        val id=ready(DeliveryTarget.LOCAL)
        listOf("READY","SENDING","DONE").forEachIndexed { index,state ->
            if(index>0) db.transition(id,if(index==1) "READY" else "SENDING",state)
            assertFalse(db.retry(id,DeliveryTarget.FEISHU)); assertEquals(DeliveryTarget.LOCAL,db.get(id)!!.target)
        }
        add("Other"); val queued=db.createBatch()!!
        assertFalse(db.retry(queued,DeliveryTarget.FEISHU)); db.transition(queued,"QUEUED","ANALYZING")
        assertFalse(db.retry(queued,DeliveryTarget.FEISHU)); assertEquals(DeliveryTarget.LOCAL,db.get(queued)!!.target)
    }
    @Test fun uncertainFeishuCanExplicitlyRetryLocallyAndLocalInterruptionsAreNotUncertain() {
        val id=ready(DeliveryTarget.FEISHU); db.transition(id,"READY","SENDING"); db.recover()
        assertEquals("UNCERTAIN",db.get(id)!!.state)
        assertTrue(db.retry(id,DeliveryTarget.LOCAL)); db.transition(id,"READY","SENDING"); db.recover()
        assertEquals("SEND_FAILED",db.get(id)!!.state); assertEquals("@error:LOCAL_INTERRUPTED",db.get(id)!!.error)
        assertEquals(DeliveryTarget.LOCAL,db.get(id)!!.target)
        assertTrue(db.retry(id)); assertEquals("READY",db.get(id)!!.state); assertEquals(DeliveryTarget.LOCAL,db.get(id)!!.target)
    }
    @Test fun failedAnalysisMayChangeDestinationOnlyWhenManuallyRetried() {
        add(); val id=db.createBatch()!!; db.transition(id,"QUEUED","ANALYZING"); db.transition(id,"ANALYZING","FAILED")
        selected.set(DeliveryTarget.FEISHU); assertEquals(DeliveryTarget.LOCAL,db.get(id)!!.target)
        assertTrue(db.retry(id,DeliveryTarget.FEISHU)); assertEquals("QUEUED",db.get(id)!!.state); assertEquals(DeliveryTarget.FEISHU,db.get(id)!!.target)
    }
    @Test fun v2MigrationPreservesHistoryFiltersStopsTimerAndDefaultsOldBatchesToFeishu() {
        db.close(); context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use { old ->
            old.execSQL("CREATE TABLE notices(id INTEGER PRIMARY KEY AUTOINCREMENT,nkey TEXT NOT NULL,pkg TEXT NOT NULL,app TEXT NOT NULL,title TEXT NOT NULL,body TEXT NOT NULL,at INTEGER NOT NULL,kind TEXT NOT NULL,hidden INTEGER NOT NULL,summary INTEGER NOT NULL,sourceTime INTEGER NOT NULL DEFAULT 0,ongoing INTEGER NOT NULL DEFAULT 0,channelId TEXT NOT NULL DEFAULT '',fingerprint TEXT NOT NULL,batch INTEGER)")
            old.execSQL("CREATE TABLE latest(nkey TEXT PRIMARY KEY,fingerprint TEXT NOT NULL)")
            old.execSQL("CREATE TABLE batches(id INTEGER PRIMARY KEY AUTOINCREMENT,state TEXT NOT NULL,created INTEGER NOT NULL,message TEXT NOT NULL DEFAULT '',error TEXT NOT NULL DEFAULT '',ids TEXT NOT NULL DEFAULT '')")
            old.execSQL("CREATE TABLE timer(id INTEGER PRIMARY KEY CHECK(id=1),state TEXT NOT NULL,endAt INTEGER NOT NULL,remaining INTEGER NOT NULL,token INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE filters(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,pkg TEXT NOT NULL,app TEXT NOT NULL,value TEXT NOT NULL,UNIQUE(kind,pkg,value))")
            old.execSQL("CREATE TABLE meta(name TEXT PRIMARY KEY,value TEXT NOT NULL)")
            old.execSQL("INSERT INTO meta VALUES('legacyExcluded','1')")
            old.execSQL("INSERT INTO notices VALUES(1,'old','old.app','旧应用','旧标题','旧正文',1,'private',0,0,100,0,'chat','old-fingerprint',9001)")
            old.execSQL("INSERT INTO batches VALUES(9001,'SEND_FAILED',1,'旧摘要','旧错误','1')")
            old.execSQL("INSERT INTO filters VALUES(1,'body','blocked.app','Blocked','spam')")
            old.execSQL("INSERT INTO timer VALUES(1,'PAUSED',0,60000,123)")
            old.version=2
        }
        db=Store(context,name,emptySet()) { DeliveryTarget.LOCAL }
        assertEquals(4,db.readableDatabase.version)
        val old=db.get(9001)!!
        assertEquals(DeliveryTarget.FEISHU,old.target); assertEquals("旧摘要",old.message); assertEquals("旧错误",old.error)
        assertEquals("旧正文",db.notices(9001).single().body); assertEquals("spam",db.filters().single().value)
        assertEquals("IDLE",db.timer().state); assertEquals(0L,db.timer().remaining); assertTrue(db.timer().token>123)
        assertTrue(db.retry(9001,DeliveryTarget.LOCAL)); assertEquals(DeliveryTarget.LOCAL,db.get(9001)!!.target)
    }
}
