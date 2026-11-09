package com.focusinbox.app

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.min

/** A direct choice: selecting either row applies immediately and closes the sheet. */
class FocusModePicker(
    baseContext: Context,
    private val selected: FocusMode,
    private val minutes: Int,
    private val onSelected: (FocusMode)->Unit
) : Dialog(Appearance.wrap(baseContext),R.style.DurationSheetTheme) {
    private val palette=Appearance.palette(context)
    private fun dp(value: Int)=(value*context.resources.displayMetrics.density).toInt()
    private fun shape(color: Int,radius: Int)=GradientDrawable().apply {
        setColor(color); cornerRadius=dp(radius).toFloat()
    }
    private fun text(value: String,size: Float,bold: Boolean=false)=TextView(context).apply {
        text=value; textSize=size; setTextColor(if(bold) palette.text else palette.secondary)
        UiStyle.text(this,if(bold) 600 else 400)
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCancelable(true); setCanceledOnTouchOutside(true)
        val root=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(14),dp(20),dp(20))
            background=GradientDrawable().apply {
                setColor(palette.surface)
                cornerRadii=floatArrayOf(dp(28).toFloat(),dp(28).toFloat(),dp(28).toFloat(),dp(28).toFloat(),0f,0f,0f,0f)
            }
            clipToOutline=true
        }
        val header=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(text(context.getString(R.string.focus_mode_title),21f,true).apply {
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
            if(Build.VERSION.SDK_INT>=28) isAccessibilityHeading=true
        },LinearLayout.LayoutParams(0,-2,1f))
        header.addView(ImageButton(context).apply {
            contentDescription=context.getString(R.string.close)
            setImageDrawable(UiSymbols.drawable(context,"close",palette.secondary))
            background=RippleDrawable(ColorStateList.valueOf(palette.ripple),shape(Color.TRANSPARENT,24),null)
            setPadding(dp(12),dp(12),dp(12),dp(12))
            setOnClickListener { dismiss() }
        },LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(header,LinearLayout.LayoutParams(-1,-2))
        val maxHeight=(context.resources.displayMetrics.heightPixels*0.65f).toInt()
        val scroll=object: ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec(maxHeight,View.MeasureSpec.AT_MOST))
            }
        }.apply { isVerticalScrollBarEnabled=false }
        val choices=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
        FocusMode.entries.forEachIndexed { index,mode ->
            if(index>0) choices.addView(View(context).apply {
                setBackgroundColor(palette.outline); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },LinearLayout.LayoutParams(-1,dp(1).coerceAtLeast(1)).apply { marginStart=dp(16); marginEnd=dp(16); topMargin=dp(10) })
            val single=mode==FocusMode.SINGLE
            val chosen=mode==selected
            val row=LinearLayout(context).apply {
                gravity=Gravity.CENTER_VERTICAL; minimumHeight=dp(88)
                setPadding(dp(16),dp(16),dp(12),dp(16))
                isSelected=chosen; isFocusable=true; isClickable=true
                contentDescription=if(single) context.getString(R.string.focus_mode_single_option)
                    else context.getString(R.string.focus_mode_loop_option,minutes)
                background=RippleDrawable(ColorStateList.valueOf(palette.ripple),shape(if(chosen) palette.selected else palette.surface,18),null)
                setOnClickListener { dismiss(); onSelected(mode) }
            }
            val words=LinearLayout(context).apply {
                orientation=LinearLayout.VERTICAL; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            words.addView(text(context.getString(if(single) R.string.focus_mode_single else R.string.focus_mode_loop),18f,true))
            words.addView(text(context.getString(if(single) R.string.mode_picker_single_help else R.string.mode_picker_loop_help,minutes),14f),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) })
            row.addView(words,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(ImageView(context).apply {
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                if(chosen) setImageDrawable(UiSymbols.drawable(context,"check",palette.accent))
            },LinearLayout.LayoutParams(dp(24),dp(24)).apply { marginStart=dp(14) })
            choices.addView(row,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10) })
        }
        scroll.addView(choices); root.addView(scroll,LinearLayout.LayoutParams(-1,-2))
        root.setOnApplyWindowInsetsListener { view,insets ->
            if(Build.VERSION.SDK_INT>=30) {
                val bars=insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(dp(20)+bars.left,dp(14),dp(20)+bars.right,dp(20)+bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(dp(20),dp(14),dp(20),dp(20)+insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(root)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes=attributes.apply { dimAmount=0.28f }
            if(Build.VERSION.SDK_INT>=29) decorView.isForceDarkAllowed=false
            if(Build.VERSION.SDK_INT>=30) setDecorFitsSystemWindows(false)
        }
    }
    override fun show() {
        super.show()
        window?.apply {
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            setLayout(min(context.resources.displayMetrics.widthPixels,dp(520)),WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }
}
