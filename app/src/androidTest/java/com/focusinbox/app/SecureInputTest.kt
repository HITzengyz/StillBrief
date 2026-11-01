package com.focusinbox.app

import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureInputTest {
    @Test fun secretMaskSurvivesSingleLineConfiguration() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val field=EditText(instrumentation.targetContext)
            field.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            field.setSingleLine(true)
            field.maskSecret()
            field.setText("synthetic-secret-not-a-real-key")
            assertTrue(field.transformationMethod is PasswordTransformationMethod)
            val rendered=field.transformationMethod.getTransformation(field.text,field).toString()
            assertNotEquals(field.text.toString(),rendered)
            assertFalse(rendered.contains("synthetic-secret"))
            assertEquals("synthetic-secret-not-a-real-key",field.text.toString())
        }
    }
}
