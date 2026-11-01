package com.focusinbox.app

import org.junit.Assert.*
import org.junit.Test

class NotificationCandidatesTest {
    private fun health(body: String="3条隐藏通知（内容不可见）",title: String="华为运动健康",hidden: Boolean=true)=
        Notice(1,"health","com.huawei.health","华为运动健康",title,body,1,hidden=hidden)
    private val wifi=Notice(2,"wifi","android","Android系统","2台设备","正在共享 WLAN 网络",1,ongoing=true)
    @Test fun genericHealthPlaceholdersAreNotEvidenceEvenWithoutHiddenFlag() {
        listOf("3条隐藏通知（内容不可见）","有 3 条隐藏通知 (内容不可见)","3条新通知","3 hidden notifications (content unavailable)","").forEach { body ->
            assertFalse(body,NotificationCandidates.eligible(health(body,hidden=false)))
        }
    }
    @Test fun specificVisibleHealthAlertsRemainCandidates() {
        assertTrue(NotificationCandidates.eligible(health("",title="心率过高")))
        assertTrue(NotificationCandidates.eligible(health("心率过高，请及时查看",hidden=false)))
        assertTrue(NotificationCandidates.eligible(health("无法读取健康数据，请检查传感器",hidden=false)))
    }
    @Test fun onlyExplicitSystemSharingStatusIsRemoved() {
        assertFalse(NotificationCandidates.eligible(wifi))
        assertTrue(NotificationCandidates.eligible(wifi.copy(title="发现未经授权的设备")))
        assertTrue(NotificationCandidates.eligible(wifi.copy(pkg="other.app")))
    }
    @Test fun realCommunicationProtectionRemains() {
        listOf("private","missed_call","message","sms","mail").forEach { kind ->
            val n=health().copy(kind=kind)
            assertTrue(NotificationCandidates.eligible(n)); assertTrue(n.hardKeep)
        }
    }
    @Test fun truncationCannotPromoteHiddenNoiseOrOngoingStatus() {
        assertFalse(health().copy(truncated=true).hardKeep)
        assertFalse(wifi.copy(truncated=true).hardKeep)
        assertFalse(health("Visible system progress",title="Background status",hidden=false).copy(ongoing=true,truncated=true).hardKeep)
        assertTrue(health("真实完整消息",title="同事",hidden=false).copy(kind="private",truncated=true).hardKeep)
    }
    @Test fun mixedAnalysisOnlyReceivesUsefulCandidates() {
        val chat=Notice(3,"chat","com.tencent.mm","微信","张三","请回复文档",1,kind="private")
        var requests=0
        val result=NotificationCandidates.analyze(listOf(health(),wifi,chat),UiLanguage.CHINESE) { candidates ->
            requests++; assertEquals(listOf(3L),candidates.map { it.id }); Selection(setOf(3L),"张三请回复文档。")
        }
        assertEquals(1,requests); assertEquals(setOf(3L),result.ids)
        assertFalse(result.message.contains("华为运动健康")); assertFalse(result.message.contains("WLAN")); assertFalse(result.message.contains("忽略"))
    }
    @Test fun noiseOnlyHasZeroAiRequestsAndZeroDeliveries() {
        var requests=0
        val result=NotificationCandidates.analyze(listOf(health(),wifi),UiLanguage.CHINESE) { requests++; error("AI must not be called") }
        assertEquals(0,requests); assertTrue(result.ids.isEmpty()); assertEquals("",result.message)
        DeliveryTarget.entries.forEach { target ->
            var sent=0
            val config=RunConfig(false,AiConfig(Provider.DEEPSEEK,"","", "",""),"","",target)
            assertFalse(DeliveryDispatcher.send(Batch(1,"READY",1,result.message,"","",target),config,{ _,_,_ -> sent++ },{ _,_ -> sent++ }))
            assertEquals(0,sent)
        }
    }
    @Test fun fallbackAndCombinedSummariesContainOnlyItems() {
        val call=health("",title="未接来电").copy(kind="missed_call")
        val fallback=Policy.parse("""{"selected_ids":[],"message":""}""",listOf(call))
        assertTrue(fallback.message.contains("未接来电")); assertFalse(fallback.message.contains("有新消息"))
        assertFalse(fallback.message.contains("保守")); assertFalse(fallback.message.contains("必须保留"))
        val combined=Policy.combine(UiLanguage.CHINESE,listOf(call),listOf(fallback,Selection(emptySet(),"")))
        assertEquals(fallback.message,combined.message)
    }
}
