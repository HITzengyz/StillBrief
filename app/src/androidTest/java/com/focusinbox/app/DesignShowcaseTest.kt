package com.focusinbox.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicitly opted-in synthetic demonstration, in an isolated application ID. Never calls an AI API. */
@RunWith(AndroidJUnit4::class)
class DesignShowcaseTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=ins.targetContext
    private fun main(block: ()->Unit)=ins.runOnMainSync(block)
    private fun views(v: View): Sequence<View> = sequence {
        yield(v); if(v is ViewGroup) for(i in 0 until v.childCount) yieldAll(views(v.getChildAt(i)))
    }
    private fun navigate(a: MainActivity,page: Int) {
        main { a.findViewById<ViewGroup>(R.id.page_navigation).getChildAt(page).performClick() }
        val end=SystemClock.uptimeMillis()+3000
        var ready=false
        while(!ready && SystemClock.uptimeMillis()<end) {
            ins.waitForIdleSync()
            main { val h=a.findViewById<FrameLayout>(R.id.page_host); ready=h.childCount==1 && h.getChildAt(0).alpha==1f }
            if(!ready) SystemClock.sleep(30)
        }
        assertTrue(ready)
    }
    private fun capture(name: String) {
        ins.waitForIdleSync(); SystemClock.sleep(300)
        val bitmap=ins.uiAutomation.takeScreenshot()!!
        File(c.getExternalFilesDir(null),"design-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Test fun captureTaskStatesInBothThemes() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("designCapture")=="true")
        require(c.packageName.endsWith(".verify26"))
        val settings=Settings(c)
        val english=InstrumentationRegistry.getArguments().getString("designLanguage")=="en"
        settings.language=if(english) UiLanguage.ENGLISH else UiLanguage.CHINESE
        if(android.os.Build.VERSION.SDK_INT>=33) c.getSystemService(android.app.LocaleManager::class.java).applicationLocales=android.os.LocaleList.forLanguageTags(settings.language.tag)
         settings.minutes=35; settings.focusMode=FocusMode.LOOP
        settings.saveBasic(Provider.DEEPSEEK,"synthetic-design-example","",false,DeliveryTarget.LOCAL,false)
        c.deleteDatabase("inbox.db")
        val now=System.currentTimeMillis()
        Store(c).use { db ->
            db.startFocus(35,FocusMode.SINGLE,now-34*60_000)
            db.insert(Notice(key="meeting",pkg="demo.messages",app="微信",title="张三",body="明天下午三点开会，确认下时间。",at=now-33*60_000,kind="private"))
            db.insert(Notice(key="chat",pkg="demo.groups",app="QQ",title="同学群",body="今晚有人一起跑步吗？",at=now-32*60_000,kind="group"))
            val batch=db.createBatch(now-8*60_000)!!
            db.transition(batch,"QUEUED","ANALYZING")
            db.transition(batch,"ANALYZING","READY","张三请你确认明天下午三点的会议时间。",ids="1")
            db.transition(batch,"READY","SENDING"); db.transition(batch,"SENDING","DONE")
            db.stopFocus(now-7*60_000)
            db.startFocus(35,FocusMode.LOOP,now-6*60_000)
            listOf(
                Triple("邮件","项目组","本周评审材料已更新，请在周五前确认。"),
                Triple("微信","李明","我把参考资料发给你了，空了看看。"),
                Triple("短信","快递服务","包裹已到达驿站，取件码 3-2041。")
            ).forEachIndexed { i,(app,title,body) ->
                db.insert(Notice(key="pending-$i",pkg="demo.$i",app=app,title=title,body=body,at=now-3*60_000+i*10_000,kind="message"))
            }
        }
        settings.enabled=true
        try {
            for(mode in listOf(AppearanceMode.LIGHT,AppearanceMode.DARK)) {
                settings.appearance=mode
                val a=ins.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
                val prefix=(if(english) "en-" else "")+mode.name.lowercase()
                var sheet: DurationPicker?=null
                try {
                    capture("$prefix-focus")
                    navigate(a,1); capture("$prefix-summaries")
                    main { views(a.window.decorView).filterIsInstance<Button>().first { it.text==a.getString(R.string.design_notifications) }.performClick() }
                    ins.waitForIdleSync(); capture("$prefix-notifications")
                    navigate(a,2)
                    main {
                        val save=views(a.window.decorView).filterIsInstance<Button>().first { it.text==a.getString(R.string.save_settings) }
                        val visible=android.graphics.Rect()
                        assertTrue("Save stays visible above navigation",save.getGlobalVisibleRect(visible))
                        assertEquals(save.height,visible.height())
                    }
                    capture("$prefix-settings")
                    main { views(a.window.decorView).filterIsInstance<ScrollView>().first().fullScroll(View.FOCUS_DOWN) }
                    capture("$prefix-general")
                    navigate(a,0)
                    main { sheet=DurationPicker(a,35,a.getString(R.string.duration_default_title)) {}.also { it.show() } }
                    capture("$prefix-duration")
                    main { sheet!!.dismiss(); sheet=null }
                    main { a.findViewById<Button>(R.id.focus_mode_menu).performClick() }
                    capture("$prefix-mode")
                } finally { main { sheet?.dismiss(); a.finish() }; ins.waitForIdleSync() }
            }
        } finally {
            settings.enabled=false
            Store(c).use { db -> db.stopFocus(); db.clear() }
            settings.clearSecrets()
        }
    }
}
