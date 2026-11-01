package com.focusinbox.app

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Transport formats are pure Kotlin so each provider can be tested without a network or Android. */
enum class Provider(val id: String,val title: String,val endpoint: String,val model: String) {
    DEEPSEEK("deepseek","DeepSeek","https://api.deepseek.com/chat/completions","deepseek-flash"),
    OPENAI("openai","OpenAI","https://api.openai.com/v1/chat/completions","gpt-6-luna"),
    ANTHROPIC("anthropic","Anthropic","https://api.anthropic.com/v1/messages","claude-haiku-5-5");
    companion object { fun fromId(id: String): Provider = entries.firstOrNull { it.id==id } ?: DEEPSEEK }
}
class AiConfig(val provider: Provider,val key: String,val endpoint: String,val model: String,val prompt: String)
class RunConfig(val enabled: Boolean,val ai: AiConfig,val webhook: String,val signingKey: String)
class AiRequest(val url: String,val headers: Map<String,String>,val body: String)
object AiAdapter {
    const val ANTHROPIC_VERSION="2026-10-21"
    fun request(config: AiConfig,notices: List<Notice>): AiRequest {
        require(config.key.isNotBlank()) { "尚未配置${config.provider.title} API Key" }
        require(config.model.isNotBlank()) { "尚未配置模型名称" }
        val data=JSONObject().put("notifications",JSONArray().apply { notices.forEach { put(it.json()) } }).put("custom_rule",config.prompt).toString()
        val body=JSONObject().put("model",config.model).put("stream",false)
        val headers=linkedMapOf("Content-Type" to "application/json; charset=utf-8","Authorization" to "Bearer ${config.key}")
        when(config.provider) {
            Provider.ANTHROPIC -> {
                headers["anthropic-version"]=ANTHROPIC_VERSION
                body.put("system",Policy.SYSTEM).put("max_tokens",8192)
                    .put("messages",JSONArray().put(JSONObject().put("role","user").put("content",JSONArray().put(JSONObject().put("type","text").put("text",data)))))
            }
            Provider.OPENAI,Provider.DEEPSEEK -> {
                body.put("messages",JSONArray().put(JSONObject().put("role","system").put("content",Policy.SYSTEM)).put(JSONObject().put("role","user").put("content",data)))
                    .put("response_format",JSONObject().put("type","json_object"))
                if(config.provider==Provider.DEEPSEEK) body.put("max_tokens",4096).put("thinking",JSONObject().put("type","disabled"))
                else {
                    body.put("max_completion_tokens",4096)
                    if(config.model==Provider.OPENAI.model) body.put("reasoning_effort","none")
                }
            }
        }
        return AiRequest(config.endpoint,headers,body.toString())
    }
    private fun objectValue(text: String): JSONObject {
        val tokener=JSONTokener(text)
        val value=tokener.nextValue()
        require(value is JSONObject && tokener.nextClean()=='\u0000') { "API响应不是完整JSON对象" }
        return value
    }
    fun parse(provider: Provider,response: String,notices: List<Notice>): Selection {
        val reply=objectValue(response)
        require(!reply.has("error")) { "AI服务返回错误" }
        val text=when(provider) {
            Provider.ANTHROPIC -> {
                require(reply.optString("stop_reason")=="end_turn") { "模型输出截断或未正常结束" }
                require(reply.optJSONObject("stop_details")?.optString("type")!="refusal") { "模型拒绝处理本批通知" }
                val content=reply.getJSONArray("content")
                val pieces=mutableListOf<String>()
                for(i in 0 until content.length()) {
                    val block=content.getJSONObject(i)
                    when(block.getString("type")) {
                        "text" -> { val value=block.get("text"); require(value is String) { "模型文本格式错误" }; pieces.add(value) }
                        "thinking","redacted_thinking" -> Unit
                        else -> throw IllegalArgumentException("模型返回了不支持或拒绝的内容")
                    }
                }
                pieces.joinToString("\n")
            }
            Provider.OPENAI,Provider.DEEPSEEK -> {
                val choices=reply.getJSONArray("choices")
                require(choices.length()==1) { "模型候选数量不符" }
                val choice=choices.getJSONObject(0)
                require(choice.optString("finish_reason")=="stop") { "模型输出截断或未正常结束" }
                val message=choice.getJSONObject("message")
                require(!message.has("refusal") || message.isNull("refusal") || message.opt("refusal")=="") { "模型拒绝处理本批通知" }
                val value=message.get("content")
                require(value is String) { "模型文本格式错误" }
                value
            }
        }
        val trimmed=text.trim()
        val json=if(trimmed.startsWith("```")) {
            val match=Regex("\\A```(?:json)?[ \\t]*\\r?\\n([\\s\\S]*?)\\r?\\n```\\z",RegexOption.IGNORE_CASE).matchEntire(trimmed)
            require(match!=null) { "模型返回了不完整或混杂的JSON代码块" }
            match.groupValues[1]
        } else trimmed
        return Policy.parse(json,notices)
    }
}
