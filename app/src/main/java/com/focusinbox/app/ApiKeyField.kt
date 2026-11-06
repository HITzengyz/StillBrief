package com.focusinbox.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.PersistableBundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast

/** The value is the current provider's form draft; actions never save it. */
class ApiKeyField(baseContext: Context,initial: String): LinearLayout(Appearance.wrap(baseContext)) {
    private val palette=Appearance.palette(context)
    val editText=EditText(context).apply {
        setTextColor(palette.text); setHintTextColor(palette.secondary)
        id=R.id.api_key_input; textSize=14f
        inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setSingleLine(true); maskSecret(); setText(initial)
        minimumWidth=0; minWidth=0
    }
    private val reveal: ImageButton
    private val copy: ImageButton
    private val clear: ImageButton
    private var revealed=false
    private val green=palette.accent
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    init {
        orientation=HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
        addView(editText,LayoutParams(0,LayoutParams.WRAP_CONTENT,1f))
        reveal=action(R.id.api_key_reveal,R.drawable.ic_key_visible,R.string.api_key_show) {
            val start=editText.selectionStart; val end=editText.selectionEnd
            revealed=!revealed
            if(revealed) editText.transformationMethod=HideReturnsTransformationMethod.getInstance() else editText.maskSecret()
            if(start>=0 && end>=0) editText.setSelection(start.coerceAtMost(editText.length()),end.coerceAtMost(editText.length()))
            updateReveal()
        }
        copy=action(R.id.api_key_copy,R.drawable.ic_key_copy,R.string.api_key_copy) {
            val clip=ClipData.newPlainText(context.getString(R.string.api_key_clip_label),editText.text.toString())
            // The literal is the platform key on API 33+, and remains readable by compatible keyboards on older devices.
            clip.description.extras=PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE",true) }
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
            if(Build.VERSION.SDK_INT<33) Toast.makeText(context,R.string.api_key_copied,Toast.LENGTH_SHORT).show()
        }
        clear=action(R.id.api_key_clear,R.drawable.ic_key_clear,R.string.api_key_clear) {
            editText.text.clear(); conceal()
            Toast.makeText(context,R.string.api_key_cleared,Toast.LENGTH_SHORT).show()
        }
        editText.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int)=Unit
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { updateEnabled() }
            override fun afterTextChanged(s: Editable?)=Unit
        })
        updateEnabled()
    }
    private fun action(viewId: Int,icon: Int,description: Int,click: ()->Unit): ImageButton = ImageButton(context).also {
        it.id=viewId; it.setImageResource(icon); it.imageTintList=ColorStateList.valueOf(green)
        it.contentDescription=context.getString(description); it.tooltipText=it.contentDescription
        it.setPadding(dp(12),dp(12),dp(12),dp(12))
        it.background=RippleDrawable(ColorStateList.valueOf(palette.ripple),GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius=dp(12).toFloat() },null)
        addView(it,LayoutParams(dp(48),dp(48))); it.setOnClickListener { click() }
    }
    private fun updateReveal() {
        reveal.setImageResource(if(revealed) R.drawable.ic_key_hidden else R.drawable.ic_key_visible)
        reveal.contentDescription=context.getString(if(revealed) R.string.api_key_hide else R.string.api_key_show)
        reveal.tooltipText=reveal.contentDescription
    }
    private fun updateEnabled() {
        val populated=editText.length()>0
        listOf(copy,clear).forEach { it.isEnabled=populated; it.alpha=if(populated) 1f else 0.35f }
    }
    fun conceal() {
        val start=editText.selectionStart; val end=editText.selectionEnd
        revealed=false; editText.maskSecret(); updateReveal()
        if(start>=0 && end>=0) editText.setSelection(start.coerceAtMost(editText.length()),end.coerceAtMost(editText.length()))
    }
    override fun onDetachedFromWindow() { conceal(); super.onDetachedFromWindow() }
}
