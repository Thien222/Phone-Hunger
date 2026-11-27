package com.phonehunger.app.battery

data class BatterySnapshot(val level: Int, val plugged: Boolean, val full: Boolean, val timestamp: Long)

enum class BatteryEvent { LOW_50, LOW_30, LOW_20, LOW_10, LOW_5, LOW_1, POWER_CONNECTED, POWER_DISCONNECTED, FULL }

/** A process-local state machine. The first reading initializes state without making noise. */
class BatteryEngine(private val clock: () -> Long = System::currentTimeMillis) {
    private var previous: BatterySnapshot? = null
    private val fired = mutableSetOf<Int>()
    private var fullFired = false
    private var lastLowAt = Long.MIN_VALUE
    private var pendingPower: Pair<Boolean, Long>? = null

    fun accept(snapshot: BatterySnapshot, enabled: Set<Int> = setOf(20, 10, 5, 1), cooldownMs: Long = 30_000): BatteryEvent? {
        val prior = previous
        previous = snapshot
        if (prior == null) { fullFired = snapshot.plugged && snapshot.full; return null }
        if (snapshot.level !in 0..100) return null
        if (snapshot.level > prior.level) {
            listOf(50, 30, 20, 10, 5, 1).filter { snapshot.level >= it + 3 }.forEach(fired::remove)
        }
        if (prior.plugged != snapshot.plugged) {
            pendingPower = snapshot.plugged to clock()
            if (!snapshot.plugged) fullFired = false
        }
        val pending = pendingPower
        if (pending != null && pending.first == snapshot.plugged && clock() - pending.second >= 1_500) {
            pendingPower = null
            return if (snapshot.plugged) BatteryEvent.POWER_CONNECTED else BatteryEvent.POWER_DISCONNECTED
        }
        if (snapshot.plugged && snapshot.full && !prior.full && !fullFired && prior.plugged) {
            fullFired = true
            return BatteryEvent.FULL
        }
        if (snapshot.plugged) return null
        val crossed = listOf(50, 30, 20, 10, 5, 1).filter { prior.level > it && snapshot.level <= it && it !in fired }
        fired.addAll(crossed)
        val critical = crossed.lastOrNull { it in enabled } ?: return null
        if (lastLowAt != Long.MIN_VALUE && clock() - lastLowAt < cooldownMs) return null
        lastLowAt = clock()
        return when (critical) {
            50 -> BatteryEvent.LOW_50; 30 -> BatteryEvent.LOW_30; 20 -> BatteryEvent.LOW_20
            10 -> BatteryEvent.LOW_10; 5 -> BatteryEvent.LOW_5; else -> BatteryEvent.LOW_1
        }
    }
}
