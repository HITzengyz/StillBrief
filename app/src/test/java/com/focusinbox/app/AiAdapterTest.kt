package com.focusinbox.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiAdapterTest {
    private val notices=listOf(Notice(1,"k","app","应用","标题","请回复",1))
    private val selected="""{"selected_ids":[1],"message":"请查看消息"}"""
    private fun config(p: Provider,model: String=p.model)=AiConfig(p,"test-${p.id}-key",p.endpoint,model,"保留学校消息")
    private fun chat(content: Any=selected,finish: String="stop",refusal: Any=JSONObject.NULL)=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason",finish).put("message",JSONObject().put("role","assistant").put("content",content).put("refusal",refusal)))).toString()
    private fun anthropic(text: String=selected,stop: String="end_turn",extra: JSONObject?=null): String {
        val blocks=JSONArray()
        extra?.let { blocks.put(it) }
        blocks.put(JSONObject().put("type","text").put("text",text))
        return JSONObject().put("type","message").put("stop_reason",stop).put("content",blocks).toString()
    }
    private fun rejected(action: ()->Unit) { assertThrows(Exception::class.java) { action() } }
    @Test fun deepseekKeepsJsonAndDisabledThinking() {
        val request=AiAdapter.request(config(Provider.DEEPSEEK),notices)
        val body=JSONObject(request.body)
        assertEquals(Provider.DEEPSEEK.endpoint,request.url)
        assertEquals("Bearer test-deepseek-key",request.headers["Authorization"])
        assertFalse(request.headers.containsKey("anthropic-version"))
        assertEquals("disabled",body.getJSONObject("thinking").getString("type"))
        assertEquals("json_object",body.getJSONObject("response_format").getString("type"))
        assertEquals(4096,body.getInt("max_tokens")); assertFalse(body.has("max_completion_tokens"))
        assertEquals("system",body.getJSONArray("messages").getJSONObject(0).getString("role"))
    }
    @Test fun openaiUsesItsOwnTokenParameterAndNoDeepseekThinking() {
        val request=AiAdapter.request(config(Provider.OPENAI),notices)
        val body=JSONObject(request.body)
        assertEquals(Provider.OPENAI.endpoint,request.url)
        assertEquals("Bearer test-openai-key",request.headers["Authorization"])
        assertEquals(4096,body.getInt("max_completion_tokens"))
        assertEquals("none",body.getString("reasoning_effort"))
        assertEquals("json_object",body.getJSONObject("response_format").getString("type"))
        assertFalse(body.has("thinking")); assertFalse(body.has("max_tokens")); assertFalse(request.headers.containsKey("anthropic-version"))
        val custom=JSONObject(AiAdapter.request(config(Provider.OPENAI,"custom-model"),notices).body)
        assertFalse(custom.has("reasoning_effort"))
    }
    @Test fun anthropicUsesNativeMessagesAndCurrentVersion() {
        val request=AiAdapter.request(config(Provider.ANTHROPIC),notices)
        val body=JSONObject(request.body)
        assertEquals(Provider.ANTHROPIC.endpoint,request.url)
        assertEquals("Bearer test-anthropic-key",request.headers["Authorization"])
        assertEquals("2026-10-21",request.headers["anthropic-version"])
        assertFalse(request.headers.containsKey("x-api-key"))
        assertEquals(8192,body.getInt("max_tokens")); assertEquals(Policy.SYSTEM,body.getString("system"))
        assertFalse(body.has("response_format")); assertFalse(body.has("max_completion_tokens")); assertFalse(body.has("thinking"))
        assertFalse(body.has("temperature")); assertFalse(body.has("top_p")); assertFalse(body.has("top_k"))
        val messages=body.getJSONArray("messages"); assertEquals(1,messages.length())
        assertEquals("user",messages.getJSONObject(0).getString("role"))
        val text=messages.getJSONObject(0).getJSONArray("content").getJSONObject(0)
        assertEquals("text",text.getString("type"))
        assertEquals("保留学校消息",JSONObject(text.getString("text")).getString("custom_rule"))
    }
    @Test fun allRequestsKeepSecretsOutOfBodiesAndUseOnlyGivenConfig() {
        Provider.entries.forEach { p ->
            val c=AiConfig(p,"unique-secret","https://example.com/custom",p.model,"偏好")
            val r=AiAdapter.request(c,notices)
            assertEquals("https://example.com/custom",r.url); assertFalse(r.body.contains(c.key))
            assertEquals("application/json; charset=utf-8",r.headers["Content-Type"])
            assertEquals("Bearer unique-secret",r.headers["Authorization"])
            assertEquals(p.model,JSONObject(r.body).getString("model"))
        }
    }
    @Test fun allThreeResponsesUseTheSameStrictSelectionPolicy() {
        Provider.entries.forEach { p ->
            val response=if(p==Provider.ANTHROPIC) anthropic() else chat()
            val result=AiAdapter.parse(p,response,notices)
            assertEquals(setOf(1L),result.ids); assertEquals("请查看消息",result.message)
        }
    }
    @Test fun emptyResultsStayEmptyForEveryProvider() {
        val text="""{"selected_ids":[],"message":""}"""
        Provider.entries.forEach { p ->
            val result=AiAdapter.parse(p,if(p==Provider.ANTHROPIC) anthropic(text) else chat(text),notices)
            assertTrue(result.ids.isEmpty()); assertEquals("",result.message)
        }
    }
    @Test fun openaiAndDeepseekTruncationOrRefusalFails() {
        listOf(Provider.OPENAI,Provider.DEEPSEEK).forEach { p ->
            listOf("length","content_filter","tool_calls").forEach { stop -> rejected { AiAdapter.parse(p,chat(finish=stop),notices) } }
            rejected { AiAdapter.parse(p,chat(refusal="不能处理"),notices) }
            rejected { AiAdapter.parse(p,chat(content=JSONArray()),notices) }
        }
    }
    @Test fun anthropicThinkingIsIgnoredButTextIsParsed() {
        val response=JSONObject(anthropic(extra=JSONObject().put("type","thinking").put("thinking","not JSON")))
        response.getJSONArray("content").put(JSONObject().put("type","redacted_thinking").put("data","encrypted"))
        assertEquals(setOf(1L),AiAdapter.parse(Provider.ANTHROPIC,response.toString(),notices).ids)
    }
    @Test fun anthropicTruncationRefusalAndUnexpectedActionFail() {
        listOf("max_tokens","refusal","tool_use","stop_sequence").forEach { stop -> rejected { AiAdapter.parse(Provider.ANTHROPIC,anthropic(stop=stop),notices) } }
        val refusal=JSONObject(anthropic()).put("stop_details",JSONObject().put("type","refusal"))
        rejected { AiAdapter.parse(Provider.ANTHROPIC,refusal.toString(),notices) }
        rejected { AiAdapter.parse(Provider.ANTHROPIC,anthropic(extra=JSONObject().put("type","tool_use").put("name","do_something")),notices) }
        rejected { AiAdapter.parse(Provider.ANTHROPIC,"""{"stop_reason":"end_turn","content":[{"type":"thinking","thinking":"only thought"}]}""",notices) }
    }
    @Test fun oneCompleteJsonFenceIsAcceptedWithoutExtractingOtherText() {
        Provider.entries.forEach { p ->
            val fenced="```json\n$selected\n```"
            assertEquals(setOf(1L),AiAdapter.parse(p,if(p==Provider.ANTHROPIC) anthropic(fenced) else chat(fenced),notices).ids)
            listOf("解释\n$fenced","$fenced\n其他文字","```json\n$selected","$fenced\n$fenced").forEach { mixed ->
                rejected { AiAdapter.parse(p,if(p==Provider.ANTHROPIC) anthropic(mixed) else chat(mixed),notices) }
            }
        }
    }
    @Test fun invalidJsonIdsAndBlankContentFailForAllProviders() {
        Provider.entries.forEach { p ->
            listOf("   ","{", """{"selected_ids":[999],"message":"捏造"}""", "$selected{}").forEach { invalid ->
                rejected { AiAdapter.parse(p,if(p==Provider.ANTHROPIC) anthropic(invalid) else chat(invalid),notices) }
            }
        }
    }
    @Test fun errorObjectsAndMalformedEnvelopeFail() {
        Provider.entries.forEach { p ->
            listOf("{}", "[]", """{"error":{"message":"rejected"}}""").forEach { response -> rejected { AiAdapter.parse(p,response,notices) } }
        }
    }
    @Test fun blankKeyCannotProduceARequest() {
        Provider.entries.forEach { p -> rejected { AiAdapter.request(AiConfig(p,"",p.endpoint,p.model,""),notices) } }
    }
}
