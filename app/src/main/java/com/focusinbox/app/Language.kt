package com.focusinbox.app

/** Stable persisted choice, deliberately independent of Android's system language. */
enum class UiLanguage(val tag: String) {
    CHINESE("zh"), ENGLISH("en");
    companion object { fun fromTag(tag: String?): UiLanguage = if(tag?.startsWith("en")==true) ENGLISH else CHINESE }
}

/** Persist codes rather than server text, notification contents, or a translated error. */
enum class ErrorCode {
    MODEL_BLANK, MODEL_FORMAT, MODEL_ID, MODEL_MESSAGE, MODEL_LENGTH, MODEL_REFUSAL, MODEL_TRUNCATED,
    API_KEY_REQUIRED, MODEL_REQUIRED, INVALID_URL, HTTP, NETWORK, RESPONSE_SIZE,
    WEBHOOK_INVALID, FEISHU_REJECTED, CLOUD_DISABLED, ANALYSIS_FAILED, SEND_CONFIG, SEND_UNCERTAIN, ANALYSIS_INTERRUPTED;
    fun encoded(number: Int?=null) = "@error:$name" + (number?.let { ":$it" } ?: "")
}
class AppFailure(val code: ErrorCode,val number: Int?=null): IllegalArgumentException(code.encoded(number))
fun demand(condition: Boolean,code: ErrorCode) { if(!condition) throw AppFailure(code) }
