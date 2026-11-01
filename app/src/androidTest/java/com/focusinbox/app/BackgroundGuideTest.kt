package com.focusinbox.app

import android.Manifest
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.PowerManager
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class BackgroundGuideTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test @Suppress("DEPRECATION") fun requestHasDeclaredPermissionAndTargetsOnlyThisPackage() {
        val requested=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(requested.contains(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
        val request=BackgroundGuide.batteryScreens(context.packageName,false).first()
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,request.action)
        assertEquals("package",request.data!!.scheme)
        assertEquals(context.packageName,request.data!!.schemeSpecificPart)
        assertEquals("package:${context.packageName}",request.dataString)
    }
    @Test fun acceptedLaunchDoesNotOpenAnyFallbackOrPretendToGrant() {
        val attempts=mutableListOf<Intent>()
        assertTrue(BackgroundGuide.openBatteryScreens(context.packageName,false) { attempts.add(it) })
        // The helper ends after launch; a later user's allow/deny decision is not guessed.
        assertEquals(1,attempts.size)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,attempts.single().action)
    }
    @Test fun alreadyExemptOpensManagementInsteadOfAnotherRequest() {
        val attempts=mutableListOf<Intent>()
        assertTrue(BackgroundGuide.openBatteryScreens(context.packageName,true) { attempts.add(it) })
        assertEquals(1,attempts.size)
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,attempts.single().action)
        assertNull(attempts.single().data)
    }
    @Test fun unavailableRequestFallsBackToOptimizationList() {
        val actions=mutableListOf<String?>()
        assertTrue(BackgroundGuide.openBatteryScreens(context.packageName,false) { intent ->
            actions.add(intent.action)
            if(actions.size==1) throw ActivityNotFoundException("Synthetic missing entry")
        })
        assertEquals(listOf(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),actions)
    }
    @Test fun blockedRequestAndMissingListFallBackToOwnAppDetails() {
        val attempts=mutableListOf<Intent>()
        assertTrue(BackgroundGuide.openBatteryScreens(context.packageName,false) { intent ->
            attempts.add(intent)
            when(attempts.size) { 1 -> throw SecurityException("Synthetic blocked entry"); 2 -> throw ActivityNotFoundException("Synthetic missing list") }
        })
        assertEquals(3,attempts.size)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,attempts.last().action)
        assertEquals("package:${context.packageName}",attempts.last().dataString)
    }
    @Test fun allEntryPointsBlockedReportFailureAndStop() {
        val actions=mutableListOf<String?>()
        assertFalse(BackgroundGuide.openBatteryScreens(context.packageName,false) { intent ->
            actions.add(intent.action); throw SecurityException("Synthetic blocked entry")
        })
        assertEquals(listOf(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,Settings.ACTION_APPLICATION_DETAILS_SETTINGS),actions)
    }
    @Test fun alreadyExemptStillFallsBackFromListToDetails() {
        val attempts=mutableListOf<Intent>()
        assertTrue(BackgroundGuide.openBatteryScreens(context.packageName,true) {
            attempts.add(it); if(attempts.size==1) throw SecurityException("Synthetic blocked list")
        })
        assertEquals(listOf(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,Settings.ACTION_APPLICATION_DETAILS_SETTINGS),attempts.map { it.action })
    }
    @Test fun displayedStateComesFromSystemServices() {
        val state=BackgroundGuide.read(context)
        val power=context.getSystemService(PowerManager::class.java)
        assertEquals(power.isIgnoringBatteryOptimizations(context.packageName),state.batteryExempt)
        assertEquals(power.isPowerSaveMode,state.powerSaveMode)
        if(Build.VERSION.SDK_INT>=28) assertEquals(context.getSystemService(ActivityManager::class.java).isBackgroundRestricted,state.backgroundRestricted)
        else assertNull(state.backgroundRestricted)
    }
    @Test fun manufacturerAndBrandSelectOnlySupportedGuidance() {
        assertTrue(BackgroundGuide.isHonorOrHuawei("HONOR","generic"))
        assertTrue(BackgroundGuide.isHonorOrHuawei("unknown"," hUaWeI "))
        assertFalse(BackgroundGuide.isHonorOrHuawei("Google","google"))
        assertFalse(BackgroundGuide.isHonorOrHuawei("not-honor","generic"))
        assertEquals(R.string.autostart_honor_steps,BackgroundGuide.autoLaunchGuide("HONOR","generic"))
        assertEquals(R.string.autostart_generic_steps,BackgroundGuide.autoLaunchGuide("Google","google"))
        val zh=context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList(Locale.CHINESE)) })
        val steps=zh.getString(BackgroundGuide.autoLaunchGuide("huawei","generic"),"息间")
        assertTrue(steps.contains("自动管理")); assertTrue(steps.contains("自启动")); assertTrue(steps.contains("关联启动")); assertTrue(steps.contains("后台活动"))
    }
}
