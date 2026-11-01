package com.focusinbox.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    private const val PREFS="settings"
    private const val LANGUAGE="language"
    /** A system per-app change wins; an empty system choice retains the explicit app choice. */
    fun current(context: Context): UiLanguage {
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val stored=UiLanguage.fromTag(prefs.getString(LANGUAGE,"zh"))
        val locales=if(Build.VERSION.SDK_INT>=33) context.getSystemService(LocaleManager::class.java).applicationLocales else null
        val selected=if(locales!=null && !locales.isEmpty) UiLanguage.fromTag(locales[0].language) else stored
        if(selected!=stored) prefs.edit().putString(LANGUAGE,selected.tag).commit()
        return selected
    }
    fun wrap(base: Context): Context {
        val selected=current(base)
        val configuration=Configuration(base.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag(selected.tag)))
        return base.createConfigurationContext(configuration)
    }
    fun resourcesFor(context: Context,language: UiLanguage,configuration: Configuration=context.resources.configuration): android.content.res.Resources {
        val selected=Configuration(configuration)
        selected.setLocales(LocaleList(Locale.forLanguageTag(language.tag)))
        return context.createConfigurationContext(selected).resources
    }
    fun initialize(context: Context) {
        val language=current(context)
        if(Build.VERSION.SDK_INT>=33) {
            val manager=context.getSystemService(LocaleManager::class.java)
            if(manager.applicationLocales.isEmpty) manager.applicationLocales=LocaleList.forLanguageTags(language.tag)
        }
    }
    fun change(activity: Activity,language: UiLanguage) {
        Settings(activity).language=language
        if(Build.VERSION.SDK_INT>=33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales=LocaleList.forLanguageTags(language.tag)
        }
    }
    fun renderError(context: Context,persisted: String): String {
        if(!persisted.startsWith("@error:")) return if(current(context)==UiLanguage.ENGLISH)
            context.getString(R.string.error_legacy,persisted) else persisted
        val fields=persisted.removePrefix("@error:").split(":")
        val code=ErrorCode.entries.firstOrNull { it.name==fields[0] } ?: return context.getString(R.string.error_analysis_failed)
        val id=when(code) {
            ErrorCode.MODEL_BLANK -> R.string.error_model_blank
            ErrorCode.MODEL_FORMAT -> R.string.error_model_format
            ErrorCode.MODEL_ID -> R.string.error_model_id
            ErrorCode.MODEL_MESSAGE -> R.string.error_model_message
            ErrorCode.MODEL_LENGTH -> R.string.error_model_length
            ErrorCode.MODEL_REFUSAL -> R.string.error_model_refusal
            ErrorCode.MODEL_TRUNCATED -> R.string.error_model_truncated
            ErrorCode.API_KEY_REQUIRED -> R.string.error_api_key_required
            ErrorCode.MODEL_REQUIRED -> R.string.error_model_required
            ErrorCode.INVALID_URL -> R.string.error_invalid_url
            ErrorCode.HTTP -> R.string.error_http
            ErrorCode.NETWORK -> R.string.error_network
            ErrorCode.RESPONSE_SIZE -> R.string.error_response_size
            ErrorCode.WEBHOOK_INVALID -> R.string.error_webhook_invalid
            ErrorCode.FEISHU_REJECTED -> R.string.error_feishu_rejected
            ErrorCode.CLOUD_DISABLED -> R.string.error_cloud_disabled
            ErrorCode.ANALYSIS_FAILED -> R.string.error_analysis_failed
            ErrorCode.SEND_CONFIG -> R.string.error_send_config
            ErrorCode.SEND_UNCERTAIN -> R.string.error_send_uncertain
            ErrorCode.ANALYSIS_INTERRUPTED -> R.string.error_analysis_interrupted
            ErrorCode.LOCAL_NOTIFICATIONS_DISABLED -> R.string.error_local_notifications_disabled
            ErrorCode.LOCAL_SEND_FAILED -> R.string.error_local_send_failed
            ErrorCode.LOCAL_INTERRUPTED -> R.string.error_local_interrupted
        }
        return if(code==ErrorCode.HTTP) context.getString(id,fields.getOrNull(1)?.toIntOrNull() ?: 0) else context.getString(id)
    }
}
