package com.phonehunger.app.core

import org.junit.Assert.*
import org.junit.Test

class QuietHoursTest {
    @Test fun crossesMidnight() {
        val settings = UserSettings(quietEnabled = true, quietStart = 22, quietEnd = 7)
        assertTrue(isQuiet(settings, 23))
        assertTrue(isQuiet(settings, 2))
        assertFalse(isQuiet(settings, 12))
    }
}
