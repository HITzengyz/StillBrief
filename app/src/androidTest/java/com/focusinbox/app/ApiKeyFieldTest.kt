package com.focusinbox.app

import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Spinner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiKeyFieldTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private lateinit var originalLanguage: UiLanguage
    @Before fun launch() {
        originalLanguage=LocaleHelper.current(instrumentation.targetContext)
        activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        instrumentation.waitForIdleSync(); navigate(2)
    }
    @After fun close() {
        onMain { LocaleHelper.change(activity,originalLanguage); if(!activity.isDestroyed) activity.finish() }
        instrumentation.waitForIdleSync()
    }
    @Suppress("UNCHECKED_CAST") private fun <T> onMain(action: ()->T): T {
        var result: T?=null; var error: Throwable?=null
        instrumentation.runOnMainSync { try { result=action() } catch(t: Throwable) { error=t } }
        error?.let { throw it }; return result as T
    }
    private fun await(condition: ()->Boolean) {
        val deadline=SystemClock.uptimeMillis()+4000
        while(!condition() && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(25)
        assertTrue("UI did not settle",condition())
    }
    private fun views(view: View): Sequence<View> = sequence {
        yield(view); if(view is ViewGroup) for(index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
    }
    private fun content(): ViewGroup { val host=activity.findViewById<FrameLayout>(R.id.page_host); return host.getChildAt(host.childCount-1) as ViewGroup }
    private fun key()=views(content()).filterIsInstance<ApiKeyField>().single()
    private fun pickers()=views(content()).filterIsInstance<Spinner>().filter { it.id!=R.id.appearance_picker }.toList()
    private fun action(id: Int)=key().findViewById<ImageButton>(id)
    private fun navigate(index: Int) {
        onMain { (activity.findViewById<ViewGroup>(R.id.page_navigation).getChildAt(index) as Button).performClick() }
        await { onMain { val host=activity.findViewById<FrameLayout>(R.id.page_host); host.childCount==1 && host.getChildAt(0).alpha==1f } }
    }
    @Test fun revealCopyAndClearOperateOnlyOnCurrentDraft() {
        val before=instrumentation.targetContext.getSharedPreferences("settings",0).all.toMap()
        onMain {
            val field=key().editText; field.setText("")
            assertFalse(action(R.id.api_key_copy).isEnabled); assertFalse(action(R.id.api_key_clear).isEnabled)
            field.setText("synthetic-copy-key"); field.setSelection(2,8)
            assertTrue(field.transformationMethod is PasswordTransformationMethod)
            assertTrue(action(R.id.api_key_reveal).performClick())
            assertFalse(field.transformationMethod is PasswordTransformationMethod)
            assertEquals(2,field.selectionStart); assertEquals(8,field.selectionEnd)
            assertEquals("synthetic-copy-key",field.text.toString())
            action(R.id.api_key_copy).performClick()
            val clipboard=activity.getSystemService(ClipboardManager::class.java)
            assertEquals("synthetic-copy-key",clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertTrue(clipboard.primaryClipDescription!!.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
            if(Build.VERSION.SDK_INT>=28) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            action(R.id.api_key_reveal).performClick()
            assertTrue(field.transformationMethod is PasswordTransformationMethod)
            assertEquals(2,field.selectionStart); assertEquals(8,field.selectionEnd)
            action(R.id.api_key_clear).performClick()
            assertEquals("",field.text.toString()); assertTrue(field.transformationMethod is PasswordTransformationMethod)
            assertFalse(action(R.id.api_key_copy).isEnabled); assertFalse(action(R.id.api_key_clear).isEnabled)
        }
        assertEquals(before,instrumentation.targetContext.getSharedPreferences("settings",0).all)
    }
    @Test fun providerAndPageSwitchConcealKeysAndKeepSeparateUnsavedDrafts() {
        val before=instrumentation.targetContext.getSharedPreferences("settings",0).all.toMap()
        var first=0; var next=0
        onMain {
            first=pickers()[1].selectedItemPosition; next=(first+1)%Provider.entries.size
            key().editText.setText("provider-A-draft"); action(R.id.api_key_reveal).performClick()
            pickers()[1].setSelection(next,false)
        }
        await { onMain { key().editText.text.toString()!="provider-A-draft" } }
        onMain {
            assertTrue(key().editText.transformationMethod is PasswordTransformationMethod)
            key().editText.setText("provider-B-draft"); action(R.id.api_key_reveal).performClick()
            pickers()[1].setSelection(first,false)
        }
        await { onMain { key().editText.text.toString()=="provider-A-draft" } }
        onMain {
            assertTrue(key().editText.transformationMethod is PasswordTransformationMethod)
            action(R.id.api_key_clear).performClick(); pickers()[1].setSelection(next,false)
        }
        await { onMain { key().editText.text.toString()=="provider-B-draft" } }
        onMain { action(R.id.api_key_reveal).performClick() }
        navigate(0); navigate(2)
        onMain {
            assertEquals("provider-B-draft",key().editText.text.toString())
            assertTrue(key().editText.transformationMethod is PasswordTransformationMethod)
            pickers()[1].setSelection(first,false)
        }
        await { onMain { key().editText.length()==0 } }
        assertEquals(before,instrumentation.targetContext.getSharedPreferences("settings",0).all)
    }
    @Test fun backgroundAndLanguageChangesConcealWithoutLosingText() {
        onMain {
            key().editText.setText("locale-background-draft"); action(R.id.api_key_reveal).performClick()
            instrumentation.callActivityOnPause(activity)
            assertTrue(key().editText.transformationMethod is PasswordTransformationMethod)
            instrumentation.callActivityOnResume(activity)
            action(R.id.api_key_reveal).performClick()
            val next=if(Settings(activity).language==UiLanguage.CHINESE) UiLanguage.ENGLISH else UiLanguage.CHINESE
            pickers()[0].setSelection(UiLanguage.entries.indexOf(next),false)
        }
        await { onMain { key().editText.transformationMethod is PasswordTransformationMethod } }
        onMain { assertEquals("locale-background-draft",key().editText.text.toString()) }
    }
}
