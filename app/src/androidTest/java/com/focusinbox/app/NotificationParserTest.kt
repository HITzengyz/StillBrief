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
    @Test fun missedCallRetainsExpandedTitleSubtextAndFullNumber() {
        val extras=Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE,"未接来电")
            putCharSequence(Notification.EXTRA_TITLE_BIG,"张三")
            putCharSequence(Notification.EXTRA_SUB_TEXT,"+86 13812345678")
        }
        val parsed=NotificationParser.parse(notification(extras,Notification.CATEGORY_MISSED_CALL),"dialer")!!
        assertEquals("未接来电",parsed.title); assertTrue(parsed.body.contains("张三"))
        assertTrue(parsed.body.contains("+86 13812345678")); assertFalse(parsed.hidden)
    }
    @Test fun callStyleRetainsOnlyProvidedCallerIdentityWithoutResolvingUri() {
        if(Build.VERSION.SDK_INT < 31) return
        val extras=Bundle().apply {
            putParcelable(Notification.EXTRA_CALL_PERSON,Person.Builder().setName("Alex").setUri("tel:+12025550123").build())
        }
        val parsed=NotificationParser.parse(notification(extras,Notification.CATEGORY_MISSED_CALL),"dialer")!!
        assertTrue(parsed.body.contains("Alex")); assertTrue(parsed.body.contains("+12025550123"))
        extras.putParcelable(Notification.EXTRA_CALL_PERSON,Person.Builder().setUri("content://never-read/contact/7").build())
        val unknown=NotificationParser.parse(notification(extras,Notification.CATEGORY_MISSED_CALL),"dialer")!!
        assertEquals("",unknown.body); assertTrue(unknown.hidden)
    }
    @Test fun mixedMediaDoesNotDiscardVisibleNumberOrCodeInFallback() {
        val extras=messages(false,null,message(null),message("验证码483921，请联系13812345678"))
        val parsed=NotificationParser.parse(notification(extras),"p")!!
        assertTrue(parsed.hidden); assertTrue(parsed.body.contains("验证码483921，请联系13812345678"))
        val source=Notice(1,"key","p","聊天",parsed.title,parsed.body,1,kind=parsed.kind,hidden=parsed.hidden)
        val selected=Policy.parse("""{"selected_ids":[],"message":""}""",listOf(source))
        assertTrue(selected.message.contains("验证码483921，请联系13812345678"))
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
    @Test fun englishSummaryWithLegacyMarkerStillDoesNotLoop() {
        val extras=Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE,"StillBrief")
            putCharSequence(Notification.EXTRA_TEXT,NotificationParser.MARKER+" StillBrief: 2 notifications to check.")
        }
        assertNull(NotificationParser.parse(notification(extras),"com.ss.android.lark"))
        val mixed=messages(true,"Work",message(NotificationParser.MARKER+" StillBrief: check your messages.","StillBrief"),message("Can you review this?","Alex"))
        val parsed=NotificationParser.parse(notification(mixed),"com.lark.overseas")!!
        assertTrue(parsed.body.contains("Alex：Can you review this?")); assertFalse(parsed.body.contains(NotificationParser.MARKER))
    }

    @Test fun genericHealthCountIsHiddenButConcreteAlertStillEligible() {
        val placeholders=Bundle().apply { putCharSequence(Notification.EXTRA_TITLE,"华为运动健康"); putCharSequence(Notification.EXTRA_TEXT,"3条隐藏通知（内容不可见）") }
        val generic=NotificationParser.parse(notification(placeholders),"com.huawei.health")!!
        assertTrue(generic.hidden)
        assertFalse(NotificationCandidates.eligible(Notice(1,"h","com.huawei.health","华为运动健康",generic.title,generic.body,1,kind=generic.kind,hidden=generic.hidden)))
        val alert=Bundle().apply { putCharSequence(Notification.EXTRA_TITLE,"心率过高") }
        val actual=NotificationParser.parse(notification(alert),"com.huawei.health")!!
        assertTrue(NotificationCandidates.eligible(Notice(2,"a","com.huawei.health","华为运动健康",actual.title,actual.body,1,kind=actual.kind,hidden=actual.hidden)))
    }
    @Test fun prefixFreeSummaryRoundTripDoesNotLoopAndKeepsCollapsedPreviewOut() {
        val summary="张三：请回复最新项目文档，并确认预算、交付日期以及负责人\n李四：会议改为明天下午"
        val wire=FeishuEchoes.plainText(summary)
        assertFalse(wire.contains(NotificationParser.MARKER)); assertEquals(summary,wire)
        val extras=Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE,"项目群")
            putCharSequence(Notification.EXTRA_BIG_TEXT,wire)
            putCharSequence(Notification.EXTRA_TEXT,wire.lineSequence().first())
        }
        assertNull(NotificationParser.parse(notification(extras),"com.ss.android.lark",sentSummaries=listOf(summary)))
    }
    @Test fun summarySplitAcrossTextLinesIsMatchedAsOneBlockAndHumanLineSurvives() {
        val summary="张三：请回复项目文档\n李四：请确认会议时间"
        val extras=Bundle().apply {
            putCharSequenceArray(Notification.EXTRA_TEXT_LINES,arrayOf("机器人：张三：请回复项目文档","李四：请确认会议时间","同事：预算也请补充"))
        }
        val parsed=NotificationParser.parse(notification(extras),"com.ss.android.lark",sentSummaries=listOf(summary))!!
        assertEquals("同事：预算也请补充",parsed.body)
        val ownOnly=Bundle().apply { putCharSequenceArray(Notification.EXTRA_TEXT_LINES,summary.lines().toTypedArray()) }
        assertNull(NotificationParser.parse(notification(ownOnly),"com.ss.android.lark",sentSummaries=listOf(summary)))
    }
    @Test fun humanFallbackSurvivesWhenMessagingBundleContainsOnlyRobotEcho() {
        val summary="张三：请回复项目文档\n李四：会议改到明天下午"
        val extras=messages(true,"项目群",message(summary,"机器人")).apply {
            putCharSequence(Notification.EXTRA_BIG_TEXT,"同事：请回复文档")
            putCharSequenceArray(Notification.EXTRA_TEXT_LINES,arrayOf("同事：请补充预算"))
            putCharSequence(Notification.EXTRA_TEXT,"同事：别忘了签字")
        }
        val parsed=NotificationParser.parse(notification(extras),"com.ss.android.lark",sentSummaries=listOf(summary))!!
        listOf("同事：请回复文档","同事：请补充预算","同事：别忘了签字").forEach { assertTrue(parsed.body.contains(it)) }
        assertFalse(parsed.body.contains("会议改到明天"))
    }
    @Test fun multiLineMessagingEchoDoesNotHarvestShortHumanReply() {
        val summary="好的\n张三还需要回复项目文档"
        val extras=messages(true,"项目群",message(summary,"机器人"),message("好的","同事"))
        val parsed=NotificationParser.parse(notification(extras),"com.lark.overseas",sentSummaries=listOf(summary))!!
        assertEquals("同事：好的",parsed.body)
    }
    @Test fun matchingTextInAnotherAppOrMixedSameLineIsNotDiscarded() {
        val summary="张三：请回复项目文档"
        val raw="机器人：$summary；同事：请补充预算"
        val extras=Bundle().apply { putCharSequence(Notification.EXTRA_TEXT,raw) }
        assertTrue(NotificationParser.parse(notification(extras),"com.ss.android.lark",sentSummaries=listOf(summary))!!.body.contains("同事：请补充预算"))
        val other=Bundle().apply { putCharSequence(Notification.EXTRA_TEXT,summary) }
        assertEquals(summary,NotificationParser.parse(notification(other),"another.app",sentSummaries=listOf(summary))!!.body)
    }

    @Test fun shortHumanFallbackIsNotMistakenForDerivedEchoPreview() {
        val summary="好的\n张三还需要回复文档"
        val extras=messages(true,"项目群",message(summary,"机器人")).apply { putCharSequence(Notification.EXTRA_BIG_TEXT,"Alex：好的") }
        val parsed=NotificationParser.parse(notification(extras),"com.ss.android.lark",sentSummaries=listOf(summary))!!
        assertEquals("Alex：好的",parsed.body)
    }

}
