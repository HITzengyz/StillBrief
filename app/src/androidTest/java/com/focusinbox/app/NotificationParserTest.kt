package com.focusinbox.app

import android.app.Notification
import android.app.Person
import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationParserTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().context
    private fun notification(extra: Bundle = Bundle(), category: String? = null, ongoing: Boolean = false): Notification =
        Notification.Builder(context,"parser-test").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setCategory(category).setOngoing(ongoing).addExtras(extra).build()
    private fun message(text: String?, sender: String = "张三"): Bundle = Bundle().apply {
        if(text != null) putCharSequence("text",text)
        if(Build.VERSION.SDK_INT >= 28) putParcelable("sender_person",Person.Builder().setName(sender).build())
        else putCharSequence("sender",sender)
    }
    private fun messages(group: Boolean? = null, title: String? = null, vararg bodies: Bundle) = Bundle().apply {
        putParcelableArray(Notification.EXTRA_MESSAGES,bodies)
        group?.let { putBoolean("android.isGroupConversation",it) }
        title?.let { putCharSequence("android.conversationTitle",it) }
    }
    @Test fun explicitPrivateOverridesConversationTitle() {
        val parsed=NotificationParser.parse(notification(messages(false,"好友备注",message("在吗"))),"com.tencent.mm")!!
        assertEquals("private",parsed.kind); assertTrue(parsed.body.contains("在吗"))
        assertTrue(Notice(key="key",pkg="com.tencent.mm",app="微信",title=parsed.title,body=parsed.body,at=1,kind=parsed.kind).hardKeep)
    }
    @Test fun explicitGroupStaysGroup() {
        assertEquals("group",NotificationParser.parse(notification(messages(true,"群聊",message("今天吃什么"))),"p")!!.kind)
    }
    @Test fun missingGroupMetadataFallsBackToTitle() {
        assertEquals("group",NotificationParser.parse(notification(messages(null,"群聊",message("hi"))),"p")!!.kind)
    }
    @Test fun missedCallPreserved() {
        val parsed=NotificationParser.parse(notification(category=Notification.CATEGORY_MISSED_CALL),"dialer")!!
        assertEquals("missed_call",parsed.kind)
    }
    @Test fun ongoingCallNotFiltered() {
        val n=notification(category=Notification.CATEGORY_CALL,ongoing=true)
        assertTrue((n.flags and Notification.FLAG_ONGOING_EVENT)!=0)
        assertEquals("call",NotificationParser.parse(n,"dialer")!!.kind)
    }
    @Test fun everyTextLineRemainsEvenWithExpandedText() {
        val extras=Bundle().apply {
            putCharSequence(Notification.EXTRA_BIG_TEXT,"第一条")
            putCharSequenceArray(Notification.EXTRA_TEXT_LINES,arrayOf("第一条","第二条","第三条"))
            putCharSequence(Notification.EXTRA_TEXT,"折叠正文")
        }
        val body=NotificationParser.parse(notification(extras),"p")!!.body
        listOf("第一条","第二条","第三条","折叠正文").forEach { assertTrue(body.contains(it)) }
    }
    @Test fun mediaWithoutBodyUsesPlaceholderAndPerson() {
        val media=message(null).apply { putString("type","image/jpeg"); putParcelable("uri",android.net.Uri.parse("content://never-read/media")) }
        val parsed=NotificationParser.parse(notification(messages(false,null,media)),"p")!!
        assertEquals("张三：[图片/语音/媒体消息]",parsed.body); assertTrue(parsed.hidden); assertEquals("private",parsed.kind)
    }
    @Test fun larkOwnMarkerDoesNotProduceRecord() {
        val extras=Bundle().apply { putCharSequence(Notification.EXTRA_TITLE,"群机器人"); putCharSequence(Notification.EXTRA_TEXT,NotificationParser.MARKER+"有1条消息") }
        assertNull(NotificationParser.parse(notification(extras),"com.ss.android.lark"))
        assertNull(NotificationParser.parse(notification(messages(false,null,message(NotificationParser.MARKER+"提醒"))),"com.lark.overseas"))
    }
    @Test fun mixedMessagingRetainsHumanText() {
        val parsed=NotificationParser.parse(notification(messages(true,"工作群",message(NotificationParser.MARKER+"提醒","息间"),message("请看文档","同事"))),"com.ss.android.lark")!!
        assertTrue(parsed.body.contains("同事：请看文档")); assertFalse(parsed.body.contains(NotificationParser.MARKER))
    }
    @Test fun mixedTextLinesRetainHumanText() {
        val extras=Bundle().apply { putCharSequenceArray(Notification.EXTRA_TEXT_LINES,arrayOf(NotificationParser.MARKER+"提醒","同事：请回复")) }
        assertEquals("同事：请回复",NotificationParser.parse(notification(extras),"com.lark.overseas")!!.body)
    }
    @Test fun markerInAnotherAppIsOrdinaryData() {
        val raw=NotificationParser.MARKER+"其他应用中的普通消息"
        val extras=Bundle().apply { putCharSequence(Notification.EXTRA_TEXT,raw) }
        assertEquals(raw,NotificationParser.parse(notification(extras),"other.app")!!.body)
    }
}
