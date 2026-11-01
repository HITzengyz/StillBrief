package com.focusinbox.app

import java.util.Locale

/** Filters analysis input, not collection. Original records remain available in history. */
object NotificationCandidates {
    val communicationKinds=setOf("private","group","message","mail","sms","call","missed_call")
    private val systemPackages=setOf("android","com.android.systemui","com.android.settings","com.huawei.systemmanager","com.hihonor.systemmanager")
    private fun compact(text: String)=text.lowercase(Locale.ROOT).replace(Regex("\\s+"),"").trim('：',':','。','.','!','！')
    fun placeholder(text: String,app: String=""): Boolean {
        var value=compact(text)
        val appName=compact(app)
        if(appName.isNotEmpty() && value.startsWith(appName)) value=value.removePrefix(appName).trimStart('：',':')
        value=value.replace(Regex("[（(](?:内容不可见|内容已隐藏|通知内容已隐藏|contenthidden|contentunavailable)[）)]"),"")
        if(value in setOf("","通知","消息","新消息","新通知","隐藏通知","内容已隐藏","内容不可见","通知内容已隐藏","你收到了一条消息","你收到一条新消息","收到一条新消息","message","newmessage","notification","newnotification","contenthidden","contentunavailable","hiddennotification")) return true
        return Regex("(?:有|收到|你有|您有)?(?:\\d+|[一二三四五六七八九十]+)条?(?:新的?|隐藏的?)?(?:通知|消息)").matches(value) ||
            Regex("\\d+(?:new|hidden)?(?:notifications?|messages?)").matches(value)
    }
    private fun sharingStatus(n: Notice): Boolean {
        if(n.pkg !in systemPackages || n.kind in communicationKinds) return false
        val title=compact(n.title)
        val genericTitle=placeholder(n.title,n.app) || Regex("(?:已连接)?\\d+(?:台|个)设备(?:已连接)?").matches(title) ||
            Regex("\\d+devices?(?:connected)?").matches(title) || title in setOf("wlan热点","wlan","wi-fi","wifi","移动热点","个人热点","热点","hotspot","mobilehotspot","wifihotspot")
        val body=compact(n.body).replace("wi-fi","wifi")
        val pureSharing=Regex("(?:正在|已|当前正在|您正在)?共享(?:wlan|wifi|无线)(?:网络)?").matches(body) ||
            body in setOf("sharingwifi","sharingwifinetwork","wifisharingactive","wifihotspotactive","mobilehotspotactive")
        return genericTitle && pureSharing
    }
    fun eligible(n: Notice): Boolean {
        if(sharingStatus(n)) return false
        if(n.kind in communicationKinds) return true
        return !placeholder(n.title,n.app) || !placeholder(n.body,n.app)
    }
    fun prepare(notices: List<Notice>): List<Notice> = Policy.merge(notices.filter { eligible(it) })
    fun analyze(notices: List<Notice>,language: UiLanguage,request: (List<Notice>)->Selection): Selection {
        val candidates=prepare(notices)
        if(candidates.isEmpty()) return Selection(emptySet(),"")
        return Policy.combine(language,candidates,Policy.chunks(candidates).map(request))
    }
}
