package com.focusinbox.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsTest {
    @Test fun savingBasicKeepsAdvancedAndDefaultDuration() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs="settings-instrumentation-test"
        context.getSharedPreferences(prefs,0).edit().clear().commit()
        try {
            val settings=Settings(context,prefs)
            settings.saveAdvanced("https://example.com/chat/completions","my-model","test-signature","保留学校消息")
            settings.minutes=45; settings.hideRecents=true
            settings.saveBasic("test-api","https://open.feishu.cn/open-apis/bot/v2/hook/test",false)
            val reopened=Settings(context,prefs)
            assertEquals("my-model",reopened.model); assertEquals("https://example.com/chat/completions",reopened.endpoint)
            assertEquals("test-signature",reopened.signingKey); assertEquals("保留学校消息",reopened.prompt)
            assertEquals(45,reopened.minutes); assertTrue(reopened.hideRecents)
            assertEquals("test-api",reopened.apiKey); assertFalse(reopened.enabled)
        } finally { context.getSharedPreferences(prefs,0).edit().clear().commit() }
    }
    private fun isolated(name: String,action: (Settings,android.content.SharedPreferences)->Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences(name,0)
        prefs.edit().clear().commit()
        try { action(Settings(context,name),prefs) } finally { prefs.edit().clear().commit() }
    }
    @Test fun legacyDeepseekConfigMigratesOnceWithoutChangingGlobalSettings() = isolated("settings-migration-test") { original,prefs ->
        original.saveBasic("legacy-api-key","https://open.feishu.cn/open-apis/bot/v2/hook/legacy",false)
        original.saveAdvanced("https://legacy.example/chat/completions","legacy-model","legacy-sign","学校消息")
        original.minutes=35; original.hideRecents=true
        val encrypted=prefs.getString("provider.deepseek.key",null)!!
        // Recreate the real v1.1 preference layout, retaining the original encrypted value.
        prefs.edit().putString("key",encrypted).putString("endpoint","https://legacy.example/chat/completions").putString("model","legacy-model")
            .remove("provider.deepseek.key").remove("provider.deepseek.endpoint").remove("provider.deepseek.model")
            .remove("provider").remove("providers_migrated").commit()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val upgraded=Settings(context,"settings-migration-test")
        assertEquals(Provider.DEEPSEEK,upgraded.provider)
        val ds=upgraded.config(Provider.DEEPSEEK)
        assertEquals("legacy-api-key",ds.key); assertEquals("legacy-model",ds.model); assertEquals("https://legacy.example/chat/completions",ds.endpoint)
        assertEquals(encrypted,prefs.getString("provider.deepseek.key",null)); assertFalse(prefs.contains("key"))
        assertEquals("legacy-sign",upgraded.signingKey); assertEquals("学校消息",upgraded.prompt)
        assertEquals("https://open.feishu.cn/open-apis/bot/v2/hook/legacy",upgraded.webhook)
        assertEquals(35,upgraded.minutes); assertTrue(upgraded.hideRecents); assertFalse(upgraded.enabled)
        assertTrue(upgraded.config(Provider.OPENAI).key.isEmpty()); assertTrue(upgraded.config(Provider.ANTHROPIC).key.isEmpty())
        upgraded.saveBasic(Provider.OPENAI,"openai-key",upgraded.webhook,false)
        assertEquals(Provider.OPENAI,Settings(context,"settings-migration-test").provider)
    }
    @Test fun eachProviderOwnsItsKeyModelAndEndpointAndAdvancedDoesNotSwitchActive() = isolated("settings-isolation-test") { settings,_ ->
        settings.saveBasic(Provider.DEEPSEEK,"ds-key","https://open.feishu.cn/open-apis/bot/v2/hook/test",true)
        settings.saveAdvanced(Provider.OPENAI,"https://o.example/v1/chat/completions","openai-custom","shared-sign","共享偏好")
        assertEquals(Provider.DEEPSEEK,settings.provider); assertEquals("ds-key",settings.apiKey)
        assertEquals(Provider.DEEPSEEK.model,settings.model)
        settings.saveBasic(Provider.OPENAI,"o-key",settings.webhook,true)
        assertEquals(Provider.OPENAI,settings.provider); assertEquals("openai-custom",settings.model)
        assertEquals("https://o.example/v1/chat/completions",settings.endpoint)
        settings.saveAdvanced(Provider.ANTHROPIC,"https://a.example/v1/messages","anthropic-custom",settings.signingKey,settings.prompt)
        assertEquals(Provider.OPENAI,settings.provider)
        settings.saveBasic(Provider.ANTHROPIC,"a-key",settings.webhook,true)
        assertEquals("a-key",settings.config(Provider.ANTHROPIC).key)
        assertEquals("o-key",settings.config(Provider.OPENAI).key); assertEquals("ds-key",settings.config(Provider.DEEPSEEK).key)
        settings.saveBasic(Provider.DEEPSEEK,settings.config(Provider.DEEPSEEK).key,settings.webhook,true)
        assertEquals("ds-key",settings.apiKey); assertEquals(Provider.DEEPSEEK.endpoint,settings.endpoint)
        assertEquals("shared-sign",settings.signingKey); assertEquals("共享偏好",settings.prompt)
    }
    @Test fun snapshotCannotMixProviderKeyOrEndpointAfterSwitching() = isolated("settings-snapshot-test") { settings,_ ->
        settings.saveBasic(Provider.DEEPSEEK,"ds-key","https://open.feishu.cn/open-apis/bot/v2/hook/old",true)
        settings.saveAdvanced(Provider.DEEPSEEK,"https://ds.example/chat/completions","ds-model","old-sign","old-prompt")
        val frozen=settings.snapshot()
        settings.saveAdvanced(Provider.OPENAI,"https://o.example/v1/chat/completions","o-model","new-sign","new-prompt")
        settings.saveBasic(Provider.OPENAI,"o-key","https://open.feishu.cn/open-apis/bot/v2/hook/new",true)
        assertEquals(Provider.DEEPSEEK,frozen.ai.provider); assertEquals("ds-key",frozen.ai.key)
        assertEquals("https://ds.example/chat/completions",frozen.ai.endpoint); assertEquals("ds-model",frozen.ai.model)
        assertEquals("old-prompt",frozen.ai.prompt); assertEquals("old-sign",frozen.signingKey)
        assertEquals("https://open.feishu.cn/open-apis/bot/v2/hook/old",frozen.webhook)
        val request=AiAdapter.request(frozen.ai,listOf(Notice(1,"k","p","应用","标题","正文",1)))
        assertEquals("Bearer ds-key",request.headers["Authorization"]); assertEquals(frozen.ai.endpoint,request.url)
        assertEquals("o-key",settings.snapshot().ai.key)
    }
    @Test fun clearingSecretsClearsAllProvidersAndDisablesCloud() = isolated("settings-clear-test") { settings,prefs ->
        Provider.entries.forEach { p -> settings.saveBasic(p,"key-${p.id}","https://open.feishu.cn/open-apis/bot/v2/hook/test",true) }
        settings.signingKey="shared-sign"
        settings.clearSecrets()
        Provider.entries.forEach { p -> assertEquals("",settings.config(p).key); assertFalse(prefs.contains("provider.${p.id}.key")) }
        assertEquals("",settings.webhook); assertEquals("",settings.signingKey); assertFalse(settings.enabled)
    }

    @Test fun languagePersistsWithoutChangingSavedCredentialsOrOtherSettings() = isolated("settings-language-test") { settings,prefs ->
        assertEquals(UiLanguage.CHINESE,settings.language)
        settings.saveBasic(Provider.OPENAI,"test-language-key","https://open.feishu.cn/open-apis/bot/v2/hook/language",false)
        settings.saveAdvanced(Provider.OPENAI,"https://example.com/v1/chat/completions","language-model","test-sign","不要翻译这条偏好")
        settings.minutes=35; settings.hideRecents=true
        val before=prefs.all.toMap(); val frozen=settings.snapshot()
        settings.language=UiLanguage.ENGLISH
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val reopened=Settings(context,"settings-language-test")
        assertEquals(UiLanguage.ENGLISH,reopened.language)
        assertEquals(before,prefs.all.filterKeys { it!="language" })
        assertEquals(UiLanguage.CHINESE,frozen.ai.language); assertEquals(UiLanguage.ENGLISH,reopened.snapshot().ai.language)
        assertEquals("test-language-key",reopened.apiKey); assertEquals("不要翻译这条偏好",reopened.prompt)
        assertEquals(35,reopened.minutes); assertTrue(reopened.hideRecents)
        reopened.language=UiLanguage.CHINESE
        assertEquals(UiLanguage.CHINESE,Settings(context,"settings-language-test").language)
        assertEquals(before,prefs.all.filterKeys { it!="language" })
    }

    @Test fun freshAndLanguageOnlySettingsDefaultLocalButLegacyDefaultsFeishu() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="settings-target-migration-test"
        val prefs=context.getSharedPreferences(name,0)
        try {
            prefs.edit().clear().commit()
            assertEquals(DeliveryTarget.LOCAL,Settings(context,name).destination)
            prefs.edit().clear().putString("language","en").commit()
            assertEquals(DeliveryTarget.LOCAL,Settings(context,name).destination)
            assertEquals(UiLanguage.ENGLISH,Settings(context,name).language)
            prefs.edit().clear().putBoolean("providers_migrated",true).putString("provider",Provider.OPENAI.id).commit()
            assertEquals(DeliveryTarget.FEISHU,Settings(context,name).destination)
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun localSnapshotAndSavesIgnoreAndPreserveUnreadableFeishuSecrets() = isolated("settings-local-secret-test") { settings,prefs ->
        settings.saveBasic(Provider.OPENAI,"test-key","",true,DeliveryTarget.LOCAL)
        prefs.edit().putString("hook","not-a-valid-ciphertext").putString("sign","also-not-a-valid-ciphertext").commit()
        val beforeHook=prefs.getString("hook",null); val beforeSign=prefs.getString("sign",null)
        val config=settings.snapshot(DeliveryTarget.LOCAL)
        assertEquals(DeliveryTarget.LOCAL,config.target); assertEquals("",config.webhook); assertEquals("",config.signingKey)
        settings.saveBasic(Provider.OPENAI,"new-test-key","",true,DeliveryTarget.LOCAL)
        settings.saveAdvanced(Provider.OPENAI,"https://example.com/v1/chat/completions","local-model","","本机偏好",DeliveryTarget.LOCAL)
        assertEquals(beforeHook,prefs.getString("hook",null)); assertEquals(beforeSign,prefs.getString("sign",null))
        assertEquals("new-test-key",settings.snapshot(DeliveryTarget.LOCAL).ai.key)
    }
    @Test fun requestedBatchDestinationWinsOverLaterSavedSelection() = isolated("settings-target-snapshot-test") { settings,_ ->
        settings.saveBasic(Provider.DEEPSEEK,"synthetic-key","https://open.feishu.cn/open-apis/bot/v2/hook/test",true,DeliveryTarget.FEISHU)
        settings.saveAdvanced(Provider.DEEPSEEK,Provider.DEEPSEEK.endpoint,Provider.DEEPSEEK.model,"synthetic-sign","",DeliveryTarget.FEISHU)
        val frozen=settings.snapshot(DeliveryTarget.FEISHU)
        settings.saveBasic(Provider.DEEPSEEK,"synthetic-key","",true,DeliveryTarget.LOCAL)
        assertEquals(DeliveryTarget.LOCAL,settings.destination)
        assertEquals(DeliveryTarget.FEISHU,frozen.target); assertTrue(frozen.webhook.isNotEmpty())
        assertEquals(DeliveryTarget.FEISHU,settings.snapshot(DeliveryTarget.FEISHU).target)
        assertEquals("synthetic-sign",settings.snapshot(DeliveryTarget.FEISHU).signingKey)
        assertEquals("",settings.snapshot(DeliveryTarget.LOCAL).signingKey)
    }

}
