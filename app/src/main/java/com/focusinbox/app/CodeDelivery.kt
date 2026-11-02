package com.focusinbox.app

/** Check the saved destination again when queued work runs, not just when the notification arrived. */
object CodeDelivery {
    fun send(batch: Batch,settings: Settings,now: Long=System.currentTimeMillis(),sender: (RunConfig,String)->Unit=Network::send) {
        demand(batch.target==DeliveryTarget.FEISHU && settings.codesActive,ErrorCode.CODES_DISABLED)
        demand(VerificationCodes.fresh(batch.created,now),ErrorCode.CODE_EXPIRED)
        val config=settings.codeDeliveryConfig()
        demand(config.enabled,ErrorCode.CODES_DISABLED)
        sender(config,batch.message)
    }
}
