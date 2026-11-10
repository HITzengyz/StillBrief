package com.focusinbox.app

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper

enum class AppearanceMode {
    SYSTEM, LIGHT, DARK;
    companion object {
        fun fromStored(value: String?)=entries.firstOrNull { it.name==value } ?: SYSTEM
    }
}

/** Keeps the app override separate from the device configuration so System can follow it again. */
object Appearance {
    fun mode(context: Context)=AppearanceMode.fromStored(context.getSharedPreferences("settings",Context.MODE_PRIVATE).getString("appearance",null))
    fun configuration(source: Configuration,mode: AppearanceMode): Configuration = Configuration(source).apply {
        val night=when(mode) {
            AppearanceMode.SYSTEM -> source.uiMode and Configuration.UI_MODE_NIGHT_MASK
            AppearanceMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
            AppearanceMode.DARK -> Configuration.UI_MODE_NIGHT_YES
        }
        uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
    }
    fun wrap(context: Context): Context = ContextThemeWrapper(context,R.style.AppTheme).apply {
        // Retain Activity services (especially the dialog WindowManager token) while overriding resources.
        applyOverrideConfiguration(configuration(context.resources.configuration,mode(context)))
    }
    fun palette(context: Context)=Palette(wrap(context))
}

class Palette(context: Context) {
    val background=context.getColor(R.color.sb_background)
    val surface=context.getColor(R.color.sb_surface)
    val text=context.getColor(R.color.sb_text)
    val secondary=context.getColor(R.color.sb_secondary)
    val accent=context.getColor(R.color.sb_accent)
    val onAccent=context.getColor(R.color.sb_on_accent)
    val success=context.getColor(R.color.sb_success)
    val error=context.getColor(R.color.sb_error)
    val control=context.getColor(R.color.sb_control)
    val selected=context.getColor(R.color.sb_selected)
    val outline=context.getColor(R.color.sb_outline)
    val ripple=context.getColor(R.color.sb_ripple)
    val track=context.getColor(R.color.sb_track)
    val tick=context.getColor(R.color.sb_tick)
    val tickActive=context.getColor(R.color.sb_tick_active)
}
