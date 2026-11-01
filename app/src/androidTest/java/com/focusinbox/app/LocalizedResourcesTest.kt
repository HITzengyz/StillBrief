package com.focusinbox.app

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LocalizedResourcesTest {
    private fun context(language: String): Context {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(language))) })
    }
    @Test fun packagedBrandAndActionsHaveBothLanguages() {
        val zh=context("zh"); val en=context("en")
        assertEquals("息间",zh.getString(R.string.app_name)); assertEquals("StillBrief",en.getString(R.string.app_name))
        assertEquals("开始专注",zh.getString(R.string.timer_start)); assertEquals("Start focus",en.getString(R.string.timer_start))
        assertEquals("15 min",en.getString(R.string.minutes_value,15))
        assertEquals("15分钟",zh.getString(R.string.minutes_value,15))
        assertNotEquals(zh.getString(R.string.date_pattern),en.getString(R.string.date_pattern))
    }
    @Test fun stableErrorsRenderEnglishWithoutChangingSavedErrorCodes() {
        val en=context("en")
        ErrorCode.entries.forEach { code ->
            val stored=code.encoded(if(code==ErrorCode.HTTP) 401 else null)
            val rendered=LocaleHelper.renderError(en,stored)
            assertTrue(rendered.isNotBlank()); assertFalse(rendered.startsWith("@error:"))
            assertFalse(rendered.any { it in '\u4e00'..'\u9fff' })
            if(code==ErrorCode.HTTP) assertTrue(rendered.contains("401"))
            assertEquals(stored,code.encoded(if(code==ErrorCode.HTTP) 401 else null))
        }
    }
}
