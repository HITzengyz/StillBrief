package com.focusinbox.app

import android.text.method.PasswordTransformationMethod
import android.widget.EditText

/** Apply after setSingleLine, which otherwise replaces the password transformation. */
fun EditText.maskSecret() { transformationMethod=PasswordTransformationMethod.getInstance() }
