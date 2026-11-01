package com.focusinbox.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var settings: Settings
    private val handler=Handler(Looper.getMainLooper())
    private var page=0
    private var clock: TextView?=null
    private var status: TextView?=null
    private var pending: TextView?=null
    private val tick=object: Runnable { override fun run() { if(page==0) update(); handler.postDelayed(this,1000) } }
    private val ink=Color.rgb(28,53,49)
    private val green=Color.rgb(35,121,111)
    private fun dp(n: Int)=(n*resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); settings=Settings(this)
        window.decorView.setOnApplyWindowInsetsListener { v,insets ->
            if(android.os.Build.VERSION.SDK_INT>=30) { val bars=insets.getInsets(WindowInsets.Type.systemBars()); v.setPadding(bars.left,bars.top,bars.right,bars.bottom) }
            else { @Suppress("DEPRECATION") v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom) }
            insets
        }
        show(0)
    }
    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }
    private fun label(text: String,size: Float=15f,bold: Boolean=false,parent: LinearLayout=content): TextView = TextView(this).also {
        it.text=text; it.textSize=size; it.setTextColor(ink); it.setPadding(0,dp(6),0,dp(6)); if(bold) it.setTypeface(null,Typeface.BOLD); parent.addView(it)
    }
    private fun button(text: String,parent: LinearLayout=content,primary: Boolean=false,action: ()->Unit): Button = Button(this).also {
        it.text=text; it.isAllCaps=false; it.textSize=15f; it.setTextColor(if(primary) Color.WHITE else green)
        it.stateListAnimator=null; it.elevation=0f
        val shape=GradientDrawable().apply { setColor(if(primary) green else Color.rgb(232,241,236)); cornerRadius=dp(12).toFloat() }
        it.background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(40,35,121,111)),shape,null)
        parent.addView(it,LinearLayout.LayoutParams(-1,dp(54)).apply { topMargin=dp(5) }); it.setOnClickListener { action() }
    }
    private fun card(): LinearLayout = LinearLayout(this).also {
        it.orientation=LinearLayout.VERTICAL; it.setPadding(dp(18),dp(14),dp(18),dp(14))
        it.background=GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=dp(20).toFloat() }
        content.addView(it,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(14); bottomMargin=dp(6) })
    }
    private fun show(which: Int) {
        page=which; clock=null; status=null; pending=null
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(245,247,243)) }
        val top=LinearLayout(this).apply { setPadding(dp(20),dp(16),dp(20),0); orientation=LinearLayout.VERTICAL }
        label("息间",28f,true,top); label("把注意力留给眼前，把消息留到息间。",13f,parent=top); root.addView(top)
        val scroll=ScrollView(this); content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(4),dp(20),dp(24)) }; scroll.addView(content); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val nav=LinearLayout(this).apply { gravity=Gravity.CENTER; setPadding(dp(12),0,dp(12),dp(6)) }
        listOf("专注","通知","设置").forEachIndexed { i,name -> nav.addView(Button(this).apply { text=if(i==which) "● $name" else name; setTextColor(green); textSize=14f; stateListAnimator=null; elevation=0f; background=GradientDrawable().apply { setColor(if(i==which) Color.rgb(223,237,226) else Color.TRANSPARENT); cornerRadius=dp(12).toFloat() }; setOnClickListener { show(i) } },LinearLayout.LayoutParams(0,dp(54),1f)) }; root.addView(nav)
        setContentView(root)
        when(which) { 0 -> home(); 1 -> history(); 2 -> preferences() }
    }
    private fun home() {
        val c=card(); label("专注一刻",17f,true,c)
        clock=label("25:00",58f,true,c).apply { gravity=Gravity.CENTER }
        status=label("",14f,parent=c)
        val t=Store(this).use { it.timer() }
        button(if(t.state=="PAUSED") "继续专注" else "开始专注",c,primary=true) { TimerEngine.start(this); show(0) }
        button("暂停计时",c) { TimerEngine.pause(this); show(0) }
        button("现在休息 · 结束并分析",c) { TimerEngine.rest(this); show(0); toast("已取当前批次；之后的新通知留待下一批") }
        button("立即分析当前缓存",c) { BatchEngine.trigger(this); show(0); toast("已开始处理，结果与重试入口见通知页") }
        val info=card(); pending=label("",14f,parent=info)
        label("全时段采集已获授权的系统通知。暂停只暂停计时，休息、分析过程中收到的通知仍进入下一批。",13f,parent=info)
        button("授权通知读取",info) { startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        if(!TimerEngine.exact(this)) button("开启准时提醒权限",info) { startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:$packageName"))) }
        button("打开声音与勿扰设置",info) { startActivity(Intent(AndroidSettings.ACTION_SOUND_SETTINGS)) }
        label("本应用不会关闭其他应用通知。勿扰和隐藏通知正文会影响你能看到或采集到的内容。",12f)
        update()
    }
    private fun update() {
        Store(this).use { db ->
            var t=db.timer()
            if(t.state=="RUNNING" && t.end<=System.currentTimeMillis()) { TimerEngine.finish(this,t.token); t=db.timer() }
            val millis=when(t.state) { "RUNNING" -> (t.end-System.currentTimeMillis()).coerceAtLeast(0); "PAUSED" -> t.remaining; else -> settings.minutes*60_000L }
            val sec=(millis+999)/1000; clock?.text="%02d:%02d".format(sec/60,sec%60)
            status?.text=when(t.state) { "RUNNING" -> "正在专注 · 到时自动分析"; "PAUSED" -> "计时已暂停 · 通知仍在收集"; else -> "休息中 · 通知继续缓存到下一批" }
            val last=db.batches().firstOrNull()
            pending?.text="监听：${if(InboxListener.connected) "已连接" else "未连接，请授权或重新开启"}\n待分析 ${db.pendingCount()} 条 · 云端分析${if(settings.enabled) "已开启" else "未开启"}\n${if(TimerEngine.exact(this)) "准时闹钟可用" else "未获准时闹钟权限，后台/熄屏分析可能延迟"}\n"+(last?.let { "最近批次 #${it.id}：${stateName(it.state)}${if(it.error.isNotEmpty()) "\n${it.error}" else ""}" } ?: "还没有分析记录")
        }
    }
    private fun stateName(s: String)=mapOf("QUEUED" to "等待分析","ANALYZING" to "分析中","READY" to "等待发送","SENDING" to "发送中","DONE" to "已送达","EMPTY" to "无需提醒（未发送）","SEND_FAILED" to "发送失败 · 可手动重试","FAILED" to "分析失败 · 可手动重试","UNCERTAIN" to "送达不确定 · 请先检查飞书")[s] ?: s
    private fun kindName(kind: String)=mapOf("private" to "私聊","group" to "群聊","message" to "通信消息","mail" to "邮件","sms" to "短信","call" to "来电","missed_call" to "未接来电")[kind] ?: "一般通知"
    private fun time(t: Long)=SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(Date(t))
    private fun history() {
        label("通知与批次",23f,true)
        button("刷新") { show(1) }
        Store(this).use { db ->
            val batches=db.batches()
            label("最近批次",17f,true)
            if(batches.isEmpty()) label("开始专注或立即分析后，记录会出现在这里。",13f)
            batches.forEach { b ->
                val c=card(); label("#${b.id} · ${stateName(b.state)}",16f,true,c); label(time(b.created),12f,parent=c)
                if(b.message.isNotBlank()) label(b.message,14f,parent=c)
                if(b.error.isNotBlank()) label(b.error,14f,parent=c)
                button("查看本批原始通知（${db.notices(b.id).size}条）",c) { showNotices(Store(this).use { it.notices(b.id) }) }
                if(b.state in setOf("FAILED","SEND_FAILED","UNCERTAIN")) button("手动重试",c) {
                    AlertDialog.Builder(this).setTitle("重试此批次").setMessage(if(b.state=="UNCERTAIN") "飞书可能已收到上次提醒。继续会再次发送，可能重复。" else "将重试本批分析和发送，其他新通知仍在下一批。")
                        .setNegativeButton("取消",null).setPositiveButton("确认重试") { _,_ -> BatchEngine.retry(this,b.id); show(1) }.show()
                }
            }
            label("近期采集 · 最多显示120条",17f,true)
            db.recent().forEach { n -> val c=card(); label("${n.app} · ${n.title}",15f,true,c); label("${time(n.at)} · ${kindName(n.kind)}${if(n.summary) " · 汇总" else ""}",12f,parent=c); label(n.body.ifBlank { "（正文不可见）" },14f,parent=c) }
        }
    }
    private fun showNotices(notices: List<Notice>) {
        val view=TextView(this).apply { text=notices.joinToString("\n\n") { "#${it.id} ${it.app} ${time(it.at)}\n${it.title}\n${it.body}" }; setPadding(dp(20),dp(12),dp(20),dp(12)); setTextIsSelectable(true) }
        AlertDialog.Builder(this).setTitle("本批完整记录").setView(ScrollView(this).apply { addView(view) }).setPositiveButton("关闭",null).show()
    }
    private fun field(title: String,value: String,secret: Boolean=false,multiline: Boolean=false): EditText {
        label(title,14f,true)
        return EditText(this).also { it.setText(value); it.textSize=14f; it.inputType=if(secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else if(multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_CLASS_TEXT; it.setSingleLine(!multiline); content.addView(it,LinearLayout.LayoutParams(-1,-2)) }
    }
    private fun preferences() {
        label("设置",23f,true)
        label("采集始终在本机进行。只有开启下方开关后，分析才会把本批通知正文发送至你设置的 DeepSeek 兼容接口；飞书仅接收选中通知的摘要。",14f)
        val enabled=Switch(this).apply { text="启用云端分析与飞书提醒"; isChecked=settings.enabled }; content.addView(enabled)
        enabled.setOnCheckedChangeListener { _,on ->
            if(on && !settings.enabled) AlertDialog.Builder(this).setTitle("确认发送通知正文到云端")
                .setMessage("本批通知可能含联系人、聊天内容、验证码或事务信息。分析将直接发到你设置的 HTTPS 接口，摘要发到你的飞书机器人。确认后还需保存设置。")
                .setPositiveButton("我知晓并启用") { _,_ -> enabled.tag=true }
                .setNegativeButton("取消") { _,_ -> enabled.isChecked=false }.setOnCancelListener { enabled.isChecked=false }.show()
        }
        var secretError=false
        fun secret(read: ()->String): String = try { read() } catch(_: Exception) { secretError=true; "" }
        val api=field("DeepSeek API Key",secret { settings.apiKey },true)
        val endpoint=field("AI 接口完整地址（HTTPS）",settings.endpoint)
        val model=field("模型名称",settings.model)
        val hook=field("飞书 / Lark Webhook",secret { settings.webhook },true)
        val sign=field("机器人签名密钥（可选）",secret { settings.signingKey },true)
        if(secretError) label("本机密钥不可解密，请重新填写并保存。",14f,true)
        val mins=field("番茄时长 · 1—180分钟",settings.minutes.toString()).apply { inputType=InputType.TYPE_CLASS_NUMBER }
        val prompt=field("补充筛选偏好（私聊仍会保留）",settings.prompt,multiline=true)
        val exclusions=field("排除应用包名（每行一个；默认只排除息间自身）",settings.excluded.joinToString("\n"),multiline=true)
        label("飞书同事消息正常采集。仅过滤飞书/Lark中含【息间提醒】的机器人行。可在下方按应用选择排除。",12f)
        button("选择已采集到的应用") {
            val apps=Store(this).use { it.apps() }; val current=exclusions.text.toString().lines().map { it.trim() }.toMutableSet(); val checked=apps.map { it.first in current }.toBooleanArray()
            AlertDialog.Builder(this).setTitle("勾选要排除的应用").setMultiChoiceItems(apps.map { "${it.second}\n${it.first}" }.toTypedArray(),checked) { _,i,on -> if(on) current.add(apps[i].first) else current.remove(apps[i].first) }.setPositiveButton("确定") { _,_ -> exclusions.setText(current.filter { it.isNotBlank() }.joinToString("\n")) }.setNegativeButton("取消",null).show()
        }
        button("保存设置",primary=true) {
            val minutes=mins.text.toString().toIntOrNull()
            when {
                minutes==null || minutes !in 1..180 -> toast("时长须为1—180分钟")
                !Network.validUrl(endpoint.text.toString().trim()) -> toast("接口须为有效HTTPS完整地址")
                model.text.isBlank() -> toast("请填写模型名称")
                enabled.isChecked && (api.text.isBlank() || !Network.validUrl(hook.text.toString().trim(),true)) -> toast("启用前请填写API Key及有效飞书Webhook")
                enabled.isChecked && !settings.enabled && enabled.tag!=true -> toast("请先确认云端发送说明")
                else -> try {
                    settings.apiKey=api.text.toString().trim(); settings.webhook=hook.text.toString().trim(); settings.signingKey=sign.text.toString().trim()
                    settings.endpoint=endpoint.text.toString().trim(); settings.model=model.text.toString().trim(); settings.minutes=minutes
                    settings.prompt=prompt.text.toString(); settings.excluded=exclusions.text.toString().lines().map { it.trim() }.filter { it.isNotBlank() }.toSet(); settings.enabled=enabled.isChecked
                    toast("已保存；时长在下一次新专注生效")
                } catch(_: Exception) { toast("密钥保存失败，配置未启用"); settings.enabled=false }
            }
        }
        label("记录策略：待处理、失败、送达不确定的批次一直保留；已完成/无需提醒批次保留30天。卸载或清除应用数据会删除全部记录与密钥。",12f)
        button("删除本机通知记录") {
            AlertDialog.Builder(this).setTitle("永久删除本机通知和批次？").setMessage("此操作不可撤销。设置和密钥会保留。正在分析或发送时禁止删除。")
                .setNegativeButton("取消",null).setPositiveButton("删除") { _,_ -> toast(if(Store(this).use { it.clear() }) "已清除" else "当前正在分析或发送，请稍后再试") }.show()
        }
        button("删除保存的密钥并关闭云端分析") { AlertDialog.Builder(this).setTitle("删除密钥？").setMessage("将清除API Key、Webhook与签名密钥。已经发出的请求无法撤回。")
            .setNegativeButton("取消",null).setPositiveButton("删除") { _,_ -> settings.clearSecrets(); show(2) }.show() }
    }
    private fun toast(message: String)=Toast.makeText(this,message,Toast.LENGTH_LONG).show()
}
