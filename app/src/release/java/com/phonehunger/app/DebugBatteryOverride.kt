package com.phonehunger.app

import com.phonehunger.app.battery.BatterySnapshot

object DebugBatteryOverride {
    val snapshot: BatterySnapshot? get() = null
}
