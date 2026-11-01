package com.focusinbox.app

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.app.AlertDialog
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import android.content.Intent
import android.content.Context
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
    private var resumed=false
    private lateinit var pageHost: FrameLayout
    private lateinit var pageTransition: PageTransition
    private val navigationButtons=mutableListOf<Button>()
    private var clock: TextView?=null
    private var status: TextView?=null
    private var pending: TextView?=null
    private var startButton: Button?=null
    private var pauseButton: Button?=null
    private var restButton: Button?=null
    private var noticeDialog: AlertDialog?=null
    private var localStatusView: TextView?=null
    private var savedForm: SettingsDraft?=null
    private var captureForm: (() -> SettingsDraft)?=null
    private fun tr(id: Int,vararg args: Any): String = getString(id,*args)
    override fun attachBaseContext(base: Context) { super.attachBaseContext(LocaleHelper.wrap(base)) }
    override fun onRetainNonConfigurationInstance(): Any = RetainedScreen(page,if(page==2) captureForm?.invoke() ?: savedForm else savedForm)
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
        val retained=lastNonConfigurationInstance as? RetainedScreen
        savedForm=retained?.draft
        createChrome()
        show(retained?.page ?: 0)
        handleNotificationIntent(intent)
    }
    override fun onResume() {
        super.onResume()
        resumed=true
        getSystemService(ActivityManager::class.java).appTasks.forEach { it.setExcludeFromRecents(settings.hideRecents) }
        if(page==4 || page==5) show(page)
        if(page==2) refreshLocalStatus()
        handler.post(tick)
    }
    override fun onPause() { resumed=false; handler.removeCallbacks(tick); pageTransition.finish(); super.onPause() }
    override fun onDestroy() { handler.removeCallbacks(tick); pageTransition.clear(); noticeDialog?.dismiss(); captureForm=null; super.onDestroy() }
    private fun label(text: String,size: Float=15f,bold: Boolean=false,parent: LinearLayout=content): TextView = TextView(this).also {
        it.text=text; it.textSize=size; it.setTextColor(ink); it.setPadding(0,dp(6),0,dp(6)); if(bold) it.setTypeface(null,Typeface.BOLD); parent.addView(it)
    }
    private fun button(text: String,parent: LinearLayout=content,primary: Boolean=false,action: ()->Unit): Button = Button(this).also {
        it.text=text; it.isAllCaps=false; it.textSize=15f; it.setTextColor(if(primary) Color.WHITE else green)
        it.stateListAnimator=null; it.elevation=0f; it.minimumHeight=dp(54); it.setPadding(dp(12),dp(10),dp(12),dp(10))
        val shape=GradientDrawable().apply { setColor(if(primary) green else Color.rgb(232,241,236)); cornerRadius=dp(12).toFloat() }
        it.background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(40,35,121,111)),shape,null)
        parent.addView(it,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(5) }); it.setOnClickListener { action() }
    }
    private fun card(): LinearLayout = LinearLayout(this).also {
        it.orientation=LinearLayout.VERTICAL; it.setPadding(dp(18),dp(14),dp(18),dp(14))
        it.background=GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=dp(20).toFloat() }
        content.addView(it,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(14); bottomMargin=dp(6) })
    }
    private fun createChrome() {
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(245,247,243)) }
        val top=LinearLayout(this).apply { id=R.id.page_header; setPadding(dp(20),dp(16),dp(20),0); orientation=LinearLayout.VERTICAL }
        label(tr(R.string.app_name),28f,true,top); label(tr(R.string.tagline),13f,parent=top); root.addView(top)
        pageHost=FrameLayout(this).apply { id=R.id.page_host }
        root.addView(pageHost,LinearLayout.LayoutParams(-1,0,1f))
        pageTransition=PageTransition(pageHost)
        val nav=LinearLayout(this).apply { id=R.id.page_navigation; gravity=Gravity.CENTER; setPadding(dp(12),0,dp(12),dp(6)) }
        listOf(tr(R.string.nav_focus),tr(R.string.nav_notices),tr(R.string.nav_settings)).forEachIndexed { index,name ->
            val button=Button(this).apply {
                text=name; setTextColor(green); textSize=14f; isAllCaps=false; minimumHeight=dp(54); setPadding(dp(4),dp(10),dp(4),dp(10))
                stateListAnimator=null; elevation=0f
                background=GradientDrawable().apply { cornerRadius=dp(12).toFloat() }
                setOnClickListener { show(index) }
            }
            navigationButtons.add(button); nav.addView(button,LinearLayout.LayoutParams(0,-2,1f))
        }
        root.addView(nav); setContentView(root)
    }
    private fun show(which: Int) {
        val previous=page
        if(page==2) savedForm=captureForm?.invoke() ?: savedForm
        captureForm=null; localStatusView=null
        page=which; clock=null; status=null; pending=null; startButton=null; pauseButton=null; restButton=null
        val scroll=ScrollView(this)
        content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(4),dp(20),dp(24)) }
        scroll.addView(content)
        navigationButtons.forEachIndexed { index,button ->
            val selected=index==which || index==2 && which>=2
            button.isSelected=selected
            (button.background as GradientDrawable).setColor(if(selected) Color.rgb(223,237,226) else Color.TRANSPARENT)
        }
        when(which) { 0 -> home(); 1 -> history(); 2 -> preferences(); 3 -> filtersPage(); 4 -> backgroundPage(); 5 -> localNotificationPage() }
        val direction=when {
            previous>=3 && which==2 -> PageTransition.Direction.BACK
            which>=3 && previous!=which -> PageTransition.Direction.FORWARD
            else -> PageTransition.Direction.NONE
        }
        pageTransition.replace(scroll,resumed && previous!=which,direction)
    }
    private fun home() {
        val c=card(); label(tr(R.string.focus_title),17f,true,c)
        clock=label("25:00",58f,true,c).apply { gravity=Gravity.CENTER; setAutoSizeTextTypeUniformWithConfiguration(24,58,2,android.util.TypedValue.COMPLEX_UNIT_SP); contentDescription=tr(R.string.timer_adjust_description); setOnClickListener { editDuration() } }
        button(tr(R.string.timer_adjust),c) { editDuration() }
        status=label("",14f,parent=c)
        startButton=button(tr(R.string.timer_start),c,primary=true) { TimerEngine.start(this); show(0) }
        pauseButton=button(tr(R.string.timer_pause),c) { TimerEngine.pause(this); show(0) }
        restButton=button(tr(R.string.timer_rest),c) { TimerEngine.rest(this); show(0); toast(tr(R.string.batch_started)) }
        button(tr(R.string.analyze_now),c) { BatchEngine.trigger(this); show(0); toast(tr(R.string.analysis_started)) }
        val info=card(); pending=label("",14f,parent=info)
        label(tr(R.string.collection_help),13f,parent=info)
        button(tr(R.string.background_title),info) { show(4) }
        button(tr(R.string.filters_title),info) { show(3) }
        button(tr(R.string.sound_settings),info) { openSystem(Intent(AndroidSettings.ACTION_SOUND_SETTINGS)) }
        label(tr(R.string.notifications_help),12f)
        update()
    }
    private fun editDuration() {
        val t=Store(this).use { it.timer() }
        if(t.state in setOf("RUNNING","PAUSED")) {
            AlertDialog.Builder(this).setTitle(tr(R.string.duration_title))
                .setItems(arrayOf(tr(R.string.duration_next_only),tr(R.string.duration_current,tr(if(t.state=="RUNNING") R.string.running_word else R.string.paused_word)))) { _,which -> durationPicker(which==1) }.show()
        } else durationPicker(false)
    }
    private fun durationPicker(current: Boolean) {
        val options=(listOf(15,25,35,45,60).map { tr(R.string.minutes_value,it) }+tr(R.string.duration_custom)).toTypedArray()
        AlertDialog.Builder(this).setTitle(if(current) tr(R.string.duration_remaining_title) else tr(R.string.duration_default_title))
            .setItems(options) { _,which ->
                if(which<5) applyDuration(listOf(15,25,35,45,60)[which],current)
                else {
                    val input=EditText(this).apply { inputType=InputType.TYPE_CLASS_NUMBER; hint=tr(R.string.duration_hint); setText(settings.minutes.toString()); setSelectAllOnFocus(true) }
                    val dialog=AlertDialog.Builder(this).setTitle(if(current) tr(R.string.duration_remaining_input) else tr(R.string.duration_default_input)).setView(input)
                        .setNegativeButton(tr(R.string.cancel),null).setPositiveButton(tr(R.string.confirm),null).create()
                    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val value=input.text.toString().toIntOrNull()
                        if(value==null || value !in 1..180) input.error=tr(R.string.duration_invalid)
                        else { applyDuration(value,current); dialog.dismiss() }
                    } }; dialog.show()
                }
            }.show()
    }
    private fun applyDuration(minutes: Int,current: Boolean) {
        if(current) toast(if(TimerEngine.adjustRemaining(this,minutes)) tr(R.string.duration_changed) else tr(R.string.duration_finished))
        else { settings.minutes=minutes; toast(tr(R.string.duration_default_saved,minutes)) }
        show(0)
    }
    private fun update() {
        Store(this).use { db ->
            var t=db.timer()
            if(t.state=="RUNNING" && t.end<=System.currentTimeMillis()) { TimerEngine.finish(this,t.token); t=db.timer() }
            val millis=when(t.state) { "RUNNING" -> (t.end-System.currentTimeMillis()).coerceAtLeast(0); "PAUSED" -> t.remaining; else -> settings.minutes*60_000L }
            startButton?.apply { visibility=if(t.state=="RUNNING") View.GONE else View.VISIBLE; text=if(t.state=="PAUSED") tr(R.string.timer_resume) else tr(R.string.timer_start) }
            pauseButton?.visibility=if(t.state=="RUNNING") View.VISIBLE else View.GONE
            restButton?.visibility=if(t.state=="IDLE") View.GONE else View.VISIBLE
            val sec=(millis+999)/1000; clock?.text="%02d:%02d".format(sec/60,sec%60)
            status?.text=when(t.state) { "RUNNING" -> tr(R.string.focus_running); "PAUSED" -> tr(R.string.focus_paused); else -> tr(R.string.focus_idle,settings.minutes) }
            val last=db.batches().firstOrNull()
            pending?.text=listOf(
                tr(R.string.listener_status,tr(if(InboxListener.connected) R.string.listener_connected else R.string.listener_disconnected)),
                tr(R.string.pending_status,db.pendingCount(),tr(if(settings.enabled) R.string.enabled else R.string.disabled)),
                tr(if(TimerEngine.exact(this)) R.string.exact_available else R.string.exact_delayed),
                last?.let { tr(R.string.last_batch,it.id,stateName(it.state))+(if(it.error.isNotEmpty()) "\n"+LocaleHelper.renderError(this,it.error) else "") } ?: tr(R.string.no_batches)
            ).joinToString("\n")
        }
    }
    private fun stateName(s: String)=mapOf("QUEUED" to tr(R.string.state_queued),"ANALYZING" to tr(R.string.state_analyzing),"READY" to tr(R.string.state_ready),"SENDING" to tr(R.string.state_sending),"DONE" to tr(R.string.state_done),"EMPTY" to tr(R.string.state_empty),"SEND_FAILED" to tr(R.string.state_send_failed),"FAILED" to tr(R.string.state_failed),"UNCERTAIN" to tr(R.string.state_uncertain))[s] ?: s
    private fun kindName(kind: String)=mapOf("private" to tr(R.string.kind_private),"group" to tr(R.string.kind_group),"message" to tr(R.string.kind_message),"mail" to tr(R.string.kind_mail),"sms" to tr(R.string.kind_sms),"call" to tr(R.string.kind_call),"missed_call" to tr(R.string.kind_missed_call))[kind] ?: tr(R.string.kind_unknown)
    private fun time(t: Long)=SimpleDateFormat(tr(R.string.date_pattern),Locale.forLanguageTag(settings.language.tag)).format(Date(t))
    private fun history() {
        label(tr(R.string.history_title),23f,true)
        button(tr(R.string.filters_manage)) { show(3) }
        button(tr(R.string.refresh)) { show(1) }
        Store(this).use { db ->
            val batches=db.batches()
            label(tr(R.string.recent_batches),17f,true)
            if(batches.isEmpty()) label(tr(R.string.history_empty),13f)
            batches.forEach { b ->
                val c=card(); label("#${b.id} · ${stateName(b.state)}",16f,true,c); label(time(b.created),12f,parent=c); label(tr(R.string.batch_destination,targetName(b.target)),12f,parent=c)
                if(b.message.isNotBlank()) label(b.message,14f,parent=c)
                if(b.error.isNotBlank()) label(LocaleHelper.renderError(this,b.error),14f,parent=c)
                val unprocessed=b.state in setOf("QUEUED","FAILED")
                val records=if(unprocessed) db.visibleNotices(b.id) else db.notices(b.id)
                button(tr(R.string.batch_records,records.size),c) { showNotices(Store(this).use { if(unprocessed) it.visibleNotices(b.id) else it.notices(b.id) },!unprocessed) }
                if(b.state in setOf("FAILED","SEND_FAILED","UNCERTAIN")) button(tr(R.string.retry_manual),c) {
                    val requested=settings.destination
                    val changing=requested!=b.target
                    val reason=if(b.state=="UNCERTAIN") tr(R.string.retry_uncertain_target,targetName(b.target)) else if(b.state=="SEND_FAILED") tr(R.string.retry_send) else tr(R.string.retry_analysis)
                    val route=if(changing) tr(R.string.retry_change_target,targetName(requested),targetName(b.target)) else tr(R.string.retry_same_target,targetName(b.target))
                    AlertDialog.Builder(this).setTitle(tr(R.string.retry_title)).setMessage(reason+"\n\n"+route)
                        .setNegativeButton(tr(R.string.cancel),null).setPositiveButton(tr(R.string.retry_confirm)) { _,_ -> BatchEngine.retry(this,b.id,if(changing) requested else null); show(1) }.show()
                }
            }
            label(tr(R.string.recent_notices),17f,true)
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
        label("${time(n.at)} · ${kindName(n.kind)}${if(n.summary) tr(R.string.summary_suffix) else ""}",12f,parent=c)
        label(n.body.ifBlank { tr(R.string.body_hidden) },14f,parent=c).setTextIsSelectable(true)
        if(audit && Store(this).use { it.isFiltered(n) }) label(tr(R.string.audit_filtered),12f,parent=c)
        button(tr(R.string.filter_notice),c) { filterNotice(n) }
    }
    private fun showNotices(notices: List<Notice>,audit: Boolean=false,batch: Batch?=null) {
        val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(16)) }
        if(batch!=null) {
            label(tr(R.string.batch_detail_title,batch.id),18f,true,list)
            label(tr(R.string.batch_destination,targetName(batch.target))+" · "+stateName(batch.state),13f,parent=list)
            if(batch.message.isNotBlank()) label(batch.message,15f,parent=list)
            if(batch.error.isNotBlank()) label(LocaleHelper.renderError(this,batch.error),13f,parent=list)
        }
        label(if(audit) tr(R.string.audit_help) else tr(R.string.unprocessed_help),12f,parent=list)
        notices.forEach { noticeCard(list,it,audit) }
        noticeDialog=AlertDialog.Builder(this).setTitle(tr(R.string.batch_complete_records)).setView(ScrollView(this).apply { addView(list) }).setPositiveButton(tr(R.string.close),null).show()
    }
    private fun filterNotice(n: Notice) {
        val choices=mutableListOf(tr(R.string.filter_body_option,n.app),tr(R.string.filter_app_option,n.app))
        if(n.channelId.isNotBlank()) choices.add(tr(R.string.filter_channel_option,n.app,n.channelId))
        AlertDialog.Builder(this).setTitle(tr(R.string.filter_scope_title)).setItems(choices.toTypedArray()) { _,which ->
            val kind=when(which) { 0 -> "body"; 1 -> "app"; else -> "channel" }
            val value=when(kind) { "body" -> n.body; "channel" -> n.channelId; else -> "" }
            val scope=when(kind) {
                "body" -> tr(R.string.filter_body_scope,n.app,n.body.ifBlank { tr(R.string.filter_empty_scope) })
                "channel" -> tr(R.string.filter_channel_scope,n.app,n.channelId)
                else -> tr(R.string.filter_app_scope,n.app)
            }
            AlertDialog.Builder(this).setTitle(tr(R.string.filter_save_title)).setMessage(scope+"\n\n"+tr(R.string.filter_effect_help))
                .setNegativeButton(tr(R.string.cancel),null).setPositiveButton(tr(R.string.filter_save)) { _,_ ->
                    Store(this).use { it.addFilter(kind,n.pkg,n.app,value) }; noticeDialog?.dismiss(); noticeDialog=null; toast(tr(R.string.filter_saved)); show(1)
                }.show()
        }.show()
    }
    private fun field(title: String,value: String,secret: Boolean=false,multiline: Boolean=false,parent: LinearLayout=content): EditText {
        label(title,14f,true,parent)
        return EditText(this).also { it.setText(value); it.textSize=14f; it.inputType=if(secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else if(multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_CLASS_TEXT; it.setSingleLine(!multiline); if(secret) it.maskSecret(); parent.addView(it,LinearLayout.LayoutParams(-1,-2)) }
    }
    private fun preferences() {
        val languages=UiLanguage.entries.toList()
        label(tr(R.string.language_title),14f,true)
        val languagePicker=Spinner(this).apply {
            contentDescription=tr(R.string.language_title)
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf(tr(R.string.language_chinese),tr(R.string.language_english)))
            setSelection(languages.indexOf(settings.language),false)
        }; content.addView(languagePicker,LinearLayout.LayoutParams(-1,-2).apply { height=dp(52) })
        val retainedForm=savedForm
        label(tr(R.string.nav_settings),23f,true)
        label(tr(R.string.settings_intro),14f)
        var selected=retainedForm?.selected ?: settings.provider
        var selectedTarget=retainedForm?.destination ?: settings.destination
        var remoteLoaded=retainedForm?.remoteLoaded ?: (selectedTarget==DeliveryTarget.FEISHU)
        val drafts=retainedForm?.providers?.toMutableMap() ?: mutableMapOf()
        var keyError=false
        fun secret(read: ()->String): String = try { read() } catch(_: Exception) { keyError=true; "" }
        fun load(p: Provider): AiConfig = try { settings.config(p) } catch(_: Exception) {
            keyError=true; AiConfig(p,"",settings.providerEndpoint(p),settings.providerModel(p),settings.prompt)
        }
        val initial=drafts.getOrPut(selected) { load(selected) }
        label(tr(R.string.provider_label),14f,true)
        val providers=Provider.entries.toList()
        val picker=Spinner(this).apply {
            contentDescription=tr(R.string.provider_label)
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,providers.map { it.title })
            setSelection(providers.indexOf(selected),false)
        }; content.addView(picker,LinearLayout.LayoutParams(-1,dp(52)))
        val providerHint=label(tr(R.string.provider_active_hint,settings.provider.title),12f)
        label(tr(R.string.delivery_label),14f,true)
        val targets=DeliveryTarget.entries.toList()
        val targetPicker=Spinner(this).apply {
            contentDescription=tr(R.string.delivery_label)
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,targets.map { targetName(it) })
            setSelection(targets.indexOf(selectedTarget),false)
        }; content.addView(targetPicker,LinearLayout.LayoutParams(-1,dp(52)))
        val targetHint=label(tr(R.string.delivery_saved_hint,targetName(settings.destination)),12f)
        val enabled=Switch(this).apply { text=tr(R.string.cloud_enable); isChecked=retainedForm?.enabled ?: settings.enabled; tag=retainedForm?.consent==true }; content.addView(enabled)
        enabled.setOnCheckedChangeListener { _,on ->
            if(on && !settings.enabled) AlertDialog.Builder(this).setTitle(tr(R.string.cloud_consent_title))
                .setMessage(tr(R.string.cloud_consent_text))
                .setPositiveButton(tr(R.string.cloud_consent_yes)) { _,_ -> enabled.tag=true }
                .setNegativeButton(tr(R.string.cancel)) { _,_ -> enabled.isChecked=false }.setOnCancelListener { enabled.isChecked=false }.show()
        }
        val apiLabel=label(tr(R.string.api_key_label,selected.title),14f,true)
        val api=EditText(this).apply { setText(initial.key); textSize=14f; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setSingleLine(true); maskSecret() }
        content.addView(api,LinearLayout.LayoutParams(-1,-2))
        val remoteArea=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=if(selectedTarget==DeliveryTarget.FEISHU) View.VISIBLE else View.GONE }
        content.addView(remoteArea)
        val hook=field(tr(R.string.webhook_label),if(remoteLoaded) retainedForm?.webhook ?: secret { settings.webhook } else "",true,parent=remoteArea)
        label(tr(R.string.webhook_help),12f,parent=remoteArea)
        val localArea=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=if(selectedTarget==DeliveryTarget.LOCAL) View.VISIBLE else View.GONE }
        content.addView(localArea)
        localStatusView=label("",13f,parent=localArea)
        button(tr(R.string.local_settings_title),localArea) { show(5) }
        refreshLocalStatus()
        val keyNotice=label(if(keyError) tr(R.string.key_unreadable) else tr(R.string.provider_storage_help),12f)
        button(tr(R.string.save_settings),primary=true) {
            when {
                enabled.isChecked && api.text.isBlank() -> toast(tr(R.string.configure_ai_required))
                enabled.isChecked && selectedTarget==DeliveryTarget.FEISHU && !Network.validUrl(hook.text.toString().trim(),true) -> toast(tr(R.string.configure_required))
                enabled.isChecked && !settings.enabled && enabled.tag!=true -> toast(tr(R.string.consent_required))
                else -> try {
                    settings.saveBasic(selected,api.text.toString().trim(),hook.text.toString().trim(),enabled.isChecked,selectedTarget)
                    providerHint.text=tr(R.string.provider_active_hint,settings.provider.title)
                    targetHint.text=tr(R.string.delivery_saved_hint,targetName(settings.destination))
                    toast(tr(R.string.basic_saved,selected.title))
                } catch(_: Exception) { settings.enabled=false; toast(tr(R.string.key_save_failed)) }
            }
        }
        val advanced=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=if(retainedForm?.expanded==true) View.VISIBLE else View.GONE }
        val toggle=button(if(advanced.visibility==View.VISIBLE) tr(R.string.advanced_collapse) else tr(R.string.advanced_expand)) { advanced.visibility=if(advanced.visibility==View.GONE) View.VISIBLE else View.GONE }
        toggle.setOnClickListener { advanced.visibility=if(advanced.visibility==View.GONE) View.VISIBLE else View.GONE; toggle.text=if(advanced.visibility==View.VISIBLE) tr(R.string.advanced_collapse) else tr(R.string.advanced_expand) }
        content.addView(advanced)
        label(tr(R.string.advanced_help),12f,parent=advanced)
        val endpoint=field(tr(R.string.endpoint_label),initial.endpoint,parent=advanced)
        val model=field(tr(R.string.model_label),initial.model,parent=advanced)
        val signingArea=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=if(selectedTarget==DeliveryTarget.FEISHU) View.VISIBLE else View.GONE }
        advanced.addView(signingArea)
        val sign=field(tr(R.string.signing_label),if(remoteLoaded) retainedForm?.signingKey ?: secret { settings.signingKey } else "",true,parent=signingArea)
        label(tr(R.string.signing_help),12f,parent=signingArea)
        val prompt=field(tr(R.string.prompt_label),retainedForm?.prompt ?: settings.prompt,multiline=true,parent=advanced)
        button(tr(R.string.save_advanced),advanced) {
            when {
                !Network.validUrl(endpoint.text.toString().trim()) -> toast(tr(R.string.endpoint_invalid))
                model.text.isBlank() -> toast(tr(R.string.error_model_required))
                else -> try {
                    settings.saveAdvanced(selected,endpoint.text.toString().trim(),model.text.toString().trim(),sign.text.toString().trim(),prompt.text.toString(),selectedTarget)
                    toast(tr(R.string.advanced_saved,selected.title,settings.provider.title))
                } catch(_: Exception) { toast(tr(R.string.advanced_failed)) }
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
                apiLabel.text=tr(R.string.api_key_label,next.title); api.setText(draft.key); endpoint.setText(draft.endpoint); model.setText(draft.model)
                providerHint.text=tr(R.string.provider_form_hint,next.title,settings.provider.title)
                if(keyError) keyNotice.text=tr(R.string.provider_key_unreadable)
            }
        }
        targetPicker.onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) {
                if(position!=targetPicker.selectedItemPosition || targets[position]==selectedTarget) return
                selectedTarget=targets[position]
                if(selectedTarget==DeliveryTarget.FEISHU && !remoteLoaded) {
                    hook.setText(secret { settings.webhook }); sign.setText(secret { settings.signingKey }); remoteLoaded=true
                    if(keyError) keyNotice.text=tr(R.string.key_unreadable)
                }
                remoteArea.visibility=if(selectedTarget==DeliveryTarget.FEISHU) View.VISIBLE else View.GONE
                signingArea.visibility=remoteArea.visibility
                localArea.visibility=if(selectedTarget==DeliveryTarget.LOCAL) View.VISIBLE else View.GONE
                targetHint.text=tr(R.string.delivery_form_hint,targetName(selectedTarget),targetName(settings.destination))
                refreshLocalStatus()
            }
        }
        captureForm={
            val saved=drafts.toMutableMap()
            saved[selected]=AiConfig(selected,api.text.toString(),endpoint.text.toString(),model.text.toString(),prompt.text.toString(),settings.language)
            SettingsDraft(selected,saved,hook.text.toString(),sign.text.toString(),prompt.text.toString(),enabled.isChecked,enabled.tag==true,advanced.visibility==View.VISIBLE,selectedTarget,remoteLoaded)
        }
        languagePicker.onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) {
                if(position!=languagePicker.selectedItemPosition || languages[position]==settings.language) return
                savedForm=captureForm?.invoke()
                LocaleHelper.change(this@MainActivity,languages[position])
            }
        }
        val management=card(); label(tr(R.string.management_title),17f,true,management)
        button(tr(R.string.background_title),management) { show(4) }
        button(tr(R.string.filters_title),management) { show(3) }
        label(tr(R.string.retention_help),12f)
        button(tr(R.string.delete_records)) {
            AlertDialog.Builder(this).setTitle(tr(R.string.delete_records_title)).setMessage(tr(R.string.delete_records_help))
                .setNegativeButton(tr(R.string.cancel),null).setPositiveButton(tr(R.string.delete)) { _,_ -> toast(if(Store(this).use { it.clear() }) tr(R.string.records_deleted) else tr(R.string.records_busy)) }.show()
        }
        button(tr(R.string.delete_keys)) { AlertDialog.Builder(this).setTitle(tr(R.string.delete_keys_title)).setMessage(tr(R.string.delete_keys_help))
            .setNegativeButton(tr(R.string.cancel),null).setPositiveButton(tr(R.string.delete)) { _,_ -> settings.clearSecrets(); captureForm=null; savedForm=null; show(2) }.show() }
        recentTaskPreference()
    }
    private fun filtersPage() {
        label(tr(R.string.filters_title),23f,true)
        label(tr(R.string.filters_intro),13f)
        label(tr(R.string.filters_help),12f)
        button(tr(R.string.back_settings)) { show(2) }
        button(tr(R.string.exclude_choose_app)) {
            val apps=Store(this).use { it.apps() }
            if(apps.isEmpty()) toast(tr(R.string.no_apps))
            else AlertDialog.Builder(this).setTitle(tr(R.string.choose_app_title)).setItems(apps.map { it.second }.toTypedArray()) { _,i ->
                AlertDialog.Builder(this).setTitle(tr(R.string.exclude_app_title,apps[i].second)).setMessage(tr(R.string.exclude_app_help))
                    .setNegativeButton(tr(R.string.cancel),null).setPositiveButton(tr(R.string.exclude)) { _,_ -> Store(this).use { it.addFilter("app",apps[i].first,apps[i].second) }; show(3) }.show()
            }.show()
        }
        val rules=Store(this).use { it.filters() }
        if(rules.isEmpty()) label(tr(R.string.no_filters),14f)
        rules.forEach { rule ->
            val c=card(); label(rule.app,17f,true,c)
            label(when(rule.kind) { "app" -> tr(R.string.filter_kind_app); "body" -> tr(R.string.filter_kind_body); else -> tr(R.string.filter_kind_channel) },14f,true,c)
            if(rule.kind!="app") label(rule.value.ifBlank { tr(R.string.body_empty) },13f,parent=c)
            button(tr(R.string.filter_remove),c) { Store(this).use { it.removeFilter(rule.id) }; toast(tr(R.string.filter_removed)); show(3) }
        }
    }
    private fun backgroundPage() {
        label(tr(R.string.background_title),23f,true)
        label(tr(R.string.background_intro),13f)
        val state=BackgroundGuide.read(this)
        val autostart=card(); label(tr(R.string.autostart_title),17f,true,autostart)
        label(tr(BackgroundGuide.autoLaunchGuide(),tr(R.string.app_name)),14f,parent=autostart)
        label(tr(R.string.autostart_manual),12f,parent=autostart)
        button(tr(R.string.app_details),autostart) { openAppDetails() }
        button(tr(R.string.system_settings),autostart) { openSystem(Intent(AndroidSettings.ACTION_SETTINGS)) }

        val battery=card(); label(tr(R.string.battery_step_title),17f,true,battery)
        label(tr(if(state.batteryExempt) R.string.battery_optimization_off else R.string.battery_optimization_on),15f,true,battery)
        label(tr(R.string.battery_step_help),14f,parent=battery)
        if(BackgroundGuide.isHonorOrHuawei()) label(tr(R.string.battery_honor_help),13f,parent=battery)
        button(tr(if(state.batteryExempt) R.string.battery_settings else R.string.battery_request),battery,primary=!state.batteryExempt) {
            val current=BackgroundGuide.read(this)
            if(!BackgroundGuide.openBatteryScreens(packageName,current.batteryExempt) { startActivity(it) }) toast(tr(R.string.battery_entry_missing))
        }
        if(state.powerSaveMode) {
            label(tr(R.string.power_saver_active),15f,true,battery)
            label(tr(R.string.power_saver_help),13f,parent=battery)
            button(tr(R.string.power_saver_settings),battery) { openSystem(Intent(AndroidSettings.ACTION_BATTERY_SAVER_SETTINGS)) }
        }

        val taskLock=card(); label(tr(R.string.task_lock_title),17f,true,taskLock)
        label(tr(R.string.task_lock_help,tr(R.string.app_name)),14f,parent=taskLock)
        if(settings.hideRecents) label(tr(R.string.task_lock_hidden_help),13f,true,taskLock)

        val component=ComponentName(this,InboxListener::class.java)
        val authorized=if(Build.VERSION.SDK_INT>=27) getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
            else AndroidSettings.Secure.getString(contentResolver,"enabled_notification_listeners").orEmpty().split(":").contains(component.flattenToString())
        val permissions=card(); label(tr(R.string.background_permissions_title),17f,true,permissions)
        label(tr(R.string.background_permissions_states,
            tr(if(authorized) R.string.access_granted else R.string.access_missing),
            tr(if(InboxListener.connected) R.string.connection_active else R.string.connection_missing),
            tr(if(TimerEngine.exact(this)) R.string.alarm_available else R.string.alarm_missing),
            tr(when(state.backgroundRestricted) { true -> R.string.background_restricted; false -> R.string.background_not_reported; null -> R.string.background_unsupported })
        ),14f,parent=permissions)
        button(tr(R.string.notification_access_settings),permissions) { openSystem(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        if(Build.VERSION.SDK_INT>=31) button(tr(R.string.alarm_settings),permissions) { openSystem(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName"))) }
        button(tr(R.string.refresh_status),permissions) { show(4) }
        button(tr(R.string.back_settings)) { show(2) }
    }
    private fun recentTaskPreference() {
        val am=getSystemService(ActivityManager::class.java)
        val recents=card(); label(tr(R.string.recents_title),17f,true,recents)
        label(tr(R.string.recents_help),13f,parent=recents)
        val hidden=Switch(this).apply { text=tr(R.string.recents_hide); isChecked=settings.hideRecents }; recents.addView(hidden)
        hidden.setOnCheckedChangeListener { _,on ->
            fun apply(value: Boolean) { settings.hideRecents=value; am.appTasks.forEach { it.setExcludeFromRecents(value) } }
            if(on) AlertDialog.Builder(this).setTitle(tr(R.string.recents_confirm_title)).setMessage(tr(R.string.recents_confirm_text))
                .setNegativeButton(tr(R.string.cancel)) { _,_ -> hidden.isChecked=false }.setPositiveButton(tr(R.string.hide)) { _,_ -> apply(true) }.setOnCancelListener { hidden.isChecked=false }.show()
            else apply(false)
        }
    }
    private fun targetName(target: DeliveryTarget) = tr(if(target==DeliveryTarget.LOCAL) R.string.delivery_local else R.string.delivery_feishu)
    private fun refreshLocalStatus() {
        localStatusView?.text=tr(when(LocalNotifier.status(this)) {
            LocalNoticeStatus.READY -> R.string.local_status_ready
            LocalNoticeStatus.PERMISSION_MISSING -> R.string.local_status_permission
            LocalNoticeStatus.APP_BLOCKED -> R.string.local_status_app
            LocalNoticeStatus.CHANNEL_BLOCKED -> R.string.local_status_channel
        })
    }
    private fun localNotificationPage() {
        label(tr(R.string.local_settings_title),23f,true)
        localStatusView=label("",14f)
        refreshLocalStatus()
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            button(tr(R.string.local_permission_button),primary=true) { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),701) }
        button(tr(R.string.local_app_settings)) { openSystem(LocalNotifier.appSettings(this)) }
        button(tr(R.string.local_channel_settings)) { openSystem(LocalNotifier.channelSettings(this)) }
        button(tr(R.string.local_test_button)) {
            try { LocalNotifier.sendTest(this); toast(tr(R.string.local_test_sent)) }
            catch(e: AppFailure) { toast(LocaleHelper.renderError(this,e.code.encoded(e.number))) }
            catch(_: Exception) { toast(tr(R.string.error_local_send_failed)) }
        }
        val dnd=card(); label(tr(R.string.local_dnd_title),17f,true,dnd)
        label(tr(R.string.local_dnd_help,tr(R.string.app_name)),14f,parent=dnd)
        button(tr(R.string.local_dnd_settings),dnd) {
            if(!LocalNotifier.openDndSettings { startActivity(it) }) toast(tr(R.string.local_dnd_missing))
        }
        button(tr(R.string.back_settings)) { show(2) }
    }
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==701) { if(page==5) show(5) else refreshLocalStatus() }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleNotificationIntent(intent) }
    private fun handleNotificationIntent(incoming: Intent?) {
        val id=LocalNotifier.batchFromIntent(incoming) ?: return
        setIntent(Intent(this,MainActivity::class.java))
        noticeDialog?.dismiss(); show(1)
        Store(this).use { db ->
            val batch=db.get(id)
            if(batch==null) toast(tr(R.string.batch_missing)) else {
                val waiting=batch.state in setOf("QUEUED","FAILED")
                showNotices(if(waiting) db.visibleNotices(id) else db.notices(id),!waiting,batch)
            }
        }
    }
    private fun openSystem(intent: Intent) {
        try { startActivity(intent) }
        catch(_: ActivityNotFoundException) { openAppDetails() }
        catch(_: SecurityException) { openAppDetails() }
    }
    private fun openAppDetails() {
        try { startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))) }
        catch(_: ActivityNotFoundException) { toast(tr(R.string.app_details_missing)) }
        catch(_: SecurityException) { toast(tr(R.string.system_shortcut_blocked)) }
    }
    @Suppress("DEPRECATION")
    override fun onBackPressed() { if(page>=3) show(2) else super.onBackPressed() }
    private fun toast(message: String)=Toast.makeText(this,message,Toast.LENGTH_LONG).show()
}
