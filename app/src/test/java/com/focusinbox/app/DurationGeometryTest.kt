package com.focusinbox.app

import org.junit.Assert.*
import org.junit.Test

class DurationGeometryTest {
    @Test fun snappingUsesFiveMinuteNodesAndKeepsBothEndpoints() {
        assertEquals(1,DurationGeometry.snap(-20f)); assertEquals(180,DurationGeometry.snap(500f))
        assertEquals(25,DurationGeometry.snap(26f)); assertEquals(30,DurationGeometry.snap(28f))
        val snapped=(1..1800).map { DurationGeometry.snap(it/10f) }
        assertTrue(snapped.all { it==1 || it%5==0 })
        assertTrue(snapped.zipWithNext().all { (a,b) -> a<=b })
    }
    @Test fun oneGestureAcrossEndGapNeverWrapsToMinimumAndCanReturn() {
        val drag=DurationGeometry.Drag(60f,180)
        assertEquals(180,drag.value())
        listOf(85f,115f,130f).forEach { assertEquals(180,drag.move(it)) }
        assertEquals(180,drag.move(100f)); assertEquals(180,drag.move(65f))
        assertEquals(175,drag.move(DurationGeometry.angle(175)%360))
        // A new deliberate touch can select the start after the old gesture ends.
        assertEquals(1,DurationGeometry.Drag(DurationGeometry.angle(1),180).value())
    }
    @Test fun startGapIsAlsoClampedUntilReturningToItsArc() {
        val drag=DurationGeometry.Drag(DurationGeometry.angle(1),1)
        listOf(100f,75f,50f,100f).forEach { assertEquals(1,drag.move(it)) }
        assertEquals(5,drag.move(DurationGeometry.angle(5)))
        assertEquals(1,DurationGeometry.Drag(90f,25).value())
        assertEquals(180,DurationGeometry.Drag(90f,150).value())
    }
    @Test fun accessibilityStepsAndRemainingInitialValueRespectMinutePrecision() {
        assertEquals(5,DurationGeometry.nextTick(1,1)); assertEquals(1,DurationGeometry.nextTick(5,-1))
        assertEquals(30,DurationGeometry.nextTick(27,1)); assertEquals(25,DurationGeometry.nextTick(27,-1))
        assertEquals(180,DurationGeometry.nextTick(180,1)); assertEquals(1,DurationGeometry.nextTick(1,-1))
        assertEquals(8,DurationGeometry.remainingMinutes(7*60000L+15000))
        assertEquals(1,DurationGeometry.remainingMinutes(15000)); assertEquals(180,DurationGeometry.remainingMinutes(300*60000L))
    }
}
