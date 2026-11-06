package com.focusinbox.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeWidgetTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun host(night: Boolean): Context {
        val configuration=Configuration(context.resources.configuration).apply {
            uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if(night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return ContextThemeWrapper(context.createConfigurationContext(configuration),android.R.style.Theme_Material_Light_NoActionBar)
    }
    private fun text(view: View,id: Int,color: Int) {
        assertEquals("Text color for $id",color,view.findViewById<TextView>(id).currentTextColor)
    }
    private fun background(view: View,id: Int,color: Int) {
        val drawable=view.findViewById<View>(id).background
        val fill=if(drawable is RippleDrawable) drawable.getDrawable(0) else drawable
        assertTrue("Expected an explicit widget fill for $id",fill is GradientDrawable)
        assertEquals("Background color for $id",color,(fill as GradientDrawable).color!!.defaultColor)
    }
    @Test fun realRemoteViewsRespectForcedModeAcrossHostConfigurationsAndSystemFollowsHost() {
        val settings=Settings(context); val saved=settings.appearance
        val now=System.currentTimeMillis()
        val running=WidgetState("RUNNING",7,now+35*60_000L,0,35,true,1,false,true)
        try {
            for(mode in AppearanceMode.entries) {
                settings.appearance=mode
                for(producerNight in listOf(false,true)) for(shape in listOf("wide","square","quick")) {
                    // Reuse one payload in both hosts: SYSTEM must not freeze its producer's colors.
                    val producer=host(producerNight)
                    val remote=when(shape) {
                        "quick" -> FocusWidgets.renderQuick(producer,123,running,now,0)
                        else -> FocusWidgets.render(producer,123,shape=="wide",running,now,0)
                    }
                    for(hostNight in listOf(false,true)) instrumentation.runOnMainSync {
                        val launcher=host(hostNight)
                        val view=remote.apply(launcher,FrameLayout(launcher))
                        val dark=mode==AppearanceMode.DARK || (mode==AppearanceMode.SYSTEM && hostNight)
                        fun color(light: Int,darkColor: Int)=launcher.getColor(if(dark) darkColor else light)
                        val main=color(R.color.sb_light_text,R.color.sb_dark_text)
                        val accent=color(R.color.sb_light_accent,R.color.sb_dark_accent)
                        val onAccent=color(R.color.sb_light_on_accent,R.color.sb_dark_on_accent)
                        background(view,R.id.widget_root,color(R.color.sb_light_surface,R.color.sb_dark_surface))
                        background(view,R.id.widget_primary,accent)
                        text(view,R.id.widget_timer,main); text(view,R.id.widget_time_static,main)
                        text(view,R.id.widget_primary,onAccent)
                        if(shape=="quick") {
                            background(view,R.id.widget_quick_setup,accent)
                            text(view,R.id.widget_quick_setup,onAccent)
                            for(id in listOf(R.id.widget_preset_25,R.id.widget_preset_35,R.id.widget_preset_45)) {
                                text(view,id,accent)
                                background(view,id,color(R.color.sb_light_control,R.color.sb_dark_control))
                            }
                        } else {
                            text(view,R.id.widget_mode,accent)
                            text(view,R.id.widget_status,color(R.color.sb_light_secondary,R.color.sb_dark_secondary))
                            text(view,R.id.widget_secondary,accent)
                            background(view,R.id.widget_secondary,color(R.color.sb_light_control,R.color.sb_dark_control))
                            if(shape=="square") text(view,R.id.widget_brand,main)
                        }
                    }
                }
            }
        } finally { settings.appearance=saved }
    }
    @Test fun switchingFromForcedToSystemUsesDifferentLayoutsForLauncherReinflation() {
        val settings=Settings(context); val saved=settings.appearance
        val idle=WidgetState("IDLE",0,0,0,35,false,0,false,true)
        try {
            for(shape in listOf("wide","square","quick")) {
                val ids=AppearanceMode.entries.map { mode ->
                    settings.appearance=mode
                    if(shape=="quick") FocusWidgets.renderQuick(context,123,idle).layoutId
                    else FocusWidgets.render(context,123,shape=="wide",idle).layoutId
                }
                assertEquals("Each appearance needs a fresh layout for $shape",3,ids.toSet().size)
            }
        } finally { settings.appearance=saved }
    }
}
