package com.focusinbox.app

import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class RemoteFailure(val code: ErrorCode,val number: Int?=null): Exception(code.encoded(number))
class SendUncertain: Exception(ErrorCode.SEND_UNCERTAIN.encoded())
object Network {
    fun validUrl(value: String, feishu: Boolean=false): Boolean = try {
        val u=URL(value)
        u.protocol=="https" && u.userInfo==null && u.host.isNotBlank() && (!feishu || (u.host in setOf("open.feishu.cn","open.larksuite.com") && u.path.startsWith("/open-apis/bot/v2/hook/")))
    } catch(_: java.net.MalformedURLException) { false }
    private fun post(url: String, payload: String, headers: Map<String,String> = emptyMap(), sending: Boolean=false): String {
        demand(validUrl(url),ErrorCode.INVALID_URL)
        val c=URL(url).openConnection() as HttpsURLConnection
        try {
            c.requestMethod="POST"; c.connectTimeout=20_000; c.readTimeout=60_000; c.doOutput=true; c.instanceFollowRedirects=false
            c.setRequestProperty("Content-Type","application/json; charset=utf-8")
            headers.forEach { (name,value) -> c.setRequestProperty(name,value) }
            c.outputStream.use { it.write(payload.toByteArray()) }
            val code=c.responseCode
            if(sending && code>=500) throw SendUncertain()
            if(code !in 200..299) throw RemoteFailure(ErrorCode.HTTP,code)
            val out=StringBuilder()
            c.inputStream.bufferedReader().use { r ->
                val b=CharArray(4096)
                while(true) { val n=r.read(b); if(n<0) break; out.append(b,0,n); if(out.length>1_000_000) { if(sending) throw SendUncertain() else throw RemoteFailure(ErrorCode.RESPONSE_SIZE) } }
            }
            return out.toString()
        } catch(e: java.io.IOException) { if(sending) throw SendUncertain() else throw RemoteFailure(ErrorCode.NETWORK) }
        finally { c.disconnect() }
    }
    fun analyze(config: AiConfig, notices: List<Notice>): Selection {
        val chunks=Policy.chunks(notices)
        val selections=chunks.map { analyzeChunk(config,it) }
        return Policy.combine(config.language,notices,selections)
    }
    private fun analyzeChunk(config: AiConfig,notices: List<Notice>): Selection {
        val request=AiAdapter.request(config,notices)
        return AiAdapter.parse(config.provider,post(request.url,request.body,request.headers),notices,config.language)
    }
    fun send(s: RunConfig, message: String) {
        demand(validUrl(s.webhook,true),ErrorCode.WEBHOOK_INVALID)
        // One line makes the marker cover the whole bot notification and prevents feedback loops.
        val body=JSONObject().put("msg_type","text").put("content",JSONObject().put("text",InboxListener.MARKER+message.replace(Regex("[\\r\\n]+")," · ")))
        if(s.signingKey.isNotBlank()) {
            val time=(System.currentTimeMillis()/1000).toString()
            val mac=Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec("$time\n${s.signingKey}".toByteArray(),"HmacSHA256")) }
            body.put("timestamp",time).put("sign",Base64.encodeToString(mac.doFinal(byteArrayOf()),Base64.NO_WRAP))
        }
        val result=post(s.webhook,body.toString(),sending=true)
        try { if(!Policy.feishuOk(result)) throw RemoteFailure(ErrorCode.FEISHU_REJECTED) }
        catch(e: org.json.JSONException) { throw SendUncertain() }
    }
}
