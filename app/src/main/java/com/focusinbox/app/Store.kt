package com.focusinbox.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Batch(val id: Long, val state: String, val created: Long, val message: String, val error: String, val ids: String)
class Store(context: Context, databaseName: String = "inbox.db") : SQLiteOpenHelper(context, databaseName, null, 1) {
    companion object { private val lock = Any() }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE notices(id INTEGER PRIMARY KEY AUTOINCREMENT, nkey TEXT NOT NULL, pkg TEXT NOT NULL, app TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, at INTEGER NOT NULL, kind TEXT NOT NULL, hidden INTEGER NOT NULL, summary INTEGER NOT NULL, sourceTime INTEGER NOT NULL DEFAULT 0, fingerprint TEXT NOT NULL, batch INTEGER)")
        db.execSQL("CREATE INDEX notices_batch ON notices(batch)")
        db.execSQL("CREATE TABLE latest(nkey TEXT PRIMARY KEY, fingerprint TEXT NOT NULL)")
        db.execSQL("CREATE TABLE batches(id INTEGER PRIMARY KEY AUTOINCREMENT, state TEXT NOT NULL, created INTEGER NOT NULL, message TEXT NOT NULL DEFAULT '', error TEXT NOT NULL DEFAULT '', ids TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE timer(id INTEGER PRIMARY KEY CHECK(id=1), state TEXT NOT NULL, endAt INTEGER NOT NULL, remaining INTEGER NOT NULL, token INTEGER NOT NULL)")
        db.execSQL("INSERT INTO timer VALUES(1,'IDLE',0,0,0)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) { error("Unsupported database migration") }
    fun removeLatest(key: String) = synchronized(lock) { writableDatabase.delete("latest","nkey=?",arrayOf(key)) }
    fun insert(n: Notice): Boolean = synchronized(lock) {
        val db = writableDatabase; db.beginTransaction()
        try {
            val prev = db.rawQuery("SELECT fingerprint FROM latest WHERE nkey=?", arrayOf(n.key)).use { if(it.moveToFirst()) it.getString(0) else null }
            if(prev == n.fingerprint()) { db.setTransactionSuccessful(); false } else {
                val v = ContentValues().apply {
                    put("nkey",n.key); put("pkg",n.pkg); put("app",n.app); put("title",n.title); put("body",n.body)
                    put("sourceTime",n.sourceTime); put("at",n.at); put("kind",n.kind); put("hidden",if(n.hidden) 1 else 0); put("summary",if(n.summary) 1 else 0); put("fingerprint",n.fingerprint())
                }
                db.insertOrThrow("notices",null,v)
                db.execSQL("INSERT OR REPLACE INTO latest(nkey,fingerprint) VALUES(?,?)", arrayOf(n.key,n.fingerprint()))
                db.setTransactionSuccessful(); true
            }
        } finally { db.endTransaction() }
    }
    private fun batch(c: Cursor) = Batch(c.getLong(0),c.getString(1),c.getLong(2),c.getString(3),c.getString(4),c.getString(5))
    fun batches(): List<Batch> = readableDatabase.rawQuery("SELECT id,state,created,message,error,ids FROM batches ORDER BY id DESC LIMIT 80",null).use { c -> buildList { while(c.moveToNext()) add(batch(c)) } }
    fun get(id: Long): Batch? = readableDatabase.rawQuery("SELECT id,state,created,message,error,ids FROM batches WHERE id=?",arrayOf(id.toString())).use { if(it.moveToFirst()) batch(it) else null }
    /** The transaction fixes a snapshot. Later arrivals stay unassigned for the next batch. */
    fun createBatch(): Long? = synchronized(lock) {
        val db=writableDatabase; db.beginTransaction()
        try {
            val active=db.rawQuery("SELECT id FROM batches WHERE state NOT IN ('DONE','EMPTY') ORDER BY id LIMIT 1",null).use { if(it.moveToFirst()) it.getLong(0) else null }
            if(active!=null) { db.setTransactionSuccessful(); return@synchronized active }
            val has=db.rawQuery("SELECT 1 FROM notices WHERE batch IS NULL LIMIT 1",null).use { it.moveToFirst() }
            if(!has) { db.setTransactionSuccessful(); return@synchronized null }
            val id=db.insertOrThrow("batches",null,ContentValues().apply { put("state","QUEUED"); put("created",System.currentTimeMillis()) })
            db.execSQL("UPDATE notices SET batch=? WHERE batch IS NULL",arrayOf(id))
            db.setTransactionSuccessful(); id
        } finally { db.endTransaction() }
    }
    fun notices(batch: Long): List<Notice> = readableDatabase.rawQuery("SELECT id,nkey,pkg,app,title,body,at,kind,hidden,summary,sourceTime FROM notices WHERE batch=? ORDER BY id",arrayOf(batch.toString())).use { c -> buildList { while(c.moveToNext()) add(Notice(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getLong(6),c.getString(7),c.getInt(8)==1,c.getInt(9)==1,sourceTime=c.getLong(10))) } }
    fun transition(id: Long, from: String, to: String, message: String?=null, error: String="", ids: String?=null): Boolean = synchronized(lock) {
        val v=ContentValues().apply { put("state",to); put("error",error); message?.let { put("message",it) }; ids?.let { put("ids",it) } }
        writableDatabase.update("batches",v,"id=? AND state=?",arrayOf(id.toString(),from))==1
    }
    fun retry(id: Long): Boolean = synchronized(lock) {
        val b=get(id)?:return@synchronized false
        when(b.state) { "FAILED" -> transition(id,"FAILED","QUEUED"); "UNCERTAIN" -> transition(id,"UNCERTAIN","READY"); "SEND_FAILED" -> transition(id,"SEND_FAILED","READY"); else -> false }
    }
    fun recover() = synchronized(lock) {
        writableDatabase.execSQL("UPDATE batches SET state='FAILED', error='分析被中断，请手动重试' WHERE state='ANALYZING'")
        writableDatabase.execSQL("UPDATE batches SET state='UNCERTAIN', error='发送被中断，送达情况不确定；重试可能重复' WHERE state='SENDING'")
    }
    fun pendingCount(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM notices WHERE batch IS NULL",null).use { it.moveToFirst(); it.getLong(0) }
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
    fun recent(): List<Notice> = readableDatabase.rawQuery("SELECT id,nkey,pkg,app,title,body,at,kind,hidden,summary,sourceTime FROM notices ORDER BY id DESC LIMIT 120",null).use { c -> buildList { while(c.moveToNext()) add(Notice(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getLong(6),c.getString(7),c.getInt(8)==1,c.getInt(9)==1,sourceTime=c.getLong(10))) } }
    fun finishAndSnapshot(token: Long): Boolean = synchronized(lock) {
        val db=writableDatabase; db.beginTransaction()
        try { val finished=finishTimer(token); if(finished) createBatch(); db.setTransactionSuccessful(); finished }
        finally { db.endTransaction() }
    }
    fun finishTimer(token: Long): Boolean = synchronized(lock) {
        writableDatabase.update("timer",ContentValues().apply { put("state","IDLE"); put("endAt",0); put("remaining",0) },"id=1 AND state='RUNNING' AND token=? AND endAt<=?",arrayOf(token.toString(),System.currentTimeMillis().toString()))==1
    }
}
