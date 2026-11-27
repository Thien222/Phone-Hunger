package com.phonehunger.app

import android.Manifest
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
import com.phonehunger.app.battery.BatteryState
import org.junit.Rule
import org.junit.Test

class HomeFlowTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun opensHomeAndSettingsWithoutAccount() {
        compose.onNodeWithText(compose.activity.getString(R.string.try_sound)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.settings)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.thresholds)).assertExists()
    }

    @Test fun simulatedBatteryUpdatesRealHomeState() {
        compose.activity.runOnUiThread { FakeBatterySource.send(compose.activity, 20, false) }
        compose.waitUntil(timeoutMillis = 10_000) { BatteryState.snapshot.value?.level == 20 }
        compose.onNodeWithTag("battery_level").assertTextEquals("20%")
    }
}
