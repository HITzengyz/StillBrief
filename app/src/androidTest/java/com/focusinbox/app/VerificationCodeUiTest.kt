package com.focusinbox.app

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Spinner
import android.widget.Switch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VerificationCodeUiTest {
    @Test fun feishuOnlySwitchSavesIndependentlyOfAi() {
        val ins=InstrumentationRegistry.getInstrumentation(); val c=ins.targetContext; val s=Settings(c)
        val previous=s.snapshot(); val previousCodes=s.forwardCodes; val previousLanguage=s.language
        s.saveBasic(s.provider,"","https://open.feishu.cn/open-apis/bot/v2/hook/example",false,DeliveryTarget.FEISHU,false)
        val a=ins.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        fun views(v: View): Sequence<View> = sequence { yield(v); if(v is ViewGroup) for(i in 0 until v.childCount) yieldAll(views(v.getChildAt(i))) }
        fun codeSwitch()=views(a.window.decorView).filterIsInstance<Switch>().first { it.text==a.getString(R.string.codes_enable) }
        fun picker()=views(a.window.decorView).filterIsInstance<Spinner>().first { it.contentDescription==a.getString(R.string.delivery_label) }
        try {
            ins.runOnMainSync { a.findViewById<ViewGroup>(R.id.page_navigation).getChildAt(2).performClick() }
            ins.waitForIdleSync()
            ins.runOnMainSync {
                assertEquals(View.VISIBLE,(codeSwitch().parent as View).visibility)
                codeSwitch().isChecked=true
                views(a.window.decorView).filterIsInstance<Button>().first { it.text==a.getString(R.string.save_settings) }.performClick()
            }
            assertTrue(s.codesActive); assertFalse(s.enabled)
            ins.runOnMainSync { picker().setSelection(DeliveryTarget.entries.indexOf(DeliveryTarget.LOCAL)) }
            ins.waitForIdleSync()
            ins.runOnMainSync {
                assertEquals(View.GONE,(codeSwitch().parent as View).visibility)
                views(a.window.decorView).filterIsInstance<Button>().first { it.text==a.getString(R.string.save_settings) }.performClick()
            }
            assertFalse(s.codesActive); assertTrue(s.forwardCodes)
        } finally {
            ins.runOnMainSync { a.finish() }
            s.saveBasic(previous.ai.provider,previous.ai.key,previous.webhook,previous.enabled,previous.target,previousCodes)
            s.language=previousLanguage
        }
    }
}
