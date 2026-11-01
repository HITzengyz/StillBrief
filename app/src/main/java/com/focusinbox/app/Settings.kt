package com.focusinbox.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var enabled: Boolean get() = prefs.getBoolean("enabled", false); set(v) { prefs.edit().putBoolean("enabled", v).commit() }
    var minutes: Int get() = prefs.getInt("minutes", 25); set(v) { prefs.edit().putInt("minutes", v).commit() }
    var model: String get() = prefs.getString("model", "deepseek-flash")!!; set(v) { prefs.edit().putString("model", v).commit() }
    var endpoint: String get() = prefs.getString("endpoint", "https://api.deepseek.com/chat/completions")!!; set(v) { prefs.edit().putString("endpoint", v).commit() }
    var prompt: String get() = prefs.getString("prompt", "")!!; set(v) { prefs.edit().putString("prompt", v).commit() }
    var excluded: Set<String> get() = prefs.getStringSet("excluded", emptySet())!!.toSet(); set(v) { prefs.edit().putStringSet("excluded", v).commit() }
    var apiKey: String get() = secret("key"); set(v) { putSecret("key", v) }
    var webhook: String get() = secret("hook"); set(v) { putSecret("hook", v) }
    var signingKey: String get() = secret("sign"); set(v) { putSecret("sign", v) }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("focus_secrets", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("focus_secrets", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun secret(name: String): String {
        val encoded = prefs.getString(name, null) ?: return ""
        val parts = encoded.split(":")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }
    private fun putSecret(name: String, value: String) {
        if(value.isBlank()) { prefs.edit().remove(name).commit(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.doFinal(value.toByteArray())
        prefs.edit().putString(name, Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)).commit()
    }
    fun clearSecrets() { prefs.edit().remove("key").remove("hook").remove("sign").putBoolean("enabled",false).commit() }
}
