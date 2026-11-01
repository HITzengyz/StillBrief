package com.focusinbox.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeishuEchoStoreTest {
    @Test fun inFlightDeliveredAndUncertainFeishuEchoesSurviveReopen() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="inbox-feishu-echo-test.db"
        context.deleteDatabase(name)
        try {
            val expected=setOf("In flight\nSummary","Delivered summary","Uncertain summary")
            Store(context,name,emptySet()).use { db ->
                val rows=listOf(
                    Triple("SENDING","In flight\nSummary","FEISHU"),Triple("DONE","Delivered summary","FEISHU"),
                    Triple("UNCERTAIN","Uncertain summary","FEISHU"),Triple("SENDING","Local only","LOCAL"),
                    Triple("ANALYZING","Not sent","FEISHU"),Triple("SEND_FAILED","Rejected","FEISHU"))
                rows.forEach { (state,message,target) -> db.writableDatabase.execSQL("INSERT INTO batches(state,created,message,target) VALUES(?,1,?,?)",arrayOf(state,message,target)) }
                assertEquals(expected,db.feishuEchoes().toSet())
            }
            Store(context,name,emptySet()).use { assertEquals(expected,it.feishuEchoes().toSet()) }
        } finally { context.deleteDatabase(name) }
    }
}
