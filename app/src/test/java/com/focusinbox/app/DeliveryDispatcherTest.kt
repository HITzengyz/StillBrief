package com.focusinbox.app

import org.junit.Assert.*
import org.junit.Test

class DeliveryDispatcherTest {
    private fun config(target: DeliveryTarget)=RunConfig(true,AiConfig(Provider.DEEPSEEK,"synthetic",Provider.DEEPSEEK.endpoint,Provider.DEEPSEEK.model,"",UiLanguage.ENGLISH),"synthetic-hook","synthetic-sign",target)
    private fun batch(target: DeliveryTarget,message: String="Summary")=Batch(4294967297,"READY",1,message,"","1",target)
    @Test fun persistedLocalTargetChoosesOnlyLocalSender() {
        var local=0; var feishu=0
        assertTrue(DeliveryDispatcher.send(batch(DeliveryTarget.LOCAL),config(DeliveryTarget.LOCAL),{ id,message,language ->
            local++; assertEquals(4294967297,id); assertEquals("Summary",message); assertEquals(UiLanguage.ENGLISH,language)
        },{ _,_ -> feishu++ }))
        assertEquals(1,local); assertEquals(0,feishu)
    }
    @Test fun persistedFeishuTargetChoosesOnlyFeishuSender() {
        var local=0; var feishu=0
        assertTrue(DeliveryDispatcher.send(batch(DeliveryTarget.FEISHU),config(DeliveryTarget.FEISHU),{ _,_,_ -> local++ },{ snapshot,message ->
            feishu++; assertEquals(DeliveryTarget.FEISHU,snapshot.target); assertEquals("Summary",message)
        }))
        assertEquals(0,local); assertEquals(1,feishu)
    }
    @Test fun mismatchedSnapshotCannotSendToEitherDestination() {
        var calls=0
        assertThrows(AppFailure::class.java) {
            DeliveryDispatcher.send(batch(DeliveryTarget.LOCAL),config(DeliveryTarget.FEISHU),{ _,_,_ -> calls++ },{ _,_ -> calls++ })
        }
        assertEquals(0,calls)
    }
    @Test fun emptySummaryDoesNotCallAnySender() {
        DeliveryTarget.entries.forEach { target ->
            var calls=0
            assertFalse(DeliveryDispatcher.send(batch(target,"  "),config(target),{ _,_,_ -> calls++ },{ _,_ -> calls++ }))
            assertEquals(0,calls)
        }
    }
    @Test fun onlyUncertainFeishuSendsUseUncertainState() {
        assertEquals("SEND_FAILED",DeliveryDispatcher.failureState(DeliveryTarget.LOCAL,java.io.IOException("synthetic")))
        assertEquals("SEND_FAILED",DeliveryDispatcher.failureState(DeliveryTarget.LOCAL,AppFailure(ErrorCode.LOCAL_NOTIFICATIONS_DISABLED)))
        assertEquals("UNCERTAIN",DeliveryDispatcher.failureState(DeliveryTarget.FEISHU,java.io.IOException("synthetic")))
        assertEquals("SEND_FAILED",DeliveryDispatcher.failureState(DeliveryTarget.FEISHU,RemoteFailure(ErrorCode.HTTP,400)))
    }
}
