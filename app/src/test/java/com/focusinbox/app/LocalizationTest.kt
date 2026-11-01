package com.focusinbox.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationTest {
    private val notice=Notice(1,"key","app","聊天应用","张三","在吗，明天见",1,kind="private")
    private val empty="""{"selected_ids":[],"message":""}"""
    private fun strings(folder: String): Map<String,String> {
        val file=listOf(File("src/main/res/$folder/strings.xml"),File("app/src/main/res/$folder/strings.xml")).first { it.isFile }
        val nodes=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        val result=(0 until nodes.length).associate { i ->
            val element=nodes.item(i) as org.w3c.dom.Element
            element.getAttribute("name") to element.textContent
        }
        assertEquals("Duplicate resource key",nodes.length,result.size)
        return result
    }
    @Test fun resourceKeysAndNumberedFormatArgumentsMatch() {
        val zh=strings("values"); val en=strings("values-en")
        assertEquals(zh.keys,en.keys)
        val placeholders=Regex("%([0-9]+)\\$[dsf]")
        zh.forEach { (key,value) ->
            assertEquals(key,placeholders.findAll(value).map { it.value }.toList().sorted(),placeholders.findAll(en.getValue(key)).map { it.value }.toList().sorted())
            assertTrue("Empty English resource: $key",en.getValue(key).isNotBlank())
        }
        assertEquals("息间",zh["app_name"]); assertEquals("StillBrief",en["app_name"])
    }
    @Test fun everyStableErrorHasChineseAndEnglishResources() {
        val zh=strings("values"); val en=strings("values-en")
        ErrorCode.entries.forEach { code ->
            val key="error_"+code.name.lowercase(java.util.Locale.ROOT)
            assertTrue(key,zh.containsKey(key)); assertTrue(key,en.containsKey(key))
            assertFalse(en.getValue(key).any { it in '\u4e00'..'\u9fff' })
        }
    }
    @Test fun englishPromptForEveryProviderKeepsOriginalDataAndPreferences() {
        Provider.entries.forEach { provider ->
            val config=AiConfig(provider,"test-key",provider.endpoint,provider.model,"保留学校消息",UiLanguage.ENGLISH)
            val body=JSONObject(AiAdapter.request(config,listOf(notice)).body)
            val system=if(provider==Provider.ANTHROPIC) body.getString("system") else body.getJSONArray("messages").getJSONObject(0).getString("content")
            assertEquals(Policy.SYSTEM_EN,system); assertTrue(system.contains("English")); assertTrue(system.contains("Always keep"))
            val user=if(provider==Provider.ANTHROPIC) body.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text")
                else body.getJSONArray("messages").getJSONObject(1).getString("content")
            val data=JSONObject(user)
            assertEquals("保留学校消息",data.getString("custom_rule"))
            assertEquals("在吗，明天见",data.getJSONArray("notifications").getJSONObject(0).getString("body"))
            assertEquals("张三",data.getJSONArray("notifications").getJSONObject(0).getString("title"))
        }
    }
    @Test fun englishPrivateFallbackIsNaturalAndKeepsOriginalNames() {
        val result=Policy.parse(empty,listOf(notice),UiLanguage.ENGLISH)
        assertEquals(setOf(1L),result.ids)
        assertTrue(result.message.contains("张三")); assertTrue(result.message.contains("在吗，明天见"))
        assertTrue(result.message.contains("张三")); assertTrue(result.message.contains("在吗，明天见"))
        assertFalse(result.message.contains("protected"))
    }
    @Test fun englishHiddenAndOverflowFallbackKeepsAllRequiredIds() {
        val notices=(1L..20L).map { notice.copy(id=it,hidden=true) }
        val result=Policy.parse(empty,notices,UiLanguage.ENGLISH)
        assertEquals(20,result.ids.size); assertFalse(result.message.contains("protected"))
        assertTrue(result.message.contains(notice.body)); assertTrue(result.message.contains("14 more to check"))
        assertFalse(result.message.contains("hidden number")); assertFalse(result.message.contains("privacy protection"))
    }
    @Test fun englishMissedCallRetainsVisibleNumberAndDoesNotInventMissingDetails() {
        val source=notice.copy(title="Alex",body="Call +1 202 555 0123, code 483921",kind="missed_call",hidden=true)
        val visible=Policy.parse(empty,listOf(source),UiLanguage.ENGLISH)
        assertTrue(visible.message.contains("Alex")); assertTrue(visible.message.contains(source.body))
        val unknown=Policy.parse(empty,listOf(source.copy(title="",body="")),UiLanguage.ENGLISH)
        assertTrue(unknown.message.contains("Missed call; please call back."))
        assertFalse(unknown.message.contains("Alex")); assertFalse(unknown.message.any { it.isDigit() })
        assertFalse(unknown.message.contains("hidden")); assertFalse(unknown.message.contains("protected"))
    }
    @Test fun englishEmptySelectionDoesNotInventSummary() {
        val result=Policy.parse(empty,listOf(notice.copy(kind="unknown")),UiLanguage.ENGLISH)
        assertTrue(result.ids.isEmpty()); assertEquals("",result.message)
    }
    @Test fun englishErrorsUseStableCodesForLocalizedRendering() {
        val blank=assertThrows(AppFailure::class.java) { Policy.parse(" ",listOf(notice),UiLanguage.ENGLISH) }
        assertEquals(ErrorCode.MODEL_BLANK,blank.code); assertEquals("@error:MODEL_BLANK",blank.message)
        val badId=assertThrows(AppFailure::class.java) { Policy.parse("""{"selected_ids":[999],"message":"Wrong"}""",listOf(notice),UiLanguage.ENGLISH) }
        assertEquals(ErrorCode.MODEL_ID,badId.code)
        assertEquals("@error:HTTP:401",ErrorCode.HTTP.encoded(401))
    }
    @Test fun englishChunkAggregationIsEnglishAndRetainsOriginalAppNames() {
        val notices=listOf(notice,notice.copy(id=2,pkg="mail",app="邮件应用"))
        val result=Policy.combine(UiLanguage.ENGLISH,notices,listOf(Selection(setOf(1L),"Check a direct message."),Selection(setOf(2L),"Reply to an email.")))
        assertEquals(setOf(1L,2L),result.ids); assertTrue(result.message.contains("Check a direct message.")); assertTrue(result.message.contains("Reply to an email."))
        assertFalse(result.message.contains("worth checking from"))
        assertEquals("",Policy.combine(UiLanguage.ENGLISH,notices,listOf(Selection(emptySet(),""))).message)
    }
    @Test fun chineseRemainsTheExplicitDefault() {
        assertEquals(UiLanguage.CHINESE,UiLanguage.fromTag(null)); assertEquals(UiLanguage.CHINESE,UiLanguage.fromTag("zh-Hans"))
        assertEquals(UiLanguage.ENGLISH,UiLanguage.fromTag("en-US")); assertEquals(Policy.SYSTEM,Policy.system(UiLanguage.CHINESE))
    }
}
