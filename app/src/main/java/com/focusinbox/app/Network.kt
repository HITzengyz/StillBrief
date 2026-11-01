package com.focusinbox.app

import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class RemoteFailure(message: String): Exception(message)
class SendUncertain: Exception("飞书送达情况不确定，手动重试可能重复")
object Network {
    fun validUrl(value: String, feishu: Boolean=false): Boolean = try {
        val u=URL(value)
        u.protocol=="https" && u.userInfo==null && u.host.isNotBlank() && (!feishu || (u.host in setOf("open.feishu.cn","open.larksuite.com") && u.path.startsWith("/open-apis/bot/v2/hook/")))
    } catch(_: java.net.MalformedURLException) { false }
    private fun post(url: String, payload: String, key: String?=null, sending: Boolean=false): String {
        require(validUrl(url)) { "仅允许有效 HTTPS 地址" }
        val c=URL(url).openConnection() as HttpsURLConnection
        try {
            c.requestMethod="POST"; c.connectTimeout=20_000; c.readTimeout=60_000; c.doOutput=true; c.instanceFollowRedirects=false
            c.setRequestProperty("Content-Type","application/json; charset=utf-8")
            key?.let { c.setRequestProperty("Authorization","Bearer $it") }
            c.outputStream.use { it.write(payload.toByteArray()) }
            val code=c.responseCode
            if(sending && code>=500) throw SendUncertain()
            if(code !in 200..299) throw RemoteFailure("服务 HTTP $code，请检查配置后手动重试")
            val out=StringBuilder()
            c.inputStream.bufferedReader().use { r ->
                val b=CharArray(4096)
                while(true) { val n=r.read(b); if(n<0) break; out.append(b,0,n); if(out.length>1_000_000) { if(sending) throw SendUncertain() else throw RemoteFailure("响应过大") } }
            }
            return out.toString()
        } catch(e: java.io.IOException) { if(sending) throw SendUncertain() else throw RemoteFailure("网络连接失败或超时，请手动重试") }
        finally { c.disconnect() }
    }
    fun analyze(s: Settings, notices: List<Notice>): Selection {
        require(s.apiKey.isNotBlank()) { "尚未配置 DeepSeek API Key" }
        val chunks=Policy.chunks(notices)
        val selections=chunks.map { analyzeChunk(s,it) }
        val ids=selections.flatMap { it.ids }.toSet()
        if(ids.isEmpty()) return Selection(emptySet(),"")
        if(chunks.size==1) return selections.first()
        val apps=notices.filter { it.id in ids }.map { it.app }.distinct()
        val involved=apps.take(8).joinToString("、")+(if(apps.size>8) "等${apps.size}个应用" else "")
        val previews=selections.filter { it.ids.isNotEmpty() }.take(3).joinToString("\n") { it.message }
        return Selection(ids,"本轮有${ids.size}条通知值得查看，涉及$involved。请趁休息查看手机。以下为部分分段摘要，完整通知见息间本批记录：\n$previews")
    }
    private fun analyzeChunk(s: Settings,notices: List<Notice>): Selection {
        val data=JSONObject().put("notifications",JSONArray().apply { notices.forEach { put(it.json()) } }).put("custom_rule",s.prompt).toString()
        val body=JSONObject().put("model",s.model).put("stream",false).put("thinking",JSONObject().put("type","disabled"))
            .put("response_format",JSONObject().put("type","json_object")).put("max_tokens",4096)
            .put("messages",JSONArray().put(JSONObject().put("role","system").put("content",Policy.SYSTEM)).put(JSONObject().put("role","user").put("content",data)))
        val reply=JSONObject(post(s.endpoint,body.toString(),s.apiKey))
        val choice=reply.getJSONArray("choices").getJSONObject(0)
        require(choice.getString("finish_reason")=="stop") { "模型输出截断或未正常结束" }
        return Policy.parse(choice.getJSONObject("message").getString("content"),notices)
    }
    fun send(s: Settings, message: String) {
        require(validUrl(s.webhook,true)) { "飞书 Webhook 地址无效" }
        // One line makes the marker cover the whole bot notification and prevents feedback loops.
        val body=JSONObject().put("msg_type","text").put("content",JSONObject().put("text",InboxListener.MARKER+message.replace(Regex("[\\r\\n]+")," · ")))
        if(s.signingKey.isNotBlank()) {
            val time=(System.currentTimeMillis()/1000).toString()
            val mac=Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec("$time\n${s.signingKey}".toByteArray(),"HmacSHA256")) }
            body.put("timestamp",time).put("sign",Base64.encodeToString(mac.doFinal(byteArrayOf()),Base64.NO_WRAP))
        }
        val result=post(s.webhook,body.toString(),sending=true)
        try { if(!Policy.feishuOk(result)) throw RemoteFailure("飞书业务返回失败，请检查签名、关键词和机器人设置") }
        catch(e: org.json.JSONException) { throw SendUncertain() }
    }
}
