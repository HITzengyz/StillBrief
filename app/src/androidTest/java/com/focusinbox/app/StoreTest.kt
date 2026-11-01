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
    @Before fun setUp() { context.deleteDatabase("inbox-instrumentation-test.db"); db=Store(context,"inbox-instrumentation-test.db") }
    @After fun tearDown() { db.close(); context.deleteDatabase("inbox-instrumentation-test.db") }
    private fun n(body: String)=Notice(key="key",pkg="p",app="应用",title="标题",body=body,at=1)
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
        assertEquals(id,db.createBatch())
        db.transition(id,"ANALYZING","EMPTY"); val next=db.createBatch()!!
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
        db.setTimer("RUNNING",System.currentTimeMillis()+60000,60000,222)
        assertFalse(db.finishTimer(111)); assertFalse(db.finishTimer(222))
        db.setTimer("RUNNING",System.currentTimeMillis()-1,0,222)
        assertTrue(db.finishTimer(222)); assertFalse(db.finishTimer(222))
    }
    @Test fun sourceTimeDistinguishesRealRepeatFromDuplicateCallback() {
        val first=n("好的").copy(sourceTime=100)
        assertTrue(db.insert(first)); assertFalse(db.insert(first.copy(at=500)))
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

}
