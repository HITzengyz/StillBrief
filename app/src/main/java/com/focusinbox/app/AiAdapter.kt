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
class AiConfig(val provider: Provider,val key: String,val endpoint: String,val model: String,val prompt: String,val language: UiLanguage=UiLanguage.CHINESE)
class RunConfig(val enabled: Boolean,val ai: AiConfig,val webhook: String,val signingKey: String,val target: DeliveryTarget=DeliveryTarget.FEISHU)
class AiRequest(val url: String,val headers: Map<String,String>,val body: String)
object AiAdapter {
    const val ANTHROPIC_VERSION="2026-10-21"
    fun request(config: AiConfig,notices: List<Notice>): AiRequest {
        demand(config.key.isNotBlank(),ErrorCode.API_KEY_REQUIRED)
        demand(config.model.isNotBlank(),ErrorCode.MODEL_REQUIRED)
        val data=JSONObject().put("notifications",JSONArray().apply { notices.forEach { put(it.json()) } }).put("custom_rule",config.prompt).toString()
        val body=JSONObject().put("model",config.model).put("stream",false)
        val headers=linkedMapOf("Content-Type" to "application/json; charset=utf-8","Authorization" to "Bearer ${config.key}")
        when(config.provider) {
            Provider.ANTHROPIC -> {
                headers["anthropic-version"]=ANTHROPIC_VERSION
                body.put("system",Policy.system(config.language)).put("max_tokens",8192)
                    .put("messages",JSONArray().put(JSONObject().put("role","user").put("content",JSONArray().put(JSONObject().put("type","text").put("text",data)))))
            }
            Provider.OPENAI,Provider.DEEPSEEK -> {
                body.put("messages",JSONArray().put(JSONObject().put("role","system").put("content",Policy.system(config.language))).put(JSONObject().put("role","user").put("content",data)))
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
        if(value !is JSONObject || tokener.nextClean()!='\u0000') throw AppFailure(ErrorCode.MODEL_FORMAT)
        return value
    }
    fun parse(provider: Provider,response: String,notices: List<Notice>,language: UiLanguage=UiLanguage.CHINESE): Selection {
        val reply=objectValue(response)
        demand(!reply.has("error"),ErrorCode.MODEL_FORMAT)
        val text=when(provider) {
            Provider.ANTHROPIC -> {
                demand(reply.optString("stop_reason")=="end_turn",ErrorCode.MODEL_TRUNCATED)
                demand(reply.optJSONObject("stop_details")?.optString("type")!="refusal",ErrorCode.MODEL_REFUSAL)
                val content=reply.getJSONArray("content")
                val pieces=mutableListOf<String>()
                for(i in 0 until content.length()) {
                    val block=content.getJSONObject(i)
                    when(block.getString("type")) {
                        "text" -> { val value=block.get("text"); if(value !is String) throw AppFailure(ErrorCode.MODEL_FORMAT); pieces.add(value) }
                        "thinking","redacted_thinking" -> Unit
                        else -> throw AppFailure(ErrorCode.MODEL_REFUSAL)
                    }
                }
                pieces.joinToString("\n")
            }
            Provider.OPENAI,Provider.DEEPSEEK -> {
                val choices=reply.getJSONArray("choices")
                demand(choices.length()==1,ErrorCode.MODEL_FORMAT)
                val choice=choices.getJSONObject(0)
                demand(choice.optString("finish_reason")=="stop",ErrorCode.MODEL_TRUNCATED)
                val message=choice.getJSONObject("message")
                demand(!message.has("refusal") || message.isNull("refusal") || message.opt("refusal")=="",ErrorCode.MODEL_REFUSAL)
                val value=message.get("content")
                if(value !is String) throw AppFailure(ErrorCode.MODEL_FORMAT)
                value
            }
        }
        val trimmed=text.trim()
        val json=if(trimmed.startsWith("```")) {
            val match=Regex("\\A```(?:json)?[ \\t]*\\r?\\n([\\s\\S]*?)\\r?\\n```\\z",RegexOption.IGNORE_CASE).matchEntire(trimmed)
            if(match==null) throw AppFailure(ErrorCode.MODEL_FORMAT)
            match.groupValues[1]
        } else trimmed
        return Policy.parse(json,notices,language)
    }
}
