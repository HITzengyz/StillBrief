package com.focusinbox.app

import android.app.AlertDialog
import android.content.Context
import android.view.WindowManager

/** Keeps the platform's scrolling, input and button lifecycle with the app's mint palette. */
open class MintDialogBuilder(context: Context) : AlertDialog.Builder(context, R.style.MintDialogTheme) {
    override fun create(): AlertDialog = super.create().also { dialog ->
        // Resize native content above the IME, including custom EditText views.
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if(android.os.Build.VERSION.SDK_INT>=29) dialog.window?.decorView?.isForceDarkAllowed=false
    }
}
