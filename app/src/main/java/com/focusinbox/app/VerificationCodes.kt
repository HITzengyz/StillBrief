package com.focusinbox.app

import java.security.MessageDigest

/** Only explicit code phrases; ordinary phone numbers, order IDs and PIN discussions don't match. */
object VerificationCodes {
    const val MAX_AGE_MS=10*60*1000L
    private const val label="(?:验证码|校验码|动态码|动态密码|一次性密码|your code|verification code|security code|login code|authentication code|one[ -]time (?:password|code)|OTP)"
    private const val code="(?<![A-Za-z0-9])([A-Za-z0-9]{4,8})(?![A-Za-z0-9])"
    private val after=Regex("$label\\s*(?:为|是|is)?[\\s:：=【】\\[\\]（）()]*$code",RegexOption.IGNORE_CASE)
    private val before=Regex("$code\\s*(?:(?:is\\s+)?your\\s+(?:[A-Za-z]+\\s+){0,4}|as\\s+(?:[A-Za-z]+\\s+){0,4}|是您的|为您的|是|为)$label",RegexOption.IGNORE_CASE)
    fun extract(title: String,body: String): String? {
        val text="$title\n$body"
        val matches=(after.findAll(text)+before.findAll(text)).map { it.groupValues[1] }
            .filter { value -> value.any(Char::isDigit) }.distinct().toList()
        return matches.singleOrNull()
    }
    fun key(pkg: String,code: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$pkg\n$code".toByteArray()).joinToString("") { "%02x".format(it) }
    fun active(enabled: Boolean,target: DeliveryTarget)=enabled && target==DeliveryTarget.FEISHU
    fun fresh(created: Long,now: Long)=now-created in 0 until MAX_AGE_MS
    fun message(n: Notice,code: String,language: UiLanguage): String =
        (if(language==UiLanguage.ENGLISH) "Verification code: $code" else "验证码：$code")+
            "\n${n.app} · ${n.title}\n${n.body}"
}
