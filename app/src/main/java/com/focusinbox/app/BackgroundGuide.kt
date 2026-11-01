package com.focusinbox.app

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale

/** System-reported battery state and user-initiated settings entry points only. */
object BackgroundGuide {
    data class State(val batteryExempt: Boolean,val powerSaveMode: Boolean,val backgroundRestricted: Boolean?)
    fun read(context: Context): State {
        val power=context.getSystemService(PowerManager::class.java)
        return State(power.isIgnoringBatteryOptimizations(context.packageName),power.isPowerSaveMode,
            if(Build.VERSION.SDK_INT>=28) context.getSystemService(ActivityManager::class.java).isBackgroundRestricted else null)
    }
    fun isHonorOrHuawei(manufacturer: String=Build.MANUFACTURER,brand: String=Build.BRAND): Boolean =
        listOf(manufacturer,brand).any { it.trim().lowercase(Locale.ROOT) in setOf("honor","huawei") }
    fun autoLaunchGuide(manufacturer: String=Build.MANUFACTURER,brand: String=Build.BRAND): Int =
        if(isHonorOrHuawei(manufacturer,brand)) R.string.autostart_honor_steps else R.string.autostart_generic_steps
    fun batteryScreens(packageName: String,alreadyExempt: Boolean): List<Intent> = buildList {
        if(!alreadyExempt) add(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.fromParts("package",packageName,null)))
        add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",packageName,null)))
    }
    /** A successful launch says nothing about the user's subsequent grant/deny decision. */
    fun openBatteryScreens(packageName: String,alreadyExempt: Boolean,start: (Intent)->Unit): Boolean {
        for(intent in batteryScreens(packageName,alreadyExempt)) {
            try { start(intent); return true }
            catch(_: ActivityNotFoundException) { /* Try the next standard system entry point. */ }
            catch(_: SecurityException) { /* Some phones block a settings shortcut. */ }
        }
        return false
    }
}
