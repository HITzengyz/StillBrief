package com.focusinbox.app

enum class DeliveryTarget { LOCAL, FEISHU;
    companion object { fun fromStored(value: String): DeliveryTarget = valueOf(value) }
}

/** The persisted batch target owns routing. UI changes never choose the current send path. */
object DeliveryDispatcher {
    fun send(batch: Batch,config: RunConfig,local: (Long,String,UiLanguage)->Unit,feishu: (RunConfig,String)->Unit): Boolean {
        if(batch.message.isBlank()) return false
        demand(batch.target==config.target,ErrorCode.SEND_CONFIG)
        when(batch.target) {
            DeliveryTarget.LOCAL -> local(batch.id,batch.message,config.ai.language)
            DeliveryTarget.FEISHU -> feishu(config,batch.message)
        }
        return true
    }
    fun failureState(target: DeliveryTarget,error: Exception): String =
        if(target==DeliveryTarget.FEISHU && error !is IllegalArgumentException && error !is RemoteFailure) "UNCERTAIN" else "SEND_FAILED"
}
