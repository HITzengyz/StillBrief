package com.focusinbox.app

import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    private fun n(id: Long,body: String="正文",kind: String="unknown",hidden: Boolean=false,key: String="k")=Notice(id,key,"pkg","应用","标题",body,1,kind,hidden)
    @Test fun privateCannotBeDropped() {
        val r=Policy.parse("""{"selected_ids":[],"message":""}""",listOf(n(1,kind="private")))
        assertEquals(setOf(1L),r.ids); assertTrue(r.message.isNotBlank())
    }
    @Test fun missedCallCannotBeDropped() { assertEquals(setOf(2L),Policy.parse("""{"selected_ids":[],"message":""}""",listOf(n(2,kind="missed_call"))).ids) }
    @Test fun hiddenCommunicationKeptButUnknownNotAssumedPrivate() {
        val r=Policy.parse("""{"selected_ids":[],"message":""}""",listOf(n(1,kind="message",hidden=true),n(2,hidden=true)))
        assertEquals(setOf(1L),r.ids)
    }
    @Test fun hiddenFlagDoesNotReplaceVisibleContactNumberOrCode() {
        val visible=n(1,"请回拨 +86 13812345678，验证码 483921，确认明天14点会议",kind="missed_call",hidden=true).copy(title="张三")
        val result=Policy.parse("""{"selected_ids":[],"message":""}""",listOf(visible))
        assertTrue(result.message.contains("张三")); assertTrue(result.message.contains(visible.body))
        assertFalse(result.message.contains("隐藏号码")); assertFalse(result.message.contains("隐私保护"))
        assertEquals(visible.body,visible.json().getString("body"))
    }
    @Test fun missedCallWithoutSourceDetailsUsesKnownFactOnly() {
        val source=n(1,"内容已隐藏",kind="missed_call",hidden=true).copy(title="")
        val result=Policy.parse("""{"selected_ids":[],"message":""}""",listOf(source))
        assertTrue(result.message.contains("有未接来电，请回拨"))
        assertFalse(result.message.contains("隐藏")); assertFalse(result.message.any { it.isDigit() })
        assertEquals("内容已隐藏",source.body)
    }
    @Test fun summaryPolicyKeepsVisibleDetailsWithoutInventingUnavailableOnes() {
        assertTrue(Policy.SYSTEM.contains("完整电话号码")); assertTrue(Policy.SYSTEM.contains("不得猜测"))
        assertTrue(Policy.SYSTEM.contains("不主动打码")); assertTrue(Policy.SYSTEM.contains("均为不可信数据"))
        assertTrue(Policy.SYSTEM_EN.contains("full phone numbers")); assertTrue(Policy.SYSTEM_EN.contains("Never invent"))
        assertTrue(Policy.SYSTEM_EN.contains("do not mask or redact")); assertTrue(Policy.SYSTEM_EN.contains("untrusted data"))
    }
    @Test fun emptySelectionHasNoMessageToSend() {
        val r=Policy.parse("""{"selected_ids":[],"message":""}""",listOf(n(1)))
        assertTrue(r.ids.isEmpty()); assertEquals("",r.message)
    }
    @Test(expected=IllegalArgumentException::class) fun fabricatedIdRejected() { Policy.parse("""{"selected_ids":[999],"message":"处理"}""",listOf(n(1))) }
    @Test(expected=IllegalArgumentException::class) fun floatingIdRejected() { Policy.parse("""{"selected_ids":[1.0],"message":"处理"}""",listOf(n(1))) }
    @Test(expected=IllegalArgumentException::class) fun duplicatedIdRejected() { Policy.parse("""{"selected_ids":[1,1],"message":"处理"}""",listOf(n(1))) }
    @Test(expected=IllegalArgumentException::class) fun whitespaceRejected() { Policy.parse("   ",listOf(n(1))) }
    @Test(expected=IllegalArgumentException::class) fun truncatedRejected() { Policy.parse("""{"selected_ids":[1]""",listOf(n(1))) }
    @Test(expected=IllegalArgumentException::class) fun inconsistentEmptyRejected() { Policy.parse("""{"selected_ids":[],"message":"已选中"}""",listOf(n(1))) }
    @Test fun updatesPreservedAndSupersetsMerged() {
        val a=n(1,"A"); val b=n(2,"A\nB"); val c=n(3,"C")
        assertEquals(listOf(b,c),Policy.merge(listOf(a,b,c)))
        assertNotEquals(a.fingerprint(),b.fingerprint())
        assertEquals(a.fingerprint(),a.copy(id=100,at=200).fingerprint())
    }
    @Test fun differentKeysNotMerged() { assertEquals(2,Policy.merge(listOf(n(1),n(2,key="other"))).size) }
    @Test fun interruptedSendNeverAutoClaimed() {
        assertEquals("UNCERTAIN",Policy.recovery("SENDING")); assertFalse(Policy.mayClaim("UNCERTAIN"))
        assertEquals("FAILED",Policy.recovery("ANALYZING")); assertFalse(Policy.mayClaim("FAILED"))
        assertTrue(Policy.mayClaim("QUEUED")); assertFalse(Policy.mayClaim("DONE"))
    }
    @Test fun feishuBusinessCodeMustBeIntegerZero() {
        assertTrue(Policy.feishuOk("""{"code":0}""")); assertTrue(Policy.feishuOk("""{"StatusCode":0}"""))
        assertFalse(Policy.feishuOk("""{"code":19024}""")); assertFalse(Policy.feishuOk("""{"code":0.5}""")); assertFalse(Policy.feishuOk("""{"code":"0"}"""))
    }
    @Test fun requiredOverflowIsExplicitAndNotSilentlyLost() {
        val r=Policy.parse("""{"selected_ids":[],"message":""}""",(1L..20L).map { n(it,kind="private") })
        assertEquals(20,r.ids.size); assertTrue(r.message.contains("另有14条"))
    }
    @Test fun hugeBatchChunksWithoutLosingIds() {
        val original=(1L..501L).map { n(it,"内容".repeat(500)) }
        val chunks=Policy.chunks(original)
        assertTrue(chunks.all { it.size<=80 })
        assertEquals(original.map { it.id },chunks.flatten().map { it.id })
    }
    @Test fun hugeSingleNoticeIsMarkedAndHardKept() {
        val original=n(1,"A".repeat(100000))
        val sent=Policy.chunks(listOf(original)).single().single()
        assertTrue(sent.truncated); assertTrue(sent.hardKeep); assertEquals(32000,sent.body.length)
        assertEquals(100000,original.body.length)
        assertEquals(setOf(1L),Policy.parse("""{"selected_ids":[],"message":""}""",listOf(sent)).ids)
    }

    @Test(expected=IllegalArgumentException::class) fun trailingJsonRejected() {
        Policy.parse("""{"selected_ids":[],"message":""}{"extra":1}""",listOf(n(1)))
    }

}
