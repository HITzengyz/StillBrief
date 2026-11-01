package com.focusinbox.app

import org.junit.Assert.*
import org.junit.Test

class FeishuEchoesTest {
    private val summary="张三：请回复项目文档\n李四：会议改到明天下午"
    @Test fun outgoingContentHasNoPrefixAndKeepsNewlines() {
        assertEquals(summary,FeishuEchoes.plainText(summary))
        assertFalse(FeishuEchoes.plainText(summary).contains("【息间提醒】"))
    }
    @Test fun completeBlocksAndSenderPrefixedBlocksAreRemoved() {
        assertEquals("",FeishuEchoes.remove(summary,listOf(summary)))
        assertEquals("",FeishuEchoes.remove("机器人：$summary",listOf(summary)))
        assertEquals("同事：请确认截止时间",FeishuEchoes.remove("机器人：$summary\n同事：请确认截止时间",listOf(summary)))
    }
    @Test fun sameLineContainingSummaryAndNewTextIsNeverDiscardedByContains() {
        val mixed="机器人：${summary.replace('\n',' ')} 同事：请补充预算"
        assertEquals(mixed,FeishuEchoes.remove(mixed,listOf(summary)))
        val exactSingle="张三：请回复项目文档"
        val sameLine="机器人：$exactSingle；同事：请补充预算"
        assertEquals(sameLine,FeishuEchoes.remove(sameLine,listOf(exactSingle)))
    }
    @Test fun individualShortLinesFromMultiLineSummaryAreNotEchoKeys() {
        val full="好的\n张三还需要回复文档"
        assertEquals("好的",FeishuEchoes.remove("好的",listOf(full)))
        assertEquals("Alex：好的",FeishuEchoes.remove("Alex：好的",listOf(full)))
    }
}
