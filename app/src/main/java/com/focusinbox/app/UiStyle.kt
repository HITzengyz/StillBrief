package com.focusinbox.app

import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.graphics.text.LineBreaker
import android.widget.EditText
import android.widget.TextView
import android.widget.Switch

/** Shared native typography and recessed fields; no changes to input or timer state. */
object UiStyle {
    fun face(context: Context,weight: Int=400): Typeface {
        val dark=context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
        return if(Build.VERSION.SDK_INT>=28) Typeface.create(Typeface.SANS_SERIF,weight-if(dark) 40 else 0,false)
        else Typeface.create(if(weight>=500) "sans-serif-medium" else "sans-serif",Typeface.NORMAL)
    }
    // The constant is inlined as 1; TextView has supported this break strategy since API 23.
    @android.annotation.SuppressLint("InlinedApi")
    fun text(view: TextView,weight: Int=400,numbers: Boolean=false) {
        view.typeface=face(view.context,weight)
        if(numbers) view.fontFeatureSettings="'tnum'"
        if(view !is EditText) view.breakStrategy=LineBreaker.BREAK_STRATEGY_HIGH_QUALITY
    }
    private fun blend(a: Int,b: Int,amount: Float)=Color.rgb(
        (Color.red(a)*(1-amount)+Color.red(b)*amount).toInt(),
        (Color.green(a)*(1-amount)+Color.green(b)*amount).toInt(),
        (Color.blue(a)*(1-amount)+Color.blue(b)*amount).toInt())
    fun fieldBackground(context: Context): StateListDrawable {
        val p=Palette(context); val density=context.resources.displayMetrics.density
        fun shape(color: Int,radius: Float=12f)=GradientDrawable().apply { setColor(color); cornerRadius=radius*density }
        // Two low-contrast inset layers replace the platform underline, without changing field bounds.
        val inner=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(blend(p.control,p.text,0.025f),p.control)).apply { cornerRadius=10*density }
        val focused=LayerDrawable(arrayOf(shape(blend(p.control,p.accent,0.09f)),shape(blend(p.control,p.text,0.13f),11f),inner)).apply {
            val one=density.toInt().coerceAtLeast(1)
            setLayerInset(1,one,one,one,one)
            setLayerInset(2,2*one,2*one,2*one,2*one)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused),focused)
            addState(intArrayOf(android.R.attr.state_activated),focused)
            addState(intArrayOf(),shape(p.control))
        }
    }
    fun field(view: EditText,framed: Boolean=true) {
        val p=Palette(view.context); val density=view.resources.displayMetrics.density
        text(view); view.setTextColor(p.text); view.setHintTextColor(p.secondary)
        view.backgroundTintList=null
        view.background=if(framed) fieldBackground(view.context) else null
        view.minimumHeight=(52*density).toInt()
        view.setPadding((14*density).toInt(),(12*density).toInt(),(14*density).toInt(),(12*density).toInt())
    }
    fun toggle(view: Switch) {
        val p=Palette(view.context)
        val dark=view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
        val states=arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf())
        view.thumbTintList=ColorStateList(states,intArrayOf(p.accent,if(dark) p.secondary else p.surface))
        view.trackTintList=ColorStateList(states,intArrayOf(p.accent,p.secondary))
        text(view)
    }
}
