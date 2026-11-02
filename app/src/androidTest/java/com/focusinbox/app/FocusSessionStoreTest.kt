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

@RunWith(AndroidJUnit4::class)
class FocusSessionStoreTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="inbox-focus-session-test.db"
    private lateinit var db: Store
    @Before fun before() { context.deleteDatabase(name); db=Store(context,name,emptySet()) { DeliveryTarget.LOCAL } }
    @After fun after() { db.close(); context.deleteDatabase(name) }
    private fun n(at: Long,body: String="message",key: String="key")=Notice(key=key,pkg="chat",app="Chat",title="Friend",body=body,at=at,kind="private",sourceTime=at)
    private fun count(table: String)=db.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table",null).use { it.moveToFirst(); it.getLong(0) }
    @Test fun singleWindowIsStartInclusiveEndExclusiveAndIdleNeverCollects() {
        assertFalse(db.insert(n(999))); assertNull(db.createBatch(999))
        val t=db.startFocus(1,FocusMode.SINGLE,1000)
        assertFalse(db.insert(n(999))); assertTrue(db.insert(n(1000,"at start")))
        assertTrue(db.insert(n(60999,"before end")))
        assertFalse(db.insert(n(61000,"at end"))); assertFalse(db.insert(n(62000,"after end")))
        assertEquals("IDLE",db.timer().state); assertEquals(0L,db.pendingCount())
        val b=db.batches().single()
        assertEquals(listOf("at start","before end"),db.notices(b.id).map { it.body })
        assertEquals(t.roundId,b.roundId); assertEquals(1000L,b.roundStart); assertEquals(61000L,b.roundEnd)
        assertNull(db.createBatch(70000))
    }
    @Test fun loopBoundaryGoesToNextRoundAndFailedPriorBatchDoesNotBlockIt() {
        val first=db.startFocus(1,FocusMode.LOOP,1000)
        db.insert(n(60000,"first")); assertTrue(db.insert(n(61000,"boundary")))
        val b1=db.batches().single(); db.claimAnalysis(b1.id); db.transition(b1.id,"ANALYZING","FAILED")
        val second=db.timer()
        assertNotEquals(first.roundId,second.roundId); assertEquals(61000L,second.start); assertEquals(121000L,second.end)
        assertEquals(1L,db.pendingCount()); assertFalse(db.advanceTimer(first.token,121000))
        assertTrue(db.advanceTimer(second.token,121000))
        val b2=db.batches().first(); assertNotEquals(b1.id,b2.id)
        assertEquals("FAILED",db.get(b1.id)!!.state); assertEquals("QUEUED",b2.state)
        assertEquals(listOf("first"),db.notices(b1.id).map { it.body })
        assertEquals(listOf("boundary"),db.notices(b2.id).map { it.body })
        assertEquals(121000L,db.timer().start); assertEquals("RUNNING",db.timer().state)
    }
    @Test fun manualAnalysisAndEarlyStopNeverReincludeAlreadyAssignedNotices() {
        db.startFocus(1,FocusMode.LOOP,1000)
        db.insert(n(2000,"first")); val first=db.createBatch(3000)!!
        assertEquals(first,db.createBatch(4000)); assertEquals(1,count("batches").toInt())
        db.claimAnalysis(first); db.transition(first,"ANALYZING","FAILED")
        assertNull(db.createBatch(5000)) // failure is a manual retry, not a new snapshot
        db.insert(n(6000,"second")); val second=db.createBatch(7000)!!
        assertNotEquals(first,second); db.insert(n(8000,"third"))
        assertTrue(db.stopFocus(9000)); assertFalse(db.stopFocus(9000)); assertFalse(db.insert(n(9000,"stopped")))
        val third=db.batches().first()
        assertEquals(3,count("batches").toInt()); assertEquals(listOf("third"),db.notices(third.id).map { it.body })
        assertEquals(listOf("first"),db.notices(first).map { it.body }); assertEquals(listOf("second"),db.notices(second).map { it.body })
        assertEquals("IDLE",db.timer().state)
    }
    @Test fun emptySingleAndLoopRoundsCreateNoBatches() {
        val single=db.startFocus(1,FocusMode.SINGLE,1000)
        assertTrue(db.advanceTimer(single.token,61000)); assertTrue(db.batches().isEmpty())
        val loop=db.startFocus(1,FocusMode.LOOP,100000)
        assertTrue(db.advanceTimer(loop.token,400000))
        assertTrue(db.batches().isEmpty()); assertTrue(db.claimableBatches().isEmpty())
        assertEquals(400000L,db.timer().start); assertEquals(460000L,db.timer().end)
    }
    @Test fun lateDeliverySkipsEmptyWindowsAndRecoveryKeepsCurrentWindow() {
        val t=db.startFocus(1,FocusMode.LOOP,1000)
        db.insert(n(2000,"old round"))
        db.close(); db=Store(context,name,emptySet()) { DeliveryTarget.LOCAL }
        db.recover(); assertTrue(db.advanceTimer(t.token,190000))
        assertEquals(181000L,db.timer().start); assertEquals(241000L,db.timer().end)
        assertEquals(1,db.batches().size); assertEquals(2,count("focus_rounds").toInt())
        assertFalse(db.insert(n(170000,"late old callback")))
        assertTrue(db.insert(n(190000,"current round")))
        assertFalse(db.advanceTimer(t.token,241000)); assertTrue(db.advanceTimer(db.timer().token,241000))
        assertEquals(2,db.batches().size)
        assertEquals(listOf("current round"),db.notices(db.batches().first().id).map { it.body })
    }
    @Test fun pauseKeepsCollectingAndResumePreservesModeAndMovesEnd() {
        val t=db.startFocus(1,FocusMode.LOOP,1000)
        val paused=db.pauseFocus(31000)
        assertEquals("PAUSED",paused.state); assertEquals(30000L,paused.remaining); assertEquals(0L,paused.end)
        assertFalse(db.advanceTimer(t.token,150000)); assertTrue(db.insert(n(150000,"while paused")))
        val resumed=db.startFocus(25,FocusMode.SINGLE,200000)
        assertEquals(FocusMode.LOOP,resumed.mode); assertEquals(60000L,resumed.interval); assertEquals(230000L,resumed.end)
        assertEquals(t.roundId,resumed.roundId); assertEquals(1000L,resumed.start)
        assertFalse(db.advanceTimer(paused.token,230000)); assertTrue(db.advanceTimer(resumed.token,230000))
        assertEquals(listOf("while paused"),db.notices(db.batches().single().id).map { it.body })
    }
    @Test fun remainingAdjustmentOnlyChangesCurrentRoundAndRejectsOldToken() {
        val initial=db.startFocus(1,FocusMode.LOOP,1000)
        val adjusted=db.adjustRemaining(5,2000)!!
        assertEquals(302000L,adjusted.end); assertEquals(60000L,adjusted.interval); assertEquals(FocusMode.LOOP,adjusted.mode)
        assertFalse(db.advanceTimer(initial.token,302000)); assertTrue(db.advanceTimer(adjusted.token,302000))
        assertEquals(302000L,db.timer().start); assertEquals(362000L,db.timer().end)
        val paused=db.pauseFocus(312000); val p=db.adjustRemaining(10,313000)!!
        assertEquals("PAUSED",p.state); assertEquals(600000L,p.remaining); assertEquals(0L,p.end)
        assertTrue(p.token>paused.token); assertFalse(db.advanceTimer(paused.token,1000000))
    }
    @Test fun concurrentBoundaryCollectionAndDuplicateFinishProduceOneRoundBatch() {
        val first=db.startFocus(1,FocusMode.LOOP,1000)
        assertTrue(db.insert(n(2000,"first")))
        val pool=Executors.newFixedThreadPool(4); val ready=CountDownLatch(1); val done=CountDownLatch(20)
        val errors=java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        repeat(20) { index -> pool.execute {
            try { ready.await(); if(index%2==0) db.advanceTimer(first.token,61000) else db.insert(n(61000,"boundary")) }
            catch(t: Throwable) { errors.add(t) } finally { done.countDown() }
        } }
        ready.countDown(); assertTrue(done.await(10,TimeUnit.SECONDS)); pool.shutdown(); assertTrue(errors.toString(),errors.isEmpty())
        assertEquals(1,db.batches().size); assertEquals(2,count("focus_rounds").toInt()); assertEquals(1L,db.pendingCount())
        val old=db.batches().single(); assertEquals(listOf("first"),db.notices(old.id).map { it.body })
        db.advanceTimer(db.timer().token,121000)
        assertEquals(listOf("boundary"),db.notices(db.batches().first().id).map { it.body })
    }
    @Test fun startingNewFocusDropsUnassignedCacheButPreservesHistoricalBatchesAndFilters() {
        db.startFocus(1,FocusMode.SINGLE,1000); db.insert(n(2000,"history"))
        val history=db.createBatch(3000)!!; db.claimAnalysis(history); db.transition(history,"ANALYZING","DONE","summary")
        db.stopFocus(4000); db.addFilter("app","blocked","Blocked")
        // Legacy/imported orphan cache must never be swept into a later focus.
        db.writableDatabase.execSQL("INSERT INTO notices(nkey,pkg,app,title,body,at,kind,hidden,summary,fingerprint) VALUES('orphan','old','Old','old','old cache',5,'unknown',0,0,'orphan')")
        db.startFocus(1,FocusMode.SINGLE,5000)
        assertEquals(listOf("history"),db.recent().map { it.body }); assertEquals(0L,db.pendingCount())
        assertEquals("DONE",db.get(history)!!.state); assertEquals(1,db.filters().size)
        assertFalse(db.insert(n(6000).copy(pkg="blocked")))
    }
    @Test fun currentRoundDoesNotReuseFailedBatchFromAnotherRound() {
        val t=db.startFocus(1,FocusMode.LOOP,1000); db.insert(n(2000,"first")); db.advanceTimer(t.token,61000)
        val failed=db.batches().single(); db.claimAnalysis(failed.id); db.transition(failed.id,"ANALYZING","FAILED")
        assertNull(db.createBatch(62000)); assertEquals(1,db.batches().size)
        db.insert(n(63000,"second")); val current=db.createBatch(64000)!!
        assertNotEquals(failed.id,current); assertNotEquals(failed.roundId,db.get(current)!!.roundId)
        assertEquals(listOf(current),db.claimableBatches().map { it.id })
    }
    @Test fun v3MigrationClearsOnlyOrphanCacheStopsOldTimerAndPreservesHistoryFiltersAndTarget() {
        db.close(); context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use { old ->
            old.execSQL("CREATE TABLE notices(id INTEGER PRIMARY KEY AUTOINCREMENT,nkey TEXT NOT NULL,pkg TEXT NOT NULL,app TEXT NOT NULL,title TEXT NOT NULL,body TEXT NOT NULL,at INTEGER NOT NULL,kind TEXT NOT NULL,hidden INTEGER NOT NULL,summary INTEGER NOT NULL,sourceTime INTEGER NOT NULL DEFAULT 0,ongoing INTEGER NOT NULL DEFAULT 0,channelId TEXT NOT NULL DEFAULT '',fingerprint TEXT NOT NULL,batch INTEGER)")
            old.execSQL("CREATE TABLE latest(nkey TEXT PRIMARY KEY,fingerprint TEXT NOT NULL)")
            old.execSQL("CREATE TABLE batches(id INTEGER PRIMARY KEY AUTOINCREMENT,state TEXT NOT NULL,created INTEGER NOT NULL,message TEXT NOT NULL DEFAULT '',error TEXT NOT NULL DEFAULT '',ids TEXT NOT NULL DEFAULT '',target TEXT NOT NULL DEFAULT 'FEISHU')")
            old.execSQL("CREATE TABLE timer(id INTEGER PRIMARY KEY CHECK(id=1),state TEXT NOT NULL,endAt INTEGER NOT NULL,remaining INTEGER NOT NULL,token INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE filters(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,pkg TEXT NOT NULL,app TEXT NOT NULL,value TEXT NOT NULL,UNIQUE(kind,pkg,value))")
            old.execSQL("CREATE TABLE meta(name TEXT PRIMARY KEY,value TEXT NOT NULL)")
            old.execSQL("INSERT INTO meta VALUES('legacyExcluded','1')")
            old.execSQL("INSERT INTO notices VALUES(1,'history','p','App','Title','history',1,'private',0,0,1,0,'chat','a',9001)")
            old.execSQL("INSERT INTO notices VALUES(2,'orphan','p','App','Title','orphan',2,'private',0,0,2,0,'chat','b',NULL)")
            old.execSQL("INSERT INTO batches VALUES(9001,'DONE',1,'summary','','1','LOCAL')")
            old.execSQL("INSERT INTO batches VALUES(9002,'QUEUED',2,'','','','FEISHU')")
            old.execSQL("INSERT INTO batches VALUES(9003,'READY',3,'unsent summary','','1','LOCAL')")
            old.execSQL("INSERT INTO timer VALUES(1,'RUNNING',9999999999999,60000,123)")
            old.execSQL("INSERT INTO filters VALUES(1,'app','blocked','Blocked','')")
            old.execSQL("INSERT INTO latest VALUES('orphan','b')")
            old.version=3
        }
        db=Store(context,name,emptySet()) { DeliveryTarget.LOCAL }
        assertEquals(4,db.readableDatabase.version); assertEquals("IDLE",db.timer().state); assertTrue(db.timer().token>123)
        assertEquals(0,count("latest").toInt()); assertEquals(listOf("history"),db.recent().map { it.body })
        assertEquals(DeliveryTarget.LOCAL,db.get(9001)!!.target); assertEquals("summary",db.get(9001)!!.message)
        assertEquals("FAILED",db.get(9002)!!.state); assertEquals("@error:ANALYSIS_INTERRUPTED",db.get(9002)!!.error)
        assertEquals("SEND_FAILED",db.get(9003)!!.state); assertEquals("unsent summary",db.get(9003)!!.message)
        assertEquals("@error:SEND_CONFIG",db.get(9003)!!.error); assertTrue(db.claimableBatches().isEmpty())
        assertEquals(1,db.filters().size); assertEquals(0L,db.pendingCount()); assertFalse(db.insert(n(10000)))
        db.startFocus(1,FocusMode.LOOP,10000); assertTrue(db.insert(n(11000,"new")))
        val batch=db.createBatch(12000)!!; assertEquals(listOf("new"),db.notices(batch).map { it.body })
    }
    @Test fun pruningAndClearingDiscardEmptyRoundsButPreserveTheActiveWindow() {
        val initial=db.startFocus(1,FocusMode.LOOP,1000)
        db.insert(n(2000,"failed history")); db.advanceTimer(initial.token,61000)
        val history=db.batches().single(); db.claimAnalysis(history.id); db.transition(history.id,"ANALYZING","FAILED")
        db.advanceTimer(db.timer().token,121000) // empty closed round is discarded immediately
        assertEquals(2,count("focus_rounds").toInt())
        val current=db.timer(); db.prune()
        assertEquals("FAILED",db.get(history.id)!!.state); assertEquals(current,db.timer())
        assertTrue(db.clear()); assertTrue(db.batches().isEmpty()); assertEquals(1,count("focus_rounds").toInt())
        assertEquals(current,db.timer()); assertTrue(db.insert(n(122000,"still current")))
        assertEquals(1L,db.pendingCount())
    }
    @Test fun switchingToLoopPreservesClockAndStartsNextRound() {
        val first=db.startFocus(1,FocusMode.SINGLE,1000)
        db.insert(n(2000,"first round"))
        val changed=db.changeFocusMode(FocusMode.LOOP,31000)
        assertEquals(first.copy(mode=FocusMode.LOOP),changed)
        assertTrue(db.advanceTimer(first.token,61000))
        assertEquals(61000L,db.timer().start); assertEquals(121000L,db.timer().end)
        assertEquals(FocusMode.LOOP,db.timer().mode)
        assertEquals(listOf("first round"),db.notices(db.batches().single().id).map { it.body })
        assertFalse(db.advanceTimer(first.token,61000))
    }
    @Test fun switchingToSingleStopsAtExistingDeadline() {
        val first=db.startFocus(1,FocusMode.LOOP,1000)
        db.insert(n(2000,"one message"))
        assertEquals(first.copy(mode=FocusMode.SINGLE),db.changeFocusMode(FocusMode.SINGLE,31000))
        assertTrue(db.advanceTimer(first.token,61000))
        assertEquals("IDLE",db.timer().state); assertEquals(1,db.batches().size)
        assertFalse(db.insert(n(62000,"after stop")))
    }
    @Test fun switchingWhilePausedPersistsAcrossReopenAndResume() {
        db.startFocus(1,FocusMode.SINGLE,1000)
        val paused=db.pauseFocus(31000)
        assertEquals(paused.copy(mode=FocusMode.LOOP),db.changeFocusMode(FocusMode.LOOP,40000))
        db.close(); db=Store(context,name,emptySet()) { DeliveryTarget.LOCAL }
        val resumed=db.startFocus(25,FocusMode.SINGLE,100000)
        assertEquals(FocusMode.LOOP,resumed.mode); assertEquals(130000L,resumed.end)
        assertEquals(paused.roundId,resumed.roundId)
        db.advanceTimer(resumed.token,130000)
        assertEquals("RUNNING",db.timer().state); assertEquals(190000L,db.timer().end)
    }
    @Test fun switchingAtDeadlineSettlesOldModeBeforeApplyingNewMode() {
        val first=db.startFocus(1,FocusMode.LOOP,1000)
        db.insert(n(2000,"old round"))
        val changed=db.changeFocusMode(FocusMode.SINGLE,61000)
        assertNotEquals(first.roundId,changed.roundId); assertEquals(61000L,changed.start)
        assertEquals(FocusMode.SINGLE,changed.mode); assertEquals(1,db.batches().size)
        db.advanceTimer(changed.token,121000)
        assertEquals("IDLE",db.timer().state)
        assertEquals("IDLE",db.changeFocusMode(FocusMode.LOOP,122000).state)
    }
    @Test fun modePreferenceDefaultsToSingleAndPersistsWithoutAffectingActiveMode() {
        val prefs="focus-mode-test"
        context.deleteSharedPreferences(prefs)
        try {
            val s=Settings(context,prefs); assertEquals(FocusMode.SINGLE,s.focusMode)
            s.focusMode=FocusMode.LOOP; assertEquals(FocusMode.LOOP,Settings(context,prefs).focusMode)
            val active=db.startFocus(1,s.focusMode,1000); s.focusMode=FocusMode.SINGLE
            assertEquals(FocusMode.LOOP,db.timer().mode); assertEquals(active.roundId,db.timer().roundId)
        } finally { context.deleteSharedPreferences(prefs) }
    }
}
