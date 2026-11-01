package com.focusinbox.app

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.min

/** All values are a local draft until the explicit confirmation callback. */
class DurationPicker(context: Context,initialMinutes: Int,private val heading: String,private val confirmed: (Int)->Unit):
    Dialog(context,android.R.style.Theme_Material_Light_Dialog_NoActionBar) {
    val dial=DurationDial(context)
    private var numericDialog: AlertDialog?=null
    private val chips=linkedMapOf<Int,Button>()
    private val green=Color.rgb(35,121,111)
    private fun dp(value: Int)=(value*context.resources.displayMetrics.density).toInt()
    init { dial.setMinutes(initialMinutes); setCancelable(true); setCanceledOnTouchOutside(true) }
    private fun label(text: String,size: Float,color: Int=Color.rgb(36,67,54))=TextView(context).apply {
        this.text=text; textSize=size; setTextColor(color); gravity=Gravity.CENTER
    }
    private fun shape(color: Int,radius: Int)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
    private fun action(text: String,primary: Boolean=false)=Button(context).apply {
        this.text=text; isAllCaps=false; textSize=15f; stateListAnimator=null; elevation=0f
        minimumHeight=dp(52); minWidth=0; setPadding(dp(12),dp(10),dp(12),dp(10))
        background=shape(if(primary) green else Color.rgb(232,241,236),14)
        setTextColor(if(primary) Color.WHITE else green)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val metrics=context.resources.displayMetrics
        val bodyLimit=(metrics.heightPixels*0.88f-dp(114)-dp((30*(context.resources.configuration.fontScale-1).coerceAtLeast(0f)).toInt())).toInt().coerceAtLeast(dp(140))
        val root=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(14),dp(20),dp(12))
            background=GradientDrawable().apply {
                setColor(Color.rgb(249,252,249)); cornerRadii=floatArrayOf(dp(28).toFloat(),dp(28).toFloat(),dp(28).toFloat(),dp(28).toFloat(),0f,0f,0f,0f)
            }; clipToOutline=true
        }
        val scroll=object: ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec(bodyLimit,View.MeasureSpec.AT_MOST))
            }
        }.apply { isVerticalScrollBarEnabled=false; overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS }
        val body=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
        body.addView(View(context).apply { background=shape(Color.rgb(203,220,208),2); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(dp(34),dp(4)).apply { gravity=Gravity.CENTER_HORIZONTAL; bottomMargin=dp(16) })
        body.addView(label(heading,19f).apply { setTypeface(null,Typeface.BOLD) },LinearLayout.LayoutParams(-1,-2))
        dial.id=R.id.duration_dial
        body.addView(dial,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(4) })
        body.addView(label(context.getString(R.string.duration_drag_hint),12f,Color.rgb(108,133,118)),LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(16) })
        body.addView(label(context.getString(R.string.duration_presets_label),12f,Color.rgb(108,133,118)),LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        val choices=LinearLayout(context).apply { id=R.id.duration_presets; orientation=LinearLayout.VERTICAL }
        val available=min(metrics.widthPixels,dp(520))-dp(40)
        val single=available>=dp(310) && context.resources.configuration.fontScale<=1.3f
        val groups=if(single) listOf(listOf(15,25,35,45,60)) else listOf(listOf(15,25,35),listOf(45,60))
        groups.forEach { group ->
            val row=LinearLayout(context).apply { gravity=Gravity.CENTER }
            if(!single && group.size==2) row.addView(View(context),LinearLayout.LayoutParams(0,1,0.5f))
            group.forEach { minutes ->
                val chip=action(minutes.toString()).apply {
                    tag=minutes; textSize=14f; minimumHeight=dp(44); setPadding(dp(8),dp(8),dp(8),dp(8))
                    background=shape(Color.rgb(235,243,237),30)
                    contentDescription=context.getString(R.string.minutes_value,minutes)
                    setOnClickListener { dial.setMinutes(minutes) }
                }
                chips[minutes]=chip
                row.addView(chip,LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(3); rightMargin=dp(3) })
            }
            if(!single && group.size==2) row.addView(View(context),LinearLayout.LayoutParams(0,1,0.5f))
            choices.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        }
        body.addView(choices); scroll.addView(body); root.addView(scroll,LinearLayout.LayoutParams(-1,-2))
        val footer=LinearLayout(context).apply { setPadding(0,dp(12),0,0) }
        footer.addView(action(context.getString(R.string.cancel)).apply { id=R.id.duration_cancel; setOnClickListener { cancel() } },LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=dp(6) })
        footer.addView(action(context.getString(R.string.confirm),true).apply {
            id=R.id.duration_confirm; setOnClickListener { val value=dial.minutes; dismiss(); confirmed(value) }
        },LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(6) })
        root.addView(footer,LinearLayout.LayoutParams(-1,-2))
        root.setOnApplyWindowInsetsListener { view,insets ->
            if(Build.VERSION.SDK_INT>=30) {
                val bars=insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(dp(20)+bars.left,dp(14),dp(20)+bars.right,dp(12)+bars.bottom)
            } else { @Suppress("DEPRECATION") view.setPadding(dp(20),dp(14),dp(20),dp(12)+insets.systemWindowInsetBottom) }
            insets
        }
        setContentView(root)
        dial.onValueChanged={ updateChips() }; dial.onExactInput={ exactInput() }; updateChips()
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes=attributes.apply { dimAmount=0.28f }
            if(Build.VERSION.SDK_INT>=30) setDecorFitsSystemWindows(false)
        }
    }
    override fun show() {
        super.show()
        window?.apply { setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL); setLayout(min(context.resources.displayMetrics.widthPixels,dp(520)),WindowManager.LayoutParams.WRAP_CONTENT) }
    }
    private fun updateChips() {
        chips.forEach { (minutes,chip) ->
            val selected=minutes==dial.minutes
            chip.isSelected=selected; chip.setTextColor(if(selected) Color.WHITE else green)
            (chip.background as GradientDrawable).setColor(if(selected) green else Color.rgb(235,243,237))
        }
    }
    private fun exactInput() {
        if(numericDialog?.isShowing==true) return
        val input=EditText(context).apply {
            id=R.id.duration_exact_input; inputType=InputType.TYPE_CLASS_NUMBER; setText(dial.minutes.toString()); setSelectAllOnFocus(true); hint=context.getString(R.string.duration_hint)
        }
        val wrapper=LinearLayout(context).apply { setPadding(dp(24),dp(8),dp(24),0); addView(input,LinearLayout.LayoutParams(-1,-2)) }
        numericDialog=AlertDialog.Builder(context).setTitle(R.string.duration_exact_title).setView(wrapper)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.confirm,null).create().also { dialog ->
                dialog.setOnShowListener {
                    input.requestFocus(); dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val value=input.text.toString().toIntOrNull()
                        if(value==null || value !in 1..180) input.error=context.getString(R.string.duration_invalid)
                        else { dial.setMinutes(value); dialog.dismiss() }
                    }
                }
                dialog.setOnDismissListener { numericDialog=null }; dialog.show()
            }
    }
    override fun dismiss() {
        val child=numericDialog; numericDialog=null; child?.dismiss(); dial.cancelGesture()
        super.dismiss()
    }
}
