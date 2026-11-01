package com.focusinbox.app

import android.app.Notification
import android.app.Person
import android.os.Build
import android.os.Bundle

/** Parses only the Notification object; never reads content URIs or app storage. */
object NotificationParser {
    const val MARKER = "【息间提醒】"
    private const val GROUP = "android.isGroupConversation"
    private val larkPackages = setOf("com.ss.android.lark", "com.lark.overseas")
    private val communicationPackages = larkPackages + setOf("com.tencent.mm", "com.tencent.mobileqq", "com.tencent.tim")
    data class Parsed(val title: String, val body: String, val kind: String, val hidden: Boolean, val summary: Boolean)

    fun parse(notification: Notification, pkg: String, defaultSmsPackage: String? = null): Parsed? {
        val extras = notification.extras ?: Bundle.EMPTY
        val isLark = pkg in larkPackages
        var markerSeen = false
        fun clean(text: String): String = if (!isLark) text else text.lineSequence().filter { line ->
            val marked = line.contains(MARKER)
            if (marked) markerSeen = true
            !marked
        }.joinToString("\n")
        val title = clean(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
        @Suppress("DEPRECATION")
        val bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.mapNotNull { it as? Bundle }.orEmpty()
        val messaging = bundles.isNotEmpty() || extras.getString(Notification.EXTRA_TEMPLATE) == "android.app.Notification\$MessagingStyle"
        // Explicit false describes a private conversation even if the app also provides a title.
        val group = if (extras.containsKey(GROUP)) extras.getBoolean(GROUP)
            else extras.getCharSequence("android.conversationTitle") != null
        var hiddenMedia = false
        val messageTexts = bundles.mapNotNull { message ->
            val raw = message.getCharSequence("text")?.toString().orEmpty()
            val text = clean(raw)
            if (isLark && raw.isNotBlank() && text.isBlank()) return@mapNotNull null
            val visible = if (text.isBlank()) {
                hiddenMedia = true
                "[图片/语音/媒体消息]"
            } else text
            @Suppress("DEPRECATION")
            val person = if (Build.VERSION.SDK_INT >= 28) message.getParcelable<Person>("sender_person")?.name?.toString() else null
            val sender = person ?: message.getCharSequence("sender")?.toString().orEmpty()
            if (sender.isBlank()) visible else "$sender：$visible"
        }
        val fallback = buildList {
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let { add(clean(it)) }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(clean(it.toString())) }
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let { add(clean(it)) }
        }.filter { it.isNotBlank() }
        val candidates = messageTexts + fallback
        // Keep all distinct visible lines; expanded text often repeats collapsed or MessagingStyle content.
        val parts = candidates.filterIndexed { i, text ->
            candidates.withIndex().none { (j, other) -> j != i && other.contains(text) && (other.length > text.length || j < i) }
        }
        val body = parts.joinToString("\n")
        if (isLark && markerSeen && body.isBlank()) return null
        val kind = when {
            notification.category == Notification.CATEGORY_MISSED_CALL -> "missed_call"
            notification.category == Notification.CATEGORY_CALL -> "call"
            messaging && group -> "group"
            messaging && extras.containsKey(GROUP) -> "private"
            messaging -> "message"
            notification.category == Notification.CATEGORY_EMAIL -> "mail"
            notification.category == Notification.CATEGORY_MESSAGE -> "message"
            pkg == defaultSmsPackage -> "sms"
            pkg in communicationPackages -> "message"
            else -> "unknown"
        }
        val hidden = hiddenMedia || body.isBlank() || body.trim() in setOf(
            "你收到了一条消息", "你收到一条新消息", "收到一条新消息", "新消息", "内容已隐藏", "通知内容已隐藏", "Message", "New message"
        ) || body.contains("隐藏了通知内容")
        return Parsed(title, body, kind, hidden, (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0)
    }
}
