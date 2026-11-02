package com.focusinbox.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.WindowManager
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MintDialogTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private var dialog: AlertDialog? = null

    @Before fun launch() {
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    }
    @After fun close() {
        instrumentation.runOnMainSync { dialog?.dismiss(); activity.finish() }
    }

    @Test fun mintThemePreservesConfirmationAndDismissCallbacks() {
        var confirmed = 0
        var dismissed = 0
        instrumentation.runOnMainSync {
            dialog = MintDialogBuilder(activity).setTitle("Confirm").setMessage("Message")
                .setNegativeButton("Cancel", null).setPositiveButton("Confirm") { _, _ -> confirmed++ }
                .create().also { it.setOnDismissListener { dismissed++ }; it.show() }
            val shown = dialog!!
            val background = shown.window!!.decorView.background as GradientDrawable
            assertEquals(Color.rgb(249,252,249), background.color!!.defaultColor)
            assertTrue(background.cornerRadius > 0f)
            assertEquals(Color.WHITE, shown.getButton(AlertDialog.BUTTON_POSITIVE).currentTextColor)
            assertEquals(Color.rgb(35,121,111), shown.getButton(AlertDialog.BUTTON_NEGATIVE).currentTextColor)
            shown.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        instrumentation.waitForIdleSync()
        assertEquals(1, confirmed)
        assertEquals(1, dismissed)
    }

    @Test fun nativeListRetainsSelectionCallbackAndLongContentScrolls() {
        var selected = -1
        instrumentation.runOnMainSync {
            dialog = MintDialogBuilder(activity).setTitle("Apps")
                .setItems(Array(100) { "App $it" }) { _, which -> selected = which }.show()
            assertEquals(100, dialog!!.listView.adapter.count)
            dialog!!.listView.performItemClick(null, 37, 37)
        }
        instrumentation.waitForIdleSync()
        assertEquals(37, selected)
        lateinit var scroll: ScrollView
        instrumentation.runOnMainSync {
            scroll = ScrollView(activity).apply {
                addView(TextView(activity).apply { text = (1..200).joinToString("\n") { "Record $it" } })
            }
            dialog = MintDialogBuilder(activity).setTitle("Records").setView(scroll)
                .setPositiveButton("Close", null).show()
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val customPanel = dialog!!.window!!.decorView
            assertTrue(customPanel.height <= activity.resources.displayMetrics.heightPixels)
            assertTrue(scroll.canScrollVertically(1))
            assertTrue(dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).isShown)
        }
    }

    @Test fun inputValidationCanKeepDialogOpenAndUsesResizeForKeyboard() {
        lateinit var input: EditText
        instrumentation.runOnMainSync {
            input = EditText(activity)
            dialog = MintDialogBuilder(activity).setTitle("Input").setView(input)
                .setPositiveButton("Confirm", null).create().also { shown ->
                    shown.setOnShowListener {
                        shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            if(input.text.toString().toIntOrNull() == null) input.error = "Invalid"
                            else shown.dismiss()
                        }
                    }
                    shown.show()
                }
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            input.setText("invalid")
            dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertTrue(dialog!!.isShowing)
            assertNotNull(input.error)
            assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                dialog!!.window!!.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
            input.setText("17")
            dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertFalse(dialog!!.isShowing)
        }
    }
}
