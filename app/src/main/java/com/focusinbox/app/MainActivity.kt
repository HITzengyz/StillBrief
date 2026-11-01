package com.focusinbox.app

import android.app.Activity
import android.app.AlertDialog
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import android.os.PowerManager
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
    private var startButton: Button?=null
    private var pauseButton: Button?=null
    private var restButton: Button?=null
    private var noticeDialog: AlertDialog?=null
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
    override fun onResume() {
        super.onResume()
        getSystemService(ActivityManager::class.java).appTasks.forEach { it.setExcludeFromRecents(settings.hideRecents) }
        if(page==4) show(4)
        handler.post(tick)
    }
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
        page=which; clock=null; status=null; pending=null; startButton=null; pauseButton=null; restButton=null
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(245,247,243)) }
        val top=LinearLayout(this).apply { setPadding(dp(20),dp(16),dp(20),0); orientation=LinearLayout.VERTICAL }
        label("息间",28f,true,top); label("把注意力留给眼前，把消息留到息间。",13f,parent=top); root.addView(top)
        val scroll=ScrollView(this); content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(4),dp(20),dp(24)) }; scroll.addView(content); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val nav=LinearLayout(this).apply { gravity=Gravity.CENTER; setPadding(dp(12),0,dp(12),dp(6)) }
        listOf("专注","通知","设置").forEachIndexed { i,name -> nav.addView(Button(this).apply { text=if(i==which || i==2 && which>=2) "● $name" else name; setTextColor(green); textSize=14f; stateListAnimator=null; elevation=0f; background=GradientDrawable().apply { setColor(if(i==which || i==2 && which>=2) Color.rgb(223,237,226) else Color.TRANSPARENT); cornerRadius=dp(12).toFloat() }; setOnClickListener { show(i) } },LinearLayout.LayoutParams(0,dp(54),1f)) }; root.addView(nav)
        setContentView(root)
        when(which) { 0 -> home(); 1 -> history(); 2 -> preferences(); 3 -> filtersPage(); 4 -> backgroundPage() }
    }
    private fun home() {
        val c=card(); label("专注一刻",17f,true,c)
        clock=label("25:00",58f,true,c).apply { gravity=Gravity.CENTER; contentDescription="专注时间，点击调整时长"; setOnClickListener { editDuration() } }
        button("点击调整时长",c) { editDuration() }
        status=label("",14f,parent=c)
        startButton=button("开始专注",c,primary=true) { TimerEngine.start(this); show(0) }
        pauseButton=button("暂停计时",c) { TimerEngine.pause(this); show(0) }
        restButton=button("现在休息 · 结束并分析",c) { TimerEngine.rest(this); show(0); toast("已取当前批次；之后的新通知留待下一批") }
        button("立即分析当前缓存",c) { BatchEngine.trigger(this); show(0); toast("已开始处理，结果与重试入口见通知页") }
        val info=card(); pending=label("",14f,parent=info)
        label("暂停只暂停计时。休息、分析过程中收到的通知仍进入下一批；已排除内容不再收集。",13f,parent=info)
        button("后台运行与保活",info) { show(4) }
        button("通知过滤",info) { show(3) }
        button("打开声音与勿扰设置",info) { openSystem(Intent(AndroidSettings.ACTION_SOUND_SETTINGS)) }
        label("本应用不会关闭其他应用通知。勿扰和隐藏通知正文会影响你能看到或采集到的内容。",12f)
        update()
    }
    private fun editDuration() {
        val t=Store(this).use { it.timer() }
        if(t.state in setOf("RUNNING","PAUSED")) {
            AlertDialog.Builder(this).setTitle("调整时长")
                .setItems(arrayOf("仅设置下次默认时长（当前不变）","调整本轮剩余时间（保持${if(t.state=="RUNNING") "运行" else "暂停"}）")) { _,which -> durationPicker(which==1) }.show()
        } else durationPicker(false)
    }
    private fun durationPicker(current: Boolean) {
        val options=arrayOf("15分钟","25分钟","35分钟","45分钟","60分钟","自定义 · 1—180分钟")
        AlertDialog.Builder(this).setTitle(if(current) "设置本轮剩余时间" else "设置下次默认时长")
            .setItems(options) { _,which ->
                if(which<5) applyDuration(listOf(15,25,35,45,60)[which],current)
                else {
                    val input=EditText(this).apply { inputType=InputType.TYPE_CLASS_NUMBER; hint="1—180分钟"; setText(settings.minutes.toString()); setSelectAllOnFocus(true) }
                    val dialog=AlertDialog.Builder(this).setTitle(if(current) "本轮剩余分钟数" else "下次默认分钟数").setView(input)
                        .setNegativeButton("取消",null).setPositiveButton("确定",null).create()
                    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val value=input.text.toString().toIntOrNull()
                        if(value==null || value !in 1..180) input.error="请输入1—180的整数"
                        else { applyDuration(value,current); dialog.dismiss() }
                    } }; dialog.show()
                }
            }.show()
    }
    private fun applyDuration(minutes: Int,current: Boolean) {
        if(current) toast(if(TimerEngine.adjustRemaining(this,minutes)) "本轮剩余时间已调整，运行/暂停状态保持不变" else "本轮已结束，请设置下次默认时长")
        else { settings.minutes=minutes; toast("下次默认时长已设为${minutes}分钟，当前计时不变") }
        show(0)
    }
    private fun update() {
        Store(this).use { db ->
            var t=db.timer()
            if(t.state=="RUNNING" && t.end<=System.currentTimeMillis()) { TimerEngine.finish(this,t.token); t=db.timer() }
            val millis=when(t.state) { "RUNNING" -> (t.end-System.currentTimeMillis()).coerceAtLeast(0); "PAUSED" -> t.remaining; else -> settings.minutes*60_000L }
            startButton?.apply { visibility=if(t.state=="RUNNING") View.GONE else View.VISIBLE; text=if(t.state=="PAUSED") "继续专注" else "开始专注" }
            pauseButton?.visibility=if(t.state=="RUNNING") View.VISIBLE else View.GONE
            restButton?.visibility=if(t.state=="IDLE") View.GONE else View.VISIBLE
            val sec=(millis+999)/1000; clock?.text="%02d:%02d".format(sec/60,sec%60)
            status?.text=when(t.state) { "RUNNING" -> "正在专注 · 到时自动分析"; "PAUSED" -> "计时已暂停 · 通知仍在收集"; else -> "下次默认 ${settings.minutes} 分钟 · 点击时间可调整" }
            val last=db.batches().firstOrNull()
            pending?.text="监听：${if(InboxListener.connected) "已连接" else "未连接，请授权或重新开启"}\n待分析 ${db.pendingCount()} 条 · 云端分析${if(settings.enabled) "已开启" else "未开启"}\n${if(TimerEngine.exact(this)) "准时闹钟可用" else "未获准时闹钟权限，后台/熄屏分析可能延迟"}\n"+(last?.let { "最近批次 #${it.id}：${stateName(it.state)}${if(it.error.isNotEmpty()) "\n${it.error}" else ""}" } ?: "还没有分析记录")
        }
    }
    private fun stateName(s: String)=mapOf("QUEUED" to "等待分析","ANALYZING" to "分析中","READY" to "等待发送","SENDING" to "发送中","DONE" to "已送达","EMPTY" to "无需提醒（未发送）","SEND_FAILED" to "发送失败 · 可手动重试","FAILED" to "分析失败 · 可手动重试","UNCERTAIN" to "送达不确定 · 请先检查飞书")[s] ?: s
    private fun kindName(kind: String)=mapOf("private" to "私聊","group" to "群聊","message" to "通信消息","mail" to "邮件","sms" to "短信","call" to "来电","missed_call" to "未接来电")[kind] ?: "一般通知"
    private fun time(t: Long)=SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(Date(t))
    private fun history() {
        label("通知与批次",23f,true)
        button("通知过滤 · 管理不想收集的消息") { show(3) }
        button("刷新") { show(1) }
        Store(this).use { db ->
            val batches=db.batches()
            label("最近批次",17f,true)
            if(batches.isEmpty()) label("开始专注或立即分析后，记录会出现在这里。",13f)
            batches.forEach { b ->
                val c=card(); label("#${b.id} · ${stateName(b.state)}",16f,true,c); label(time(b.created),12f,parent=c)
                if(b.message.isNotBlank()) label(b.message,14f,parent=c)
                if(b.error.isNotBlank()) label(b.error,14f,parent=c)
                val unprocessed=b.state in setOf("QUEUED","FAILED")
                val records=if(unprocessed) db.visibleNotices(b.id) else db.notices(b.id)
                button("查看本批原始通知（${records.size}条）",c) { showNotices(Store(this).use { if(unprocessed) it.visibleNotices(b.id) else it.notices(b.id) },!unprocessed) }
                if(b.state in setOf("FAILED","SEND_FAILED","UNCERTAIN")) button("手动重试",c) {
                    AlertDialog.Builder(this).setTitle("重试此批次").setMessage(if(b.state=="UNCERTAIN") "飞书可能已收到上次提醒。继续会再次发送，可能重复。" else if(b.state=="SEND_FAILED") "将重发此前生成的摘要，不重新筛选。新的过滤规则不会改动已分析的内容。" else "将按当前过滤规则重新分析本批。其他新通知仍在下一批。")
                        .setNegativeButton("取消",null).setPositiveButton("确认重试") { _,_ -> BatchEngine.retry(this,b.id); show(1) }.show()
                }
            }
            label("近期采集 · 已隐藏排除项 · 最多120条",17f,true)
            db.recent().forEach { n -> noticeCard(content,n,false) }
        }
    }
    private fun noticeCard(parent: LinearLayout,n: Notice,audit: Boolean) {
        val c=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(12))
            background=GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=dp(16).toFloat() }
        }
        parent.addView(c,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12) })
        label("${n.app} · ${n.title}",15f,true,c)
        label("${time(n.at)} · ${kindName(n.kind)}${if(n.summary) " · 汇总" else ""}",12f,parent=c)
        label(n.body.ifBlank { "（正文不可见）" },14f,parent=c).setTextIsSelectable(true)
        if(audit && Store(this).use { it.isFiltered(n) }) label("已匹配排除规则 · 此处保留原文供核对",12f,parent=c)
        button("不再收集…",c) { filterNotice(n) }
    }
    private fun showNotices(notices: List<Notice>,audit: Boolean=false) {
        val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(16)) }
        label(if(audit) "已分析及完成批次保留原文；新的过滤规则不会撤回已提交的内容。" else "已排除的内容在未开始分析的批次中隐藏，不会再发送到AI。",12f,parent=list)
        notices.forEach { noticeCard(list,it,audit) }
        noticeDialog=AlertDialog.Builder(this).setTitle("本批完整记录").setView(ScrollView(this).apply { addView(list) }).setPositiveButton("关闭",null).show()
    }
    private fun filterNotice(n: Notice) {
        val choices=mutableListOf("仅${n.app}的相同正文（标题可变）","整个${n.app}应用")
        if(n.channelId.isNotBlank()) choices.add("${n.app}的同一通知类别（${n.channelId}）")
        AlertDialog.Builder(this).setTitle("不再收集哪一类？").setItems(choices.toTypedArray()) { _,which ->
            val kind=when(which) { 0 -> "body"; 1 -> "app"; else -> "channel" }
            val value=when(kind) { "body" -> n.body; "channel" -> n.channelId; else -> "" }
            val scope=when(kind) {
                "body" -> "仅匹配${n.app}的完全相同正文，标题变化仍匹配。\n\n${n.body.ifBlank { "（空正文：将匹配此应用所有空正文通知）" }}"
                "channel" -> "匹配${n.app}在类别“${n.channelId}”下的所有通知，不影响其他类别。"
                else -> "停止收集${n.app}的所有通知。"
            }
            AlertDialog.Builder(this).setTitle("保存过滤规则？").setMessage(scope+"\n\n新通知和未分析记录立即生效。已开始分析或生成的提醒仍可能发送；可随时到“通知过滤”取消。")
                .setNegativeButton("取消",null).setPositiveButton("保存规则") { _,_ ->
                    Store(this).use { it.addFilter(kind,n.pkg,n.app,value) }; noticeDialog?.dismiss(); noticeDialog=null; toast("规则已保存"); show(1)
                }.show()
        }.show()
    }
    private fun field(title: String,value: String,secret: Boolean=false,multiline: Boolean=false,parent: LinearLayout=content): EditText {
        label(title,14f,true,parent)
        return EditText(this).also { it.setText(value); it.textSize=14f; it.inputType=if(secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else if(multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_CLASS_TEXT; it.setSingleLine(!multiline); parent.addView(it,LinearLayout.LayoutParams(-1,-2)) }
    }
    private fun preferences() {
        label("设置",23f,true)
        label("选择AI服务，休息时自动筛选通知并将摘要发到飞书。",14f)
        var selected=settings.provider
        val drafts=mutableMapOf<Provider,AiConfig>()
        var keyError=false
        fun secret(read: ()->String): String = try { read() } catch(_: Exception) { keyError=true; "" }
        fun load(p: Provider): AiConfig = try { settings.config(p) } catch(_: Exception) {
            keyError=true; AiConfig(p,"",settings.providerEndpoint(p),settings.providerModel(p),settings.prompt)
        }
        val initial=load(selected); drafts[selected]=initial
        label("AI提供商",14f,true)
        val providers=Provider.entries.toList()
        val picker=Spinner(this).apply {
            contentDescription="AI提供商"
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,providers.map { it.title })
            setSelection(providers.indexOf(selected),false)
        }; content.addView(picker,LinearLayout.LayoutParams(-1,dp(52)))
        val providerHint=label("当前使用${settings.provider.title}；保存基础设置后才切换提供商。",12f)
        val enabled=Switch(this).apply { text="启用云端分析与飞书提醒"; isChecked=settings.enabled }; content.addView(enabled)
        enabled.setOnCheckedChangeListener { _,on ->
            if(on && !settings.enabled) AlertDialog.Builder(this).setTitle("确认发送通知正文到云端")
                .setMessage("通知可能包含联系人、聊天内容、验证码或事务信息。分析直接发到所选AI的HTTPS接口，摘要发到你的飞书机器人。确认后还需保存设置。")
                .setPositiveButton("我知晓并启用") { _,_ -> enabled.tag=true }
                .setNegativeButton("取消") { _,_ -> enabled.isChecked=false }.setOnCancelListener { enabled.isChecked=false }.show()
        }
        val apiLabel=label("${selected.title} API Key",14f,true)
        val api=EditText(this).apply { setText(initial.key); textSize=14f; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setSingleLine(true) }
        content.addView(api,LinearLayout.LayoutParams(-1,-2))
        val hook=field("飞书机器人地址（Webhook）",secret { settings.webhook },true)
        label("从飞书群聊 → 设置 → 群机器人 → 添加自定义机器人中复制 Webhook 地址。",12f)
        val keyNotice=label(if(keyError) "本机密钥不可解密，请重新填写并保存。" else "各提供商的密钥、模型和接口地址分别保存。",12f)
        button("保存设置",primary=true) {
            when {
                enabled.isChecked && (api.text.isBlank() || !Network.validUrl(hook.text.toString().trim(),true)) -> toast("启用前请填写API Key及有效飞书Webhook")
                enabled.isChecked && !settings.enabled && enabled.tag!=true -> toast("请先确认云端发送说明")
                else -> try {
                    settings.saveBasic(selected,api.text.toString().trim(),hook.text.toString().trim(),enabled.isChecked)
                    providerHint.text="当前使用${settings.provider.title}；保存基础设置后才切换提供商。"
                    toast("${selected.title}基础设置已保存，高级配置保持不变")
                } catch(_: Exception) { settings.enabled=false; toast("密钥保存失败，云端分析已关闭") }
            }
        }
        val advanced=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=View.GONE }
        val toggle=button("高级设置 · 展开") { advanced.visibility=if(advanced.visibility==View.GONE) View.VISIBLE else View.GONE }
        toggle.setOnClickListener { advanced.visibility=if(advanced.visibility==View.GONE) View.VISIBLE else View.GONE; toggle.text=if(advanced.visibility==View.VISIBLE) "高级设置 · 收起" else "高级设置 · 展开" }
        content.addView(advanced)
        label("保存当前所选AI的模型和地址；不会切换正在使用的提供商。飞书签名和筛选偏好由三家共用。",12f,parent=advanced)
        val endpoint=field("AI接口完整地址（HTTPS）",initial.endpoint,parent=advanced)
        val model=field("模型名称",initial.model,parent=advanced)
        val sign=field("签名密钥（仅机器人开启签名验证时填写）",secret { settings.signingKey },true,parent=advanced)
        label("未开启机器人签名验证时留空；这不是AI的API Key。",12f,parent=advanced)
        val prompt=field("补充筛选偏好（私聊仍会保留）",settings.prompt,multiline=true,parent=advanced)
        button("保存高级设置",advanced) {
            when {
                !Network.validUrl(endpoint.text.toString().trim()) -> toast("AI接口须为有效HTTPS完整地址")
                model.text.isBlank() -> toast("请填写模型名称")
                else -> try {
                    settings.saveAdvanced(selected,endpoint.text.toString().trim(),model.text.toString().trim(),sign.text.toString().trim(),prompt.text.toString())
                    toast("${selected.title}高级设置已保存；当前仍使用${settings.provider.title}")
                } catch(_: Exception) { toast("高级设置保存失败，请检查密钥") }
            }
        }
        // Install after all fields exist. Repeated asynchronous initial selection is a no-op.
        picker.onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) {
                if(position!=picker.selectedItemPosition) return
                val next=providers[position]
                if(next==selected) return
                drafts[selected]=AiConfig(selected,api.text.toString(),endpoint.text.toString(),model.text.toString(),prompt.text.toString())
                selected=next
                val draft=drafts.getOrPut(next) { load(next) }
                apiLabel.text="${next.title} API Key"; api.setText(draft.key); endpoint.setText(draft.endpoint); model.setText(draft.model)
                providerHint.text="表单：${next.title} · 当前使用${settings.provider.title}；点保存设置才切换。"
                if(keyError) keyNotice.text="某家本机密钥不可解密，请重新填写后保存。"
            }
        }
        val management=card(); label("运行与消息管理",17f,true,management)
        button("后台运行与保活",management) { show(4) }
        button("通知过滤",management) { show(3) }
        label("记录策略：待处理、失败和送达不确定批次一直保留，已完成/无需提醒保留30天；过滤隐藏近期与未处理内容，不删除已完成批次原文。",12f)
        button("删除本机通知记录") {
            AlertDialog.Builder(this).setTitle("永久删除本机通知和批次？").setMessage("此操作不可撤销。设置、过滤规则和密钥会保留。正在分析或发送时禁止删除。")
                .setNegativeButton("取消",null).setPositiveButton("删除") { _,_ -> toast(if(Store(this).use { it.clear() }) "已清除" else "当前正在分析或发送，请稍后再试") }.show()
        }
        button("删除保存的密钥并关闭云端分析") { AlertDialog.Builder(this).setTitle("删除密钥？").setMessage("将清除三家AI的全部API Key、Webhook与签名密钥。已发出的请求无法撤回。")
            .setNegativeButton("取消",null).setPositiveButton("删除") { _,_ -> settings.clearSecrets(); show(2) }.show() }
    }
    private fun filtersPage() {
        label("通知过滤",23f,true)
        label("在通知卡片中点“不再收集…”，可屏蔽相同正文、某类通知或整个应用。",13f)
        label("规则会隐藏未分析的记录；取消后恢复收集。已开始分析或生成提醒的批次仍可能发送，历史原文保留。",12f)
        button("返回设置") { show(2) }
        button("选择要排除的应用") {
            val apps=Store(this).use { it.apps() }
            if(apps.isEmpty()) toast("还没有采集到应用，请先授权通知读取")
            else AlertDialog.Builder(this).setTitle("选择应用（整应用排除）").setItems(apps.map { it.second }.toTypedArray()) { _,i ->
                AlertDialog.Builder(this).setTitle("停止收集${apps[i].second}的所有通知？").setMessage("该应用全部类型都会排除；如只想屏蔽某种系统提示，请回到通知卡片选择相同正文规则。")
                    .setNegativeButton("取消",null).setPositiveButton("排除") { _,_ -> Store(this).use { it.addFilter("app",apps[i].first,apps[i].second) }; show(3) }.show()
            }.show()
        }
        val rules=Store(this).use { it.filters() }
        if(rules.isEmpty()) label("尚无排除规则。可从通知卡片的“不再收集…”精准屏蔽一类消息。",14f)
        rules.forEach { rule ->
            val c=card(); label(rule.app,17f,true,c)
            label(when(rule.kind) { "app" -> "整个应用"; "body" -> "相同正文 · 标题可变"; else -> "同一通知类别" },14f,true,c)
            if(rule.kind!="app") label(rule.value.ifBlank { "（空正文）" },13f,parent=c)
            button("取消这条排除规则",c) { Store(this).use { it.removeFilter(rule.id) }; toast("已取消，后续通知恢复采集"); show(3) }
        }
    }
    private fun backgroundPage() {
        label("后台运行与保活",23f,true)
        label("以下状态来自系统；自启动和最近任务锁定需要手动确认，本应用不会代替系统开启它们。",13f)
        val pm=getSystemService(PowerManager::class.java)
        val am=getSystemService(ActivityManager::class.java)
        val component=ComponentName(this,InboxListener::class.java)
        val authorized=if(Build.VERSION.SDK_INT>=27) getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
            else AndroidSettings.Secure.getString(contentResolver,"enabled_notification_listeners").orEmpty().split(":").contains(component.flattenToString())
        val c=card(); label("当前状态",17f,true,c)
        label("通知读取：${if(authorized) "已授权" else "未授权"} · ${if(InboxListener.connected) "监听已连接" else "监听未连接"}\n准时闹钟：${if(TimerEngine.exact(this)) "可用" else "未授权，熄屏触发可能延迟"}\n省电优化白名单：${if(pm.isIgnoringBatteryOptimizations(packageName)) "已加入省电优化白名单" else "未加入（仍受省电优化）"}\n系统后台限制：${if(Build.VERSION.SDK_INT>=28) if(am.isBackgroundRestricted) "正在限制" else "系统未报告限制" else "此系统版本不提供此状态"}\n荣耀应用启动管理：需手动确认\n最近任务锁定：需手动确认",14f,parent=c)
        button("刷新状态",c) { show(4) }
        button("通知读取权限设置",c) { openSystem(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        if(Build.VERSION.SDK_INT>=31) button("准时闹钟权限设置",c) { openSystem(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName"))) }
        button("系统电池优化列表",c) { openSystem(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        button("打开息间应用详情",c) { openSystem(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))) }
        val honor=card(); label("荣耀手机 · 手动检查",17f,true,honor)
        label("1. 设置 → 应用 → 应用启动管理 → 找到息间。\n2. 关闭“自动管理”，开启“允许自启动”“允许关联启动”“允许后台活动”。\n3. 进入最近任务，在息间卡片下拉或使用卡片菜单的锁定功能（入口随系统版本变化）。\n\n这些设置需要你在手机上确认，本页不能读取其是否已开启。",14f,parent=honor)
        button("打开系统设置",honor) { openSystem(Intent(AndroidSettings.ACTION_SETTINGS)) }
        val recents=card(); label("最近任务卡片显示",17f,true,recents)
        label("隐藏只影响息间的任务卡片，不会提高保活。隐藏后从桌面图标打开。若要在最近任务里锁卡，请保持卡片可见；隐藏与锁卡是不同功能。",13f,parent=recents)
        val hidden=Switch(this).apply { text="在最近任务中隐藏息间"; isChecked=settings.hideRecents }; recents.addView(hidden)
        hidden.setOnCheckedChangeListener { _,on ->
            fun apply(value: Boolean) { settings.hideRecents=value; am.appTasks.forEach { it.setExcludeFromRecents(value) } }
            if(on) AlertDialog.Builder(this).setTitle("隐藏最近任务卡片？").setMessage("仅隐藏卡片，不提高保活；之后请从桌面图标打开息间。锁卡前需先关闭此项。")
                .setNegativeButton("取消") { _,_ -> hidden.isChecked=false }.setPositiveButton("隐藏") { _,_ -> apply(true) }.setOnCancelListener { hidden.isChecked=false }.show()
            else apply(false)
        }
        button("返回设置") { show(2) }
    }
    private fun openSystem(intent: Intent) {
        try { startActivity(intent) }
        catch(_: ActivityNotFoundException) { openAppDetails() }
        catch(_: SecurityException) { openAppDetails() }
    }
    private fun openAppDetails() {
        try { startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))) }
        catch(_: ActivityNotFoundException) { toast("此手机未提供该入口，请从系统设置中打开息间应用详情") }
        catch(_: SecurityException) { toast("此手机限制了跳转，请手动打开系统设置") }
    }
    @Suppress("DEPRECATION")
    override fun onBackPressed() { if(page>=3) show(2) else super.onBackPressed() }
    private fun toast(message: String)=Toast.makeText(this,message,Toast.LENGTH_LONG).show()
}
