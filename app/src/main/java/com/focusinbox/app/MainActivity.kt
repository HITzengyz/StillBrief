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
    private var clock: TextView?=null
    private var status: TextView?=null
    private var pending: TextView?=null
    private var startButton: Button?=null
    private var pauseButton: Button?=null
    private var restButton: Button?=null
    private var noticeDialog: AlertDialog?=null
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
        show(retained?.page ?: 0)
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
    private fun show(which: Int) {
        if(page==2) savedForm=captureForm?.invoke() ?: savedForm
        captureForm=null
        page=which; clock=null; status=null; pending=null; startButton=null; pauseButton=null; restButton=null
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(245,247,243)) }
        val top=LinearLayout(this).apply { setPadding(dp(20),dp(16),dp(20),0); orientation=LinearLayout.VERTICAL }
        label(tr(R.string.app_name),28f,true,top); label(tr(R.string.tagline),13f,parent=top); root.addView(top)
        val scroll=ScrollView(this); content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(4),dp(20),dp(24)) }; scroll.addView(content); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val nav=LinearLayout(this).apply { gravity=Gravity.CENTER; setPadding(dp(12),0,dp(12),dp(6)) }
        listOf(tr(R.string.nav_focus),tr(R.string.nav_notices),tr(R.string.nav_settings)).forEachIndexed { i,name -> nav.addView(Button(this).apply { text=if(i==which || i==2 && which>=2) "● $name" else name; setTextColor(green); textSize=14f; isAllCaps=false; minimumHeight=dp(54); setPadding(dp(4),dp(10),dp(4),dp(10)); stateListAnimator=null; elevation=0f; background=GradientDrawable().apply { setColor(if(i==which || i==2 && which>=2) Color.rgb(223,237,226) else Color.TRANSPARENT); cornerRadius=dp(12).toFloat() }; setOnClickListener { show(i) } },LinearLayout.LayoutParams(0,-2,1f)) }; root.addView(nav)
        setContentView(root)
        when(which) { 0 -> home(); 1 -> history(); 2 -> preferences(); 3 -> filtersPage(); 4 -> backgroundPage() }
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
                val c=card(); label("#${b.id} · ${stateName(b.state)}",16f,true,c); label(time(b.created),12f,parent=c)
                if(b.message.isNotBlank()) label(b.message,14f,parent=c)
                if(b.error.isNotBlank()) label(LocaleHelper.renderError(this,b.error),14f,parent=c)
                val unprocessed=b.state in setOf("QUEUED","FAILED")
                val records=if(unprocessed) db.visibleNotices(b.id) else db.notices(b.id)
                button(tr(R.string.batch_records,records.size),c) { showNotices(Store(this).use { if(unprocessed) it.visibleNotices(b.id) else it.notices(b.id) },!unprocessed) }
                if(b.state in setOf("FAILED","SEND_FAILED","UNCERTAIN")) button(tr(R.string.retry_manual),c) {
                    AlertDialog.Builder(this).setTitle(tr(R.string.retry_title)).setMessage(if(b.state=="UNCERTAIN") tr(R.string.retry_uncertain) else if(b.state=="SEND_FAILED") tr(R.string.retry_send) else tr(R.string.retry_analysis))
                        .setNegativeButton(tr(R.string.cancel),null).setPositiveButton(tr(R.string.retry_confirm)) { _,_ -> BatchEngine.retry(this,b.id); show(1) }.show()
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
    private fun showNotices(notices: List<Notice>,audit: Boolean=false) {
        val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(16)) }
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
        val hook=field(tr(R.string.webhook_label),retainedForm?.webhook ?: secret { settings.webhook },true)
        label(tr(R.string.webhook_help),12f)
        val keyNotice=label(if(keyError) tr(R.string.key_unreadable) else tr(R.string.provider_storage_help),12f)
        button(tr(R.string.save_settings),primary=true) {
            when {
                enabled.isChecked && (api.text.isBlank() || !Network.validUrl(hook.text.toString().trim(),true)) -> toast(tr(R.string.configure_required))
                enabled.isChecked && !settings.enabled && enabled.tag!=true -> toast(tr(R.string.consent_required))
                else -> try {
                    settings.saveBasic(selected,api.text.toString().trim(),hook.text.toString().trim(),enabled.isChecked)
                    providerHint.text=tr(R.string.provider_active_hint,settings.provider.title)
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
        val sign=field(tr(R.string.signing_label),retainedForm?.signingKey ?: secret { settings.signingKey },true,parent=advanced)
        label(tr(R.string.signing_help),12f,parent=advanced)
        val prompt=field(tr(R.string.prompt_label),retainedForm?.prompt ?: settings.prompt,multiline=true,parent=advanced)
        button(tr(R.string.save_advanced),advanced) {
            when {
                !Network.validUrl(endpoint.text.toString().trim()) -> toast(tr(R.string.endpoint_invalid))
                model.text.isBlank() -> toast(tr(R.string.error_model_required))
                else -> try {
                    settings.saveAdvanced(selected,endpoint.text.toString().trim(),model.text.toString().trim(),sign.text.toString().trim(),prompt.text.toString())
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
        captureForm={
            val saved=drafts.toMutableMap()
            saved[selected]=AiConfig(selected,api.text.toString(),endpoint.text.toString(),model.text.toString(),prompt.text.toString(),settings.language)
            SettingsDraft(selected,saved,hook.text.toString(),sign.text.toString(),prompt.text.toString(),enabled.isChecked,enabled.tag==true,advanced.visibility==View.VISIBLE)
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
        val pm=getSystemService(PowerManager::class.java)
        val am=getSystemService(ActivityManager::class.java)
        val component=ComponentName(this,InboxListener::class.java)
        val authorized=if(Build.VERSION.SDK_INT>=27) getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
            else AndroidSettings.Secure.getString(contentResolver,"enabled_notification_listeners").orEmpty().split(":").contains(component.flattenToString())
        val c=card(); label(tr(R.string.current_status),17f,true,c)
        label(tr(R.string.background_states,
            tr(if(authorized) R.string.access_granted else R.string.access_missing),
            tr(if(InboxListener.connected) R.string.connection_active else R.string.connection_missing),
            tr(if(TimerEngine.exact(this)) R.string.alarm_available else R.string.alarm_missing),
            tr(if(pm.isIgnoringBatteryOptimizations(packageName)) R.string.battery_exempt else R.string.battery_not_exempt),
            tr(if(Build.VERSION.SDK_INT<28) R.string.background_unsupported else if(am.isBackgroundRestricted) R.string.background_restricted else R.string.background_not_reported)
        ),14f,parent=c)
        button(tr(R.string.refresh_status),c) { show(4) }
        button(tr(R.string.notification_access_settings),c) { openSystem(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        if(Build.VERSION.SDK_INT>=31) button(tr(R.string.alarm_settings),c) { openSystem(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName"))) }
        button(tr(R.string.battery_settings),c) { openSystem(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        button(tr(R.string.app_details),c) { openSystem(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))) }
        val honor=card(); label(tr(R.string.honor_title),17f,true,honor)
        label(tr(R.string.honor_instructions),14f,parent=honor)
        button(tr(R.string.system_settings),honor) { openSystem(Intent(AndroidSettings.ACTION_SETTINGS)) }
        val recents=card(); label(tr(R.string.recents_title),17f,true,recents)
        label(tr(R.string.recents_help),13f,parent=recents)
        val hidden=Switch(this).apply { text=tr(R.string.recents_hide); isChecked=settings.hideRecents }; recents.addView(hidden)
        hidden.setOnCheckedChangeListener { _,on ->
            fun apply(value: Boolean) { settings.hideRecents=value; am.appTasks.forEach { it.setExcludeFromRecents(value) } }
            if(on) AlertDialog.Builder(this).setTitle(tr(R.string.recents_confirm_title)).setMessage(tr(R.string.recents_confirm_text))
                .setNegativeButton(tr(R.string.cancel)) { _,_ -> hidden.isChecked=false }.setPositiveButton(tr(R.string.hide)) { _,_ -> apply(true) }.setOnCancelListener { hidden.isChecked=false }.show()
            else apply(false)
        }
        button(tr(R.string.back_settings)) { show(2) }
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
