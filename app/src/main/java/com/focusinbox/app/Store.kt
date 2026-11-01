package com.focusinbox.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Batch(val id: Long, val state: String, val created: Long, val message: String, val error: String, val ids: String,val target: DeliveryTarget=DeliveryTarget.FEISHU)
class Store(context: Context, databaseName: String = "inbox.db", private val legacyExcluded: Set<String> = Settings(context).excluded,private val targetProvider: ()->DeliveryTarget = { Settings(context).destination }) : SQLiteOpenHelper(context, databaseName, null, 3) {
    companion object { private val lock = Any() }
    private val appContext=context.applicationContext
    private val columns="id,nkey,pkg,app,title,body,at,kind,hidden,summary,sourceTime,ongoing,channelId"
    private val visible="NOT EXISTS (SELECT 1 FROM filters f WHERE f.pkg=notices.pkg AND (f.kind='app' OR (f.kind='body' AND f.value=notices.body) OR (f.kind='channel' AND f.value=notices.channelId)))"
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE notices(id INTEGER PRIMARY KEY AUTOINCREMENT, nkey TEXT NOT NULL, pkg TEXT NOT NULL, app TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, at INTEGER NOT NULL, kind TEXT NOT NULL, hidden INTEGER NOT NULL, summary INTEGER NOT NULL, sourceTime INTEGER NOT NULL DEFAULT 0, ongoing INTEGER NOT NULL DEFAULT 0, channelId TEXT NOT NULL DEFAULT '', fingerprint TEXT NOT NULL, batch INTEGER)")
        db.execSQL("CREATE INDEX notices_batch ON notices(batch)")
        db.execSQL("CREATE TABLE latest(nkey TEXT PRIMARY KEY, fingerprint TEXT NOT NULL)")
        db.execSQL("CREATE TABLE batches(id INTEGER PRIMARY KEY AUTOINCREMENT, state TEXT NOT NULL, created INTEGER NOT NULL, message TEXT NOT NULL DEFAULT '', error TEXT NOT NULL DEFAULT '', ids TEXT NOT NULL DEFAULT '', target TEXT NOT NULL DEFAULT 'FEISHU')")
        db.execSQL("CREATE TABLE timer(id INTEGER PRIMARY KEY CHECK(id=1), state TEXT NOT NULL, endAt INTEGER NOT NULL, remaining INTEGER NOT NULL, token INTEGER NOT NULL)")
        db.execSQL("INSERT INTO timer VALUES(1,'IDLE',0,0,0)")
        createFilters(db)
    }
    private fun createFilters(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE filters(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, pkg TEXT NOT NULL, app TEXT NOT NULL, value TEXT NOT NULL, UNIQUE(kind,pkg,value))")
        db.execSQL("CREATE TABLE meta(name TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        check(old in 1..2 && new==3) { "Unsupported database migration" }
        if(old<2) {
            db.execSQL("ALTER TABLE notices ADD COLUMN ongoing INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notices ADD COLUMN channelId TEXT NOT NULL DEFAULT ''")
            createFilters(db)
        }
        db.execSQL("ALTER TABLE batches ADD COLUMN target TEXT NOT NULL DEFAULT 'FEISHU'")
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
    fun removeLatest(key: String) = synchronized(lock) { writableDatabase.delete("latest","nkey=?",arrayOf(key)) }
    fun insert(n: Notice): Boolean = synchronized(lock) {
        if(isFiltered(n)) return@synchronized false
        val db = writableDatabase; db.beginTransaction()
        try {
            val prev = db.rawQuery("SELECT fingerprint FROM latest WHERE nkey=?", arrayOf(n.key)).use { if(it.moveToFirst()) it.getString(0) else null }
            if(prev == n.fingerprint()) { db.setTransactionSuccessful(); false } else {
                val v = ContentValues().apply {
                    put("nkey",n.key); put("pkg",n.pkg); put("app",n.app); put("title",n.title); put("body",n.body)
                    put("ongoing",if(n.ongoing) 1 else 0); put("channelId",n.channelId); put("sourceTime",n.sourceTime); put("at",n.at); put("kind",n.kind); put("hidden",if(n.hidden) 1 else 0); put("summary",if(n.summary) 1 else 0); put("fingerprint",n.fingerprint())
                }
                db.insertOrThrow("notices",null,v)
                db.execSQL("INSERT OR REPLACE INTO latest(nkey,fingerprint) VALUES(?,?)", arrayOf(n.key,n.fingerprint()))
                db.setTransactionSuccessful(); true
            }
        } finally { db.endTransaction() }
    }
    private fun batch(c: Cursor) = Batch(c.getLong(0),c.getString(1),c.getLong(2),c.getString(3),c.getString(4),c.getString(5),DeliveryTarget.fromStored(c.getString(6)))
    fun batches(): List<Batch> = readableDatabase.rawQuery("SELECT id,state,created,message,error,ids,target FROM batches ORDER BY id DESC LIMIT 80",null).use { c -> buildList { while(c.moveToNext()) add(batch(c)) } }
    fun get(id: Long): Batch? = readableDatabase.rawQuery("SELECT id,state,created,message,error,ids,target FROM batches WHERE id=?",arrayOf(id.toString())).use { if(it.moveToFirst()) batch(it) else null }
    /** The transaction fixes a snapshot. Later arrivals stay unassigned for the next batch. */
    fun createBatch(): Long? = synchronized(lock) {
        val db=writableDatabase; db.beginTransaction()
        try {
            val active=db.rawQuery("SELECT id FROM batches WHERE state NOT IN ('DONE','EMPTY') ORDER BY id LIMIT 1",null).use { if(it.moveToFirst()) it.getLong(0) else null }
            if(active!=null) { db.setTransactionSuccessful(); return@synchronized active }
            val has=db.rawQuery("SELECT 1 FROM notices WHERE batch IS NULL AND $visible LIMIT 1",null).use { it.moveToFirst() }
            if(!has) { db.setTransactionSuccessful(); return@synchronized null }
            val id=db.insertOrThrow("batches",null,ContentValues().apply { put("state","QUEUED"); put("created",System.currentTimeMillis()); put("target",targetProvider().name) })
            db.execSQL("UPDATE notices SET batch=? WHERE batch IS NULL AND $visible",arrayOf(id))
            db.setTransactionSuccessful(); id
        } finally { db.endTransaction() }
    }
    fun notices(batch: Long): List<Notice> = readNotices("SELECT $columns FROM notices WHERE batch=? ORDER BY id",arrayOf(batch.toString()))
    fun transition(id: Long, from: String, to: String, message: String?=null, error: String="", ids: String?=null): Boolean = synchronized(lock) {
        val v=ContentValues().apply { put("state",to); put("error",error); message?.let { put("message",it) }; ids?.let { put("ids",it) } }
        writableDatabase.update("batches",v,"id=? AND state=?",arrayOf(id.toString(),from))==1
    }
    fun retry(id: Long,target: DeliveryTarget?=null): Boolean = synchronized(lock) {
        val b=get(id) ?: return@synchronized false
        val next=when(b.state) { "FAILED" -> "QUEUED"; "SEND_FAILED","UNCERTAIN" -> "READY"; else -> return@synchronized false }
        val values=ContentValues().apply { put("state",next); put("error",""); if(target!=null) put("target",target.name) }
        writableDatabase.update("batches",values,"id=? AND state=?",arrayOf(id.toString(),b.state))==1
    }
    fun recover() = synchronized(lock) {
        writableDatabase.execSQL("UPDATE batches SET state='FAILED', error='@error:ANALYSIS_INTERRUPTED' WHERE state='ANALYZING'")
        writableDatabase.execSQL("UPDATE batches SET state='UNCERTAIN', error='@error:SEND_UNCERTAIN' WHERE state='SENDING' AND target='FEISHU'")
        writableDatabase.execSQL("UPDATE batches SET state='SEND_FAILED', error='@error:LOCAL_INTERRUPTED' WHERE state='SENDING' AND target='LOCAL'")
    }
    fun pendingCount(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM notices WHERE batch IS NULL AND $visible",null).use { it.moveToFirst(); it.getLong(0) }
    fun apps(): List<Pair<String,String>> = readableDatabase.rawQuery("SELECT pkg,MAX(app) FROM notices GROUP BY pkg ORDER BY MAX(app)",null).use { c -> buildList { while(c.moveToNext()) add(c.getString(0) to c.getString(1)) } }
    fun prune() = synchronized(lock) {
        val cutoff=System.currentTimeMillis()-30L*24*60*60*1000
        writableDatabase.execSQL("DELETE FROM notices WHERE batch IN (SELECT id FROM batches WHERE state IN ('DONE','EMPTY') AND created<?)",arrayOf(cutoff))
        writableDatabase.execSQL("DELETE FROM batches WHERE state IN ('DONE','EMPTY') AND created<?",arrayOf(cutoff))
    }
    fun clear(): Boolean = synchronized(lock) {
        val busy=readableDatabase.rawQuery("SELECT 1 FROM batches WHERE state IN ('ANALYZING','SENDING') LIMIT 1",null).use { it.moveToFirst() }
        if(busy) return@synchronized false
        writableDatabase.beginTransaction()
        try { writableDatabase.execSQL("DELETE FROM notices"); writableDatabase.execSQL("DELETE FROM latest"); writableDatabase.execSQL("DELETE FROM batches"); writableDatabase.setTransactionSuccessful() } finally { writableDatabase.endTransaction() }; true
    }
    data class Timer(val state: String,val end: Long,val remaining: Long,val token: Long)
    fun timer(): Timer = readableDatabase.rawQuery("SELECT state,endAt,remaining,token FROM timer WHERE id=1",null).use { it.moveToFirst(); Timer(it.getString(0),it.getLong(1),it.getLong(2),it.getLong(3)) }
    fun setTimer(state: String,end: Long,remaining: Long,token: Long) = synchronized(lock) { writableDatabase.execSQL("UPDATE timer SET state=?,endAt=?,remaining=?,token=? WHERE id=1",arrayOf(state,end,remaining,token)) }
    fun recent(): List<Notice> = readNotices("SELECT $columns FROM notices WHERE $visible ORDER BY id DESC LIMIT 120")
    fun adjustRemaining(minutes: Int,now: Long=System.currentTimeMillis()): Timer? = synchronized(lock) {
        require(minutes in 1..180)
        val t=timer()
        if(t.state !in setOf("RUNNING","PAUSED")) return@synchronized null
        val duration=minutes*60_000L
        val next=Timer(t.state,if(t.state=="RUNNING") now+duration else 0,duration,maxOf(now,t.token+1))
        setTimer(next.state,next.end,next.remaining,next.token)
        next
    }
    fun finishAndSnapshot(token: Long): Boolean = synchronized(lock) {
        val db=writableDatabase; db.beginTransaction()
        try { val finished=finishTimer(token); if(finished) createBatch(); db.setTransactionSuccessful(); finished }
        finally { db.endTransaction() }
    }
    fun finishTimer(token: Long): Boolean = synchronized(lock) {
        writableDatabase.update("timer",ContentValues().apply { put("state","IDLE"); put("endAt",0); put("remaining",0) },"id=1 AND state='RUNNING' AND token=? AND endAt<=?",arrayOf(token.toString(),System.currentTimeMillis().toString()))==1
    }
}
