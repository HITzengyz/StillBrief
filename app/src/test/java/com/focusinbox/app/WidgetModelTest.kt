package com.focusinbox.app

import org.junit.Assert.*
import org.junit.Test

class WidgetModelTest {
    private val running=WidgetState("RUNNING",7,100_000,0,35,true,2,false,true)
    @Test fun actionsDependOnStateAndPendingWork() {
        assertEquals(WidgetAction.ANALYZE,running.primary)
        assertTrue(running.allows(WidgetAction.ANALYZE,7))
        assertFalse(running.copy(pending=0).primaryEnabled)
        assertFalse(running.copy(busy=true).allows(WidgetAction.ANALYZE,7))
        assertTrue(running.copy(busy=true).allows(WidgetAction.END,7))
        assertTrue(running.copy(pending=0).allows(WidgetAction.END,7))
        assertEquals(WidgetAction.RESUME,running.copy(timerState="PAUSED").primary)
        assertTrue(running.copy(timerState="PAUSED").allows(WidgetAction.RESUME,7))
        assertEquals(WidgetAction.START,running.copy(timerState="IDLE").primary)
        assertTrue(running.copy(timerState="IDLE").allows(WidgetAction.START,7))
        assertFalse(running.copy(timerState="IDLE").allows(WidgetAction.END,7))
    }
    @Test fun oldButtonsCannotChangeANewRound() {
        for(action in WidgetAction.entries) assertFalse(running.allows(action,6))
        assertFalse(running.allows(WidgetAction.START,7))
        assertFalse(running.allows(WidgetAction.RESUME,7))
    }
    @Test fun setupDoesNotSilentlyOpenAnActivityOrPreventStopping() {
        val missing=running.copy(ready=false)
        assertEquals(WidgetAction.SETUP,missing.primary)
        assertFalse(missing.allows(WidgetAction.ANALYZE,7))
        assertFalse(missing.allows(WidgetAction.SETUP,7))
        assertTrue(missing.allows(WidgetAction.END,7))
    }
    @Test fun countdownIsCalculatedFromDeadlineNotRefreshCount() {
        assertEquals(40_000L,running.remainingAt(60_000))
        assertEquals(0L,running.remainingAt(120_000))
        assertEquals(30_000L,running.copy(timerState="PAUSED",remaining=30_000).remainingAt(200_000))
        assertEquals(2_100_000L,running.copy(timerState="IDLE").remainingAt(200_000))
        assertEquals("180:00",WidgetState.time(10_800_000))
        assertEquals("00:01",WidgetState.time(1))
        assertEquals("00:00",WidgetState.time(-1))
    }
}
