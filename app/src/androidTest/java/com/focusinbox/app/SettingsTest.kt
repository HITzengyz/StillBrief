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
}
