package com.phonehunger.app.battery

import org.junit.Assert.*
import org.junit.Test

class BatteryEngineTest {
    private var now = 100_000L
    private fun s(level: Int, plugged: Boolean = false, full: Boolean = false) = BatterySnapshot(level, plugged, full, now)

    @Test fun crossingIsOnceAndRearmsAfterHysteresis() {
        val engine = BatteryEngine { now }
        assertNull(engine.accept(s(21)))
        assertEquals(BatteryEvent.LOW_20, engine.accept(s(19)))
        assertNull(engine.accept(s(19)))
        now += 31_000
        assertNull(engine.accept(s(23)))
        assertEquals(BatteryEvent.LOW_20, engine.accept(s(19)))
    }

    @Test fun skippedLevelsChooseMostSevere() {
        val engine = BatteryEngine { now }
        engine.accept(s(25))
        assertEquals(BatteryEvent.LOW_5, engine.accept(s(4)))
        assertNull(engine.accept(s(4)))
    }

    @Test fun connectionDebouncesAndFullDoesNotRepeat() {
        val engine = BatteryEngine { now }
        engine.accept(s(99))
        assertNull(engine.accept(s(99, true)))
        now += 1600
        assertEquals(BatteryEvent.POWER_CONNECTED, engine.accept(s(99, true)))
        assertEquals(BatteryEvent.FULL, engine.accept(s(100, true, true)))
        assertNull(engine.accept(s(100, true, true)))
    }

    @Test fun initialFullSnapshotIsSilent() {
        val engine = BatteryEngine { now }
        assertNull(engine.accept(s(100, true, true)))
        assertNull(engine.accept(s(100, true, true)))
    }

    @Test fun cooldownSuppressesSecondCrossingButRearmsLater() {
        val engine = BatteryEngine { now }
        engine.accept(s(25))
        assertEquals(BatteryEvent.LOW_20, engine.accept(s(19)))
        assertNull(engine.accept(s(9)))
        now += 31_000
        engine.accept(s(13))
        assertEquals(BatteryEvent.LOW_10, engine.accept(s(9)))
    }

    @Test fun fullRearmsOnlyAfterUnplugAndAnotherCharge() {
        val engine = BatteryEngine { now }
        engine.accept(s(99, true))
        assertEquals(BatteryEvent.FULL, engine.accept(s(100, true, true)))
        assertNull(engine.accept(s(100, true, true)))
        engine.accept(s(99, false))
        now += 1600
        engine.accept(s(99, false))
        engine.accept(s(99, true))
        now += 1600
        engine.accept(s(99, true))
        assertEquals(BatteryEvent.FULL, engine.accept(s(100, true, true)))
    }
}
