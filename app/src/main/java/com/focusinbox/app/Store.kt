package com.focusinbox.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Batch(val id: Long, val state: String, val created: Long, val message: String, val error: String, val ids: String,val target: DeliveryTarget=DeliveryTarget.FEISHU,val roundId: Long=0,val roundStart: Long=0,val roundEnd: Long=0,val mode: FocusMode=FocusMode.SINGLE,val instantKey: String="")
class Store(context: Context, databaseName: String = "inbox.db", private val legacyExcluded: Set<String> = Settings(context).excluded,private val targetProvider: ()->DeliveryTarget = { Settings(context).destination }) : SQLiteOpenHelper(context, databaseName, null, 5) {
    companion object { private val lock = Any() }
    private val appContext=context.applicationContext
    private val columns="id,nkey,pkg,app,title,body,at,kind,hidden,summary,sourceTime,ongoing,channelId"
    private val visible="NOT EXISTS (SELECT 1 FROM filters f WHERE f.pkg=notices.pkg AND (f.kind='app' OR (f.kind='body' AND f.value=notices.body) OR (f.kind='channel' AND f.value=notices.channelId)))"
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE notices(id INTEGER PRIMARY KEY AUTOINCREMENT, nkey TEXT NOT NULL, pkg TEXT NOT NULL, app TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, at INTEGER NOT NULL, kind TEXT NOT NULL, hidden INTEGER NOT NULL, summary INTEGER NOT NULL, sourceTime INTEGER NOT NULL DEFAULT 0, ongoing INTEGER NOT NULL DEFAULT 0, channelId TEXT NOT NULL DEFAULT '', fingerprint TEXT NOT NULL, batch INTEGER, roundId INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX notices_batch ON notices(batch)")
        db.execSQL("CREATE TABLE latest(nkey TEXT PRIMARY KEY, fingerprint TEXT NOT NULL)")
        db.execSQL("CREATE TABLE batches(id INTEGER PRIMARY KEY AUTOINCREMENT, state TEXT NOT NULL, created INTEGER NOT NULL, message TEXT NOT NULL DEFAULT '', error TEXT NOT NULL DEFAULT '', ids TEXT NOT NULL DEFAULT '', target TEXT NOT NULL DEFAULT 'FEISHU', roundId INTEGER NOT NULL DEFAULT 0, instantKey TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE timer(id INTEGER PRIMARY KEY CHECK(id=1), state TEXT NOT NULL, endAt INTEGER NOT NULL, remaining INTEGER NOT NULL, token INTEGER NOT NULL, startAt INTEGER NOT NULL DEFAULT 0, mode TEXT NOT NULL DEFAULT 'SINGLE', intervalMs INTEGER NOT NULL DEFAULT 1500000, roundId INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("INSERT INTO timer(id,state,endAt,remaining,token) VALUES(1,'IDLE',0,0,0)")
        createRounds(db)
        createFilters(db)
        createCodeNotifications(db)
    }
    private fun createFilters(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE filters(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, pkg TEXT NOT NULL, app TEXT NOT NULL, value TEXT NOT NULL, UNIQUE(kind,pkg,value))")
        db.execSQL("CREATE TABLE meta(name TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }
    private fun createRounds(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE focus_rounds(id INTEGER PRIMARY KEY AUTOINCREMENT,startAt INTEGER NOT NULL,endAt INTEGER NOT NULL,mode TEXT NOT NULL,intervalMs INTEGER NOT NULL,closed INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX notices_round ON notices(roundId,batch)")
    }
    private fun createCodeNotifications(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE code_notifications(nkey TEXT PRIMARY KEY,batchId INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        check(old in 1..4 && new==5) { "Unsupported database migration" }
        db.execSQL("ALTER TABLE batches ADD COLUMN instantKey TEXT NOT NULL DEFAULT ''")
        createCodeNotifications(db)
        if(old==4) return
        if(old<2) {
            db.execSQL("ALTER TABLE notices ADD COLUMN ongoing INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notices ADD COLUMN channelId TEXT NOT NULL DEFAULT ''")
            createFilters(db)
        }
        if(old<3) db.execSQL("ALTER TABLE batches ADD COLUMN target TEXT NOT NULL DEFAULT 'FEISHU'")
        db.execSQL("ALTER TABLE notices ADD COLUMN roundId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE batches ADD COLUMN roundId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE timer ADD COLUMN startAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE timer ADD COLUMN mode TEXT NOT NULL DEFAULT 'SINGLE'")
        db.execSQL("ALTER TABLE timer ADD COLUMN intervalMs INTEGER NOT NULL DEFAULT 1500000")
        db.execSQL("ALTER TABLE timer ADD COLUMN roundId INTEGER NOT NULL DEFAULT 0")
        createRounds(db)
        // Old timers have no collection start, so their unassigned cache cannot be attributed safely.
        db.execSQL("DELETE FROM notices WHERE batch IS NULL")
        db.execSQL("DELETE FROM latest")
        // Existing history stays reviewable, but upgrading never starts an old unsent batch.
        db.execSQL("UPDATE batches SET state='FAILED',error='@error:ANALYSIS_INTERRUPTED' WHERE state='QUEUED'")
        db.execSQL("UPDATE batches SET state='SEND_FAILED',error='@error:SEND_CONFIG' WHERE state='READY'")
        db.execSQL("UPDATE timer SET state='IDLE',endAt=0,remaining=0,token=token+1")
    }
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        synchronized(lock) {
            db.beginTransaction()
            try {
                val migrated=db.rawQuery("SELECT 1 FROM meta WHERE name='legacyExcluded'",null).use { it.moveToFirst() }
                if(!migrated) {
                    legacyExcluded.forEach { pkg ->
                        val label=try { appContext.packageManager.getApplicationLabel(appContext.packageManager.getApplicationInfo(pkg,0)).toString() }
                            catch(_: android.content.pm.PackageManager.NameNotFoundException) { pkg }
                        db.execSQL("INSERT OR IGNORE INTO filters(kind,pkg,app,value) VALUES('app',?,?, '')",arrayOf(pkg,label))
                    }
                    db.execSQL("INSERT INTO meta VALUES('legacyExcluded','1')")
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }
    data class Filter(val id: Long,val kind: String,val pkg: String,val app: String,val value: String)
    fun filters(): List<Filter> = readableDatabase.rawQuery("SELECT id,kind,pkg,app,value FROM filters ORDER BY id DESC",null).use { c -> buildList { while(c.moveToNext()) add(Filter(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4))) } }
    private fun matches(n: Notice,f: Filter): Boolean = f.pkg==n.pkg && when(f.kind) { "app" -> true; "body" -> f.value==n.body; "channel" -> f.value==n.channelId; else -> false }
    fun isFiltered(n: Notice): Boolean = filters().any { matches(n,it) }
    fun addFilter(kind: String,pkg: String,app: String,value: String=""): Boolean = synchronized(lock) {
        require(kind in setOf("app","body","channel"))
        require(kind!="channel" || value.isNotBlank())
        writableDatabase.insertWithOnConflict("filters",null,ContentValues().apply { put("kind",kind); put("pkg",pkg); put("app",app); put("value",value) },SQLiteDatabase.CONFLICT_IGNORE)!=-1L
    }
    fun removeFilter(id: Long) = synchronized(lock) {
        // Forget the previous fingerprint so the next real update is collected after unblocking.
        writableDatabase.execSQL("DELETE FROM latest WHERE nkey IN (SELECT nkey FROM notices WHERE pkg IN (SELECT pkg FROM filters WHERE id=?))",arrayOf(id))
        writableDatabase.delete("filters","id=?",arrayOf(id.toString()))
    }
    private fun notice(c: Cursor) = Notice(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getLong(6),c.getString(7),c.getInt(8)==1,c.getInt(9)==1,sourceTime=c.getLong(10),ongoing=c.getInt(11)==1,channelId=c.getString(12))
    private fun readNotices(sql: String,args: Array<String>?=null): List<Notice> = readableDatabase.rawQuery(sql,args).use { c -> buildList { while(c.moveToNext()) add(notice(c)) } }
    fun visibleNotices(batch: Long): List<Notice> = readNotices("SELECT $columns FROM notices WHERE batch=? AND $visible ORDER BY id",arrayOf(batch.toString()))
    /** Claim and filter atomically. Later rule changes never mutate this already claimed list. */
    fun claimAnalysis(id: Long): List<Notice>? = synchronized(lock) {
        val db=writableDatabase; db.beginTransaction()
        try {
            val result=if(transition(id,"QUEUED","ANALYZING")) visibleNotices(id) else null
            db.setTransactionSuccessful(); result
        } finally { db.endTransaction() }
    }
    fun removeLatest(key: String) = synchronized(lock) {
        writableDatabase.delete("latest","nkey=?",arrayOf(key))
        writableDatabase.delete("code_notifications","nkey=?",arrayOf(key))
    }
    /** The listener's acceptance timestamp is chosen under the same lock as round closing. */
    fun collect(n: Notice): Boolean = synchronized(lock) { insert(n.copy(at=System.currentTimeMillis())) }
    fun insert(n: Notice): Boolean = transaction {
        advanceLocked(n.at)
        val t=timer()
        if(t.state !in setOf("RUNNING","PAUSED") || t.roundId==0L || n.at<t.start || (t.state=="RUNNING" && n.at>=t.end) || isFiltered(n)) return@transaction false
        val db=writableDatabase
        val prev=db.rawQuery("SELECT fingerprint FROM latest WHERE nkey=?",arrayOf(n.key)).use { if(it.moveToFirst()) it.getString(0) else null }
        if(prev==n.fingerprint()) return@transaction false
        val v=ContentValues().apply {
            put("nkey",n.key); put("pkg",n.pkg); put("app",n.app); put("title",n.title); put("body",n.body)
            put("ongoing",if(n.ongoing) 1 else 0); put("channelId",n.channelId); put("sourceTime",n.sourceTime); put("at",n.at); put("kind",n.kind); put("hidden",if(n.hidden) 1 else 0); put("summary",if(n.summary) 1 else 0); put("fingerprint",n.fingerprint()); put("roundId",t.roundId)
        }
        db.insertOrThrow("notices",null,v)
        db.execSQL("INSERT OR REPLACE INTO latest(nkey,fingerprint) VALUES(?,?)",arrayOf(n.key,n.fingerprint()))
        true
    }
    /** Codes get their own ready-to-send record; the active focus window is never changed. */
    fun queueCode(n: Notice,code: String,language: UiLanguage): Long? = transaction {
        if(isFiltered(n) || n.summary || n.hidden) return@transaction null
        val key=VerificationCodes.key(n.pkg,code,n.title,n.body)
        val sourceTime=n.sourceTime.takeIf { it>0 } ?: n.at
        // A live notification can update its display text without becoming a new code.
        val active=readableDatabase.rawQuery("SELECT b.id FROM code_notifications c JOIN batches b ON b.id=c.batchId WHERE c.nkey=? AND b.created<=? AND b.created>?",arrayOf(n.key,sourceTime.toString(),(sourceTime-VerificationCodes.MAX_AGE_MS).toString())).use { if(it.moveToFirst()) it.getLong(0) else null }
        if(active!=null && notices(active).any { it.title==n.title && VerificationCodes.extract(it.title,it.body)==code }) return@transaction active
        val old=writableDatabase.rawQuery("SELECT id FROM batches WHERE instantKey=? AND created<=? AND created>? ORDER BY id DESC LIMIT 1",arrayOf(key,sourceTime.toString(),(sourceTime-VerificationCodes.MAX_AGE_MS).toString())).use { if(it.moveToFirst()) it.getLong(0) else null }
        if(old!=null) {
            writableDatabase.execSQL("INSERT OR REPLACE INTO code_notifications VALUES(?,?)",arrayOf(n.key,old))
            return@transaction old
        }
        val id=writableDatabase.insertOrThrow("batches",null,ContentValues().apply {
            put("state","READY"); put("created",sourceTime); put("target","FEISHU")
            put("instantKey",key); put("message",VerificationCodes.message(n,code,language))
        })
        writableDatabase.insertOrThrow("notices",null,ContentValues().apply {
            put("nkey",n.key); put("pkg",n.pkg); put("app",n.app); put("title",n.title); put("body",n.body)
            put("at",n.at); put("kind",n.kind); put("hidden",0); put("summary",0)
            put("sourceTime",n.sourceTime); put("channelId",n.channelId); put("fingerprint",n.fingerprint()); put("batch",id)
        })
        writableDatabase.execSQL("INSERT OR REPLACE INTO code_notifications VALUES(?,?)",arrayOf(n.key,id))
        id
    }
    private val batchColumns="b.id,b.state,b.created,b.message,b.error,b.ids,b.target,b.roundId,COALESCE(r.startAt,0),COALESCE(r.endAt,0),COALESCE(r.mode,'SINGLE'),b.instantKey"
    private val batchJoin="batches b LEFT JOIN focus_rounds r ON r.id=b.roundId"
    private fun batch(c: Cursor)=Batch(c.getLong(0),c.getString(1),c.getLong(2),c.getString(3),c.getString(4),c.getString(5),DeliveryTarget.fromStored(c.getString(6)),c.getLong(7),c.getLong(8),c.getLong(9),FocusMode.fromStored(c.getString(10)),c.getString(11))
    fun batches(): List<Batch> = readBatches("SELECT $batchColumns FROM $batchJoin ORDER BY b.id DESC LIMIT 80")
    fun claimableBatches(): List<Batch> = readBatches("SELECT $batchColumns FROM $batchJoin WHERE b.state IN ('QUEUED','READY') ORDER BY b.id")
    private fun readBatches(sql: String)=readableDatabase.rawQuery(sql,null).use { c -> buildList { while(c.moveToNext()) add(batch(c)) } }
    fun feishuEchoes(): List<String> = readableDatabase.rawQuery("SELECT message FROM batches WHERE target='FEISHU' AND state IN ('SENDING','DONE','UNCERTAIN') AND message<>''",null).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
    fun get(id: Long): Batch? = readableDatabase.rawQuery("SELECT $batchColumns FROM $batchJoin WHERE b.id=?",arrayOf(id.toString())).use { if(it.moveToFirst()) batch(it) else null }
    /** Only current-round unassigned notices can enter a manual analysis. Failed older batches never block it. */
    fun createBatch(now: Long=System.currentTimeMillis(),expectedToken: Long?=null): Long? = transaction {
        advanceLocked(now)
        val t=timer()
        if(expectedToken!=null && t.token!=expectedToken) return@transaction null
        if(t.state !in setOf("RUNNING","PAUSED")) return@transaction null
        snapshotLocked(t.roundId,now) ?: writableDatabase.rawQuery("SELECT id FROM batches WHERE roundId=? AND state IN ('QUEUED','ANALYZING') ORDER BY id DESC LIMIT 1",arrayOf(t.roundId.toString())).use { if(it.moveToFirst()) it.getLong(0) else null }
    }
    private fun snapshotLocked(round: Long,now: Long): Long? {
        val db=writableDatabase
        val has=db.rawQuery("SELECT 1 FROM notices WHERE roundId=? AND batch IS NULL AND $visible LIMIT 1",arrayOf(round.toString())).use { it.moveToFirst() }
        if(!has) return null
        val id=db.insertOrThrow("batches",null,ContentValues().apply { put("state","QUEUED"); put("created",now); put("target",targetProvider().name); put("roundId",round) })
        db.execSQL("UPDATE notices SET batch=? WHERE roundId=? AND batch IS NULL",arrayOf(id,round))
        return id
    }
    fun notices(batch: Long): List<Notice> = readNotices("SELECT $columns FROM notices WHERE batch=? ORDER BY id",arrayOf(batch.toString()))
    fun transition(id: Long, from: String, to: String, message: String?=null, error: String="", ids: String?=null): Boolean = synchronized(lock) {
        val v=ContentValues().apply { put("state",to); put("error",error); message?.let { put("message",it) }; ids?.let { put("ids",it) } }
        writableDatabase.update("batches",v,"id=? AND state=?",arrayOf(id.toString(),from))==1
    }
    fun retry(id: Long,target: DeliveryTarget?=null): Boolean = synchronized(lock) {
        val b=get(id) ?: return@synchronized false
        if(b.instantKey.isNotEmpty() && target!=null && target!=DeliveryTarget.FEISHU) return@synchronized false
        val next=when(b.state) { "FAILED" -> "QUEUED"; "SEND_FAILED","UNCERTAIN" -> "READY"; else -> return@synchronized false }
        val values=ContentValues().apply { put("state",next); put("error",""); if(target!=null) put("target",target.name) }
        writableDatabase.update("batches",values,"id=? AND state=?",arrayOf(id.toString(),b.state))==1
    }
    fun recover() = synchronized(lock) {
        writableDatabase.execSQL("UPDATE batches SET state='FAILED', error='@error:ANALYSIS_INTERRUPTED' WHERE state='ANALYZING'")
        writableDatabase.execSQL("UPDATE batches SET state='UNCERTAIN', error='@error:SEND_UNCERTAIN' WHERE state='SENDING' AND target='FEISHU'")
        writableDatabase.execSQL("UPDATE batches SET state='SEND_FAILED', error='@error:LOCAL_INTERRUPTED' WHERE state='SENDING' AND target='LOCAL'")
    }
    fun pendingCount(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM notices WHERE roundId=(SELECT roundId FROM timer WHERE id=1 AND state IN ('RUNNING','PAUSED')) AND batch IS NULL AND $visible",null).use { it.moveToFirst(); it.getLong(0) }
    fun apps(): List<Pair<String,String>> = readableDatabase.rawQuery("SELECT pkg,MAX(app) FROM notices GROUP BY pkg ORDER BY MAX(app)",null).use { c -> buildList { while(c.moveToNext()) add(c.getString(0) to c.getString(1)) } }
    fun prune() = synchronized(lock) {
        val cutoff=System.currentTimeMillis()-30L*24*60*60*1000
        writableDatabase.execSQL("DELETE FROM notices WHERE batch IN (SELECT id FROM batches WHERE state IN ('DONE','EMPTY') AND created<?)",arrayOf(cutoff))
        writableDatabase.execSQL("DELETE FROM batches WHERE state IN ('DONE','EMPTY') AND created<?",arrayOf(cutoff))
        writableDatabase.execSQL("DELETE FROM code_notifications WHERE batchId NOT IN (SELECT id FROM batches)")
        writableDatabase.execSQL("DELETE FROM focus_rounds WHERE closed=1 AND NOT EXISTS (SELECT 1 FROM batches WHERE roundId=focus_rounds.id)")
    }
    fun clear(): Boolean = synchronized(lock) {
        val busy=readableDatabase.rawQuery("SELECT 1 FROM batches WHERE state IN ('ANALYZING','SENDING') LIMIT 1",null).use { it.moveToFirst() }
        if(busy) return@synchronized false
        writableDatabase.beginTransaction()
        try { writableDatabase.execSQL("DELETE FROM code_notifications"); writableDatabase.execSQL("DELETE FROM notices"); writableDatabase.execSQL("DELETE FROM latest"); writableDatabase.execSQL("DELETE FROM batches"); writableDatabase.execSQL("DELETE FROM focus_rounds WHERE id<>(SELECT roundId FROM timer WHERE id=1)"); writableDatabase.setTransactionSuccessful() } finally { writableDatabase.endTransaction() }; true
    }
    data class Timer(val state: String,val end: Long,val remaining: Long,val token: Long,val start: Long=0,val mode: FocusMode=FocusMode.SINGLE,val interval: Long=1500000,val roundId: Long=0)
    fun timer(): Timer = readableDatabase.rawQuery("SELECT state,endAt,remaining,token,startAt,mode,intervalMs,roundId FROM timer WHERE id=1",null).use { it.moveToFirst(); Timer(it.getString(0),it.getLong(1),it.getLong(2),it.getLong(3),it.getLong(4),FocusMode.fromStored(it.getString(5)),it.getLong(6),it.getLong(7)) }
    private fun writeTimer(t: Timer) { writableDatabase.execSQL("UPDATE timer SET state=?,endAt=?,remaining=?,token=?,startAt=?,mode=?,intervalMs=?,roundId=? WHERE id=1",arrayOf(t.state,t.end,t.remaining,t.token,t.start,t.mode.name,t.interval,t.roundId)) }
    private fun nextToken(t: Timer,now: Long)=maxOf(now,t.token+1)
    private fun newRound(start: Long,end: Long,mode: FocusMode,interval: Long): Long = writableDatabase.insertOrThrow("focus_rounds",null,ContentValues().apply { put("startAt",start); put("endAt",end); put("mode",mode.name); put("intervalMs",interval) })
    private fun <T> transaction(action: ()->T): T = synchronized(lock) {
        val db=writableDatabase; db.beginTransaction()
        try { val result=action(); db.setTransactionSuccessful(); result } finally { db.endTransaction() }
    }
    fun startFocus(minutes: Int,mode: FocusMode,now: Long=System.currentTimeMillis()): Timer = transaction {
        require(minutes in 1..180)
        advanceLocked(now)
        val t=timer()
        if(t.state=="RUNNING") return@transaction t
        if(t.state=="PAUSED") {
            val resumed=t.copy(state="RUNNING",end=now+t.remaining,token=nextToken(t,now))
            writableDatabase.execSQL("UPDATE focus_rounds SET endAt=? WHERE id=?",arrayOf(resumed.end,t.roundId))
            writeTimer(resumed); return@transaction resumed
        }
        writableDatabase.execSQL("DELETE FROM notices WHERE batch IS NULL")
        writableDatabase.execSQL("DELETE FROM latest")
        val duration=minutes*60_000L
        val round=newRound(now,now+duration,mode,duration)
        val started=Timer("RUNNING",now+duration,duration,nextToken(t,now),now,mode,duration,round)
        writeTimer(started); started
    }
    /** Change how this round ends without resetting its clock or moving its notices. */
    fun changeFocusMode(mode: FocusMode,now: Long=System.currentTimeMillis()): Timer = transaction {
        advanceLocked(now)
        val t=timer()
        if(t.state !in setOf("RUNNING","PAUSED")) return@transaction t
        val changed=t.copy(mode=mode)
        writableDatabase.execSQL("UPDATE focus_rounds SET mode=? WHERE id=?",arrayOf(mode.name,t.roundId))
        writeTimer(changed); changed
    }
    fun pauseFocus(now: Long=System.currentTimeMillis()): Timer = transaction {
        advanceLocked(now)
        val t=timer()
        if(t.state!="RUNNING") return@transaction t
        val paused=t.copy(state="PAUSED",end=0,remaining=(t.end-now).coerceAtLeast(0),token=nextToken(t,now))
        writableDatabase.execSQL("UPDATE focus_rounds SET endAt=0 WHERE id=?",arrayOf(t.roundId))
        writeTimer(paused); paused
    }
    fun stopFocus(now: Long=System.currentTimeMillis(),expectedToken: Long?=null): Boolean = transaction {
        advanceLocked(now)
        val t=timer()
        if(expectedToken!=null && t.token!=expectedToken) return@transaction false
        if(t.state !in setOf("RUNNING","PAUSED")) return@transaction false
        closeRound(t,now)
        writeTimer(t.copy(state="IDLE",end=0,remaining=0,token=nextToken(t,now),roundId=0,start=0))
        true
    }
    private fun closeRound(t: Timer,end: Long) {
        snapshotLocked(t.roundId,end)
        writableDatabase.execSQL("UPDATE focus_rounds SET endAt=?,closed=1 WHERE id=?",arrayOf(end,t.roundId))
        // Excluded, never analyzed records need no historical cache after their collection window closes.
        writableDatabase.execSQL("DELETE FROM notices WHERE roundId=? AND batch IS NULL",arrayOf(t.roundId))
        writableDatabase.execSQL("DELETE FROM focus_rounds WHERE id=? AND NOT EXISTS (SELECT 1 FROM batches WHERE roundId=focus_rounds.id)",arrayOf(t.roundId))
        writableDatabase.execSQL("DELETE FROM latest")
    }
    private fun advanceLocked(now: Long): Boolean {
        val t=timer()
        if(t.state!="RUNNING" || t.end>now) return false
        closeRound(t,t.end)
        if(t.mode==FocusMode.SINGLE) writeTimer(t.copy(state="IDLE",end=0,remaining=0,token=nextToken(t,now),start=0,roundId=0))
        else {
            // All skipped windows are empty: capture and closing share this transaction lock.
            val interval=t.interval.coerceAtLeast(60_000L)
            val start=t.end+((now-t.end)/interval)*interval
            val end=start+interval
            val round=newRound(start,end,t.mode,interval)
            writeTimer(Timer("RUNNING",end,end-now,nextToken(t,now),start,t.mode,interval,round))
        }
        return true
    }
    fun advanceTimer(token: Long?=null,now: Long=System.currentTimeMillis()): Boolean = transaction {
        if(token!=null && timer().token!=token) false else advanceLocked(now)
    }
    fun adjustRemaining(minutes: Int,now: Long=System.currentTimeMillis()): Timer? = transaction {
        require(minutes in 1..180)
        advanceLocked(now)
        val t=timer()
        if(t.state !in setOf("RUNNING","PAUSED")) return@transaction null
        val duration=minutes*60_000L
        val next=t.copy(end=if(t.state=="RUNNING") now+duration else 0,remaining=duration,token=nextToken(t,now))
        writableDatabase.execSQL("UPDATE focus_rounds SET endAt=? WHERE id=?",arrayOf(next.end,t.roundId))
        writeTimer(next); next
    }
    fun recent(): List<Notice> = readNotices("SELECT $columns FROM notices WHERE $visible ORDER BY id DESC LIMIT 120")
}
