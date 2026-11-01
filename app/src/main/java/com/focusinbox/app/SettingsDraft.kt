package com.focusinbox.app

/** In-memory only. Never put unsaved API keys in an instance-state Bundle or preferences. */
class SettingsDraft(val selected: Provider,val providers: Map<Provider,AiConfig>,val webhook: String,
    val signingKey: String,val prompt: String,val enabled: Boolean,val consent: Boolean,val expanded: Boolean)
class RetainedScreen(val page: Int,val draft: SettingsDraft?)
