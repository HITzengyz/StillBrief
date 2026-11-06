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

class Settings(context: Context, prefsName: String = "settings") {
    companion object { private val lock=Any() }
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    init { synchronized(lock) {
        if(!prefs.contains("destination")) {
            // Locale initialization may have written only language; that is still a fresh install.
            val legacy=prefs.getBoolean("providers_migrated",false) || listOf("provider","key","hook","enabled","endpoint","model").any { prefs.contains(it) }
            prefs.edit().putString("destination",if(legacy) DeliveryTarget.FEISHU.name else DeliveryTarget.LOCAL.name).commit()
        }
        if(!prefs.getBoolean("providers_migrated",false)) {
            val editor=prefs.edit()
            prefs.getString("key",null)?.let { editor.putString("provider.deepseek.key",it) }
            editor.putString("provider.deepseek.endpoint",prefs.getString("endpoint",Provider.DEEPSEEK.endpoint))
                .putString("provider.deepseek.model",prefs.getString("model",Provider.DEEPSEEK.model))
                .putString("provider",Provider.DEEPSEEK.id).putBoolean("providers_migrated",true)
                .remove("key").remove("endpoint").remove("model").commit()
        }
    } }
    var language: UiLanguage get() = UiLanguage.fromTag(prefs.getString("language","zh")); set(v) { synchronized(lock) { prefs.edit().putString("language",v.tag).commit() } }
    var appearance: AppearanceMode get() = AppearanceMode.fromStored(prefs.getString("appearance",null)); set(v) { synchronized(lock) { prefs.edit().putString("appearance",v.name).commit() } }
    val destination: DeliveryTarget get() = DeliveryTarget.fromStored(prefs.getString("destination",DeliveryTarget.LOCAL.name)!!)
    val provider: Provider get() = Provider.fromId(prefs.getString("provider",Provider.DEEPSEEK.id)!!)
    var enabled: Boolean get() = prefs.getBoolean("enabled", false); set(v) { synchronized(lock) { prefs.edit().putBoolean("enabled", v).commit() } }
    var forwardCodes: Boolean get() = prefs.getBoolean("forwardCodes",false); set(v) { synchronized(lock) { prefs.edit().putBoolean("forwardCodes",v).commit() } }
    val codesActive: Boolean get() = VerificationCodes.active(forwardCodes,destination)
    fun codeDeliveryConfig(): RunConfig = synchronized(lock) {
        RunConfig(codesActive,AiConfig(provider,"",provider.endpoint,provider.model,"",language),webhook,signingKey,DeliveryTarget.FEISHU)
    }
    var focusMode: FocusMode get() = FocusMode.fromStored(prefs.getString("focusMode",null)); set(v) { prefs.edit().putString("focusMode",v.name).commit() }
    var minutes: Int get() = prefs.getInt("minutes", 25); set(v) { prefs.edit().putInt("minutes", v).commit() }
    // Aliases preserve existing local callers while each provider owns its own values.
    var model: String get() = prefs.getString("provider.${provider.id}.model",provider.model)!!; set(v) { synchronized(lock) { prefs.edit().putString("provider.${provider.id}.model", v).commit() } }
    var endpoint: String get() = prefs.getString("provider.${provider.id}.endpoint",provider.endpoint)!!; set(v) { synchronized(lock) { prefs.edit().putString("provider.${provider.id}.endpoint", v).commit() } }
    var prompt: String get() = prefs.getString("prompt", "")!!; set(v) { synchronized(lock) { prefs.edit().putString("prompt", v).commit() } }
    var excluded: Set<String> get() = prefs.getStringSet("excluded", emptySet())!!.toSet(); set(v) { prefs.edit().putStringSet("excluded", v).commit() }
    var apiKey: String get() = secret("provider.${provider.id}.key"); set(v) { synchronized(lock) { putSecret("provider.${provider.id}.key", v) } }
    var webhook: String get() = secret("hook"); set(v) { synchronized(lock) { putSecret("hook", v) } }
    var signingKey: String get() = secret("sign"); set(v) { synchronized(lock) { putSecret("sign", v) } }
    var hideRecents: Boolean get() = prefs.getBoolean("hideRecents",false); set(v) { prefs.edit().putBoolean("hideRecents",v).commit() }
    fun providerEndpoint(p: Provider): String = prefs.getString("provider.${p.id}.endpoint",p.endpoint)!!
    fun providerModel(p: Provider): String = prefs.getString("provider.${p.id}.model",p.model)!!
    fun config(p: Provider): AiConfig = synchronized(lock) {
        AiConfig(p,secret("provider.${p.id}.key"),providerEndpoint(p),providerModel(p),prompt,language)
    }
    fun snapshot(target: DeliveryTarget=destination): RunConfig = synchronized(lock) {
        RunConfig(enabled,config(provider),if(target==DeliveryTarget.FEISHU) webhook else "",if(target==DeliveryTarget.FEISHU) signingKey else "",target)
    }
    /** Only basic save chooses the active provider. Advanced saves never switch it. */
    fun saveBasic(key: String,hook: String,on: Boolean) = saveBasic(provider,key,hook,on)
    fun saveBasic(p: Provider,key: String,hook: String,on: Boolean) = saveBasic(p,key,hook,on,DeliveryTarget.FEISHU)
    fun saveBasic(p: Provider,key: String,hook: String,on: Boolean,target: DeliveryTarget,codes: Boolean=forwardCodes) = synchronized(lock) {
        val encryptedKey=if(key.isBlank()) null else encrypt(key)
        val editor=prefs.edit().putString("provider.${p.id}.key",encryptedKey)
            .putString("provider",p.id).putBoolean("enabled",on).putString("destination",target.name).putBoolean("forwardCodes",codes)
        if(target==DeliveryTarget.FEISHU) editor.putString("hook",if(hook.isBlank()) null else encrypt(hook))
        editor.commit()
        Unit
    }
    fun saveAdvanced(endpointValue: String,modelValue: String,signValue: String,promptValue: String) =
        saveAdvanced(provider,endpointValue,modelValue,signValue,promptValue)
    fun saveAdvanced(p: Provider,endpointValue: String,modelValue: String,signValue: String,promptValue: String) =
        saveAdvanced(p,endpointValue,modelValue,signValue,promptValue,DeliveryTarget.FEISHU)
    fun saveAdvanced(p: Provider,endpointValue: String,modelValue: String,signValue: String,promptValue: String,target: DeliveryTarget) = synchronized(lock) {
        val editor=prefs.edit().putString("provider.${p.id}.endpoint",endpointValue).putString("provider.${p.id}.model",modelValue).putString("prompt",promptValue)
        if(target==DeliveryTarget.FEISHU) editor.putString("sign",if(signValue.isBlank()) null else encrypt(signValue))
        editor.commit()
        Unit
    }
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
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.doFinal(value.toByteArray())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
    }
    private fun putSecret(name: String, value: String) {
        prefs.edit().putString(name,if(value.isBlank()) null else encrypt(value)).commit()
    }
    fun clearSecrets() = synchronized(lock) {
        val editor=prefs.edit().remove("key").remove("hook").remove("sign").putBoolean("enabled",false).putBoolean("forwardCodes",false)
        Provider.entries.forEach { editor.remove("provider.${it.id}.key") }
        editor.commit()
        Unit
    }
}
