package com.focusinbox.app

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView

/** Separate collapsed/dropdown rows with no inherited OEM selection or popup tint. */
class MintSpinner(context: Context,labels: List<String>,selected: Int,description: String):
    Spinner(ContextThemeWrapper(context,R.style.MintSpinnerTheme),null,0,0,MODE_DROPDOWN,popupTheme(context)) {
    companion object {
        private val ink=Color.rgb(28,69,51)
        private val selectedFill=Color.rgb(222,241,229)
        private val pressedFill=Color.rgb(201,231,213)
        private fun popupTheme(context: Context): Resources.Theme = context.resources.newTheme().apply {
            setTo(context.theme); applyStyle(R.style.MintSpinnerPopupTheme,true)
        }
    }
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private fun rounded(color: Int)=GradientDrawable().apply { setColor(color); cornerRadius=dp(12).toFloat() }
    private fun rowBackground()=StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed),android.graphics.drawable.ColorDrawable(pressedFill))
        addState(intArrayOf(android.R.attr.state_activated),android.graphics.drawable.ColorDrawable(selectedFill))
        addState(intArrayOf(android.R.attr.state_selected),android.graphics.drawable.ColorDrawable(selectedFill))
        addState(intArrayOf(android.R.attr.state_focused),android.graphics.drawable.ColorDrawable(selectedFill))
        addState(intArrayOf(android.R.attr.state_hovered),android.graphics.drawable.ColorDrawable(selectedFill))
        addState(intArrayOf(),android.graphics.drawable.ColorDrawable(Color.WHITE))
    }
    private fun text()=TextView(this.context).apply {
        textSize=15f; setTextColor(ink); typeface=Typeface.create("sans-serif",Typeface.NORMAL)
        gravity=Gravity.CENTER_VERTICAL; minimumHeight=dp(48)
    }
    init {
        contentDescription=description
        minimumHeight=dp(52); setPadding(0,0,0,0); backgroundTintList=null
        val fill=rounded(Color.rgb(239,247,242)).apply { setStroke(dp(1),Color.rgb(207,227,214)) }
        background=RippleDrawable(ColorStateList.valueOf(pressedFill),fill,rounded(Color.WHITE))
        setPopupBackgroundDrawable(rounded(Color.WHITE))
        dropDownWidth=ViewGroup.LayoutParams.MATCH_PARENT; dropDownVerticalOffset=dp(4); dropDownHorizontalOffset=0
        adapter=object: BaseAdapter() {
            override fun getCount()=labels.size
            override fun getItem(position: Int)=labels[position]
            override fun getItemId(position: Int)=position.toLong()
            override fun getView(position: Int,convertView: View?,parent: ViewGroup): View {
                val row=(convertView as? LinearLayout) ?: LinearLayout(this@MintSpinner.context).apply {
                    gravity=Gravity.CENTER_VERTICAL; setPadding(dp(14),dp(10),dp(14),dp(10))
                    addView(text(),LinearLayout.LayoutParams(0,-2,1f))
                    addView(TextView(this@MintSpinner.context).apply { text="⌄"; textSize=20f; setTextColor(ink); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(dp(24),-2))
                }
                (row.getChildAt(0) as TextView).text=labels[position]
                return row
            }
            override fun getDropDownView(position: Int,convertView: View?,parent: ViewGroup): View {
                if(parent is ListView) {
                    parent.background=rounded(Color.WHITE); parent.clipToOutline=true
                    parent.selector=rowBackground(); parent.divider=null; parent.setPadding(0,0,0,0)
                }
                return ((convertView as? TextView) ?: text().apply { setPadding(dp(16),dp(12),dp(16),dp(12)) }).apply {
                    text=labels[position]; background=rowBackground(); isActivated=position==selectedItemPosition
                }
            }
        }
        setSelection(selected,false)
    }
}
