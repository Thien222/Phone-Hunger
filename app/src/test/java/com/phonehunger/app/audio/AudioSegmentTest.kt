package com.phonehunger.app.audio

import org.junit.Assert.*
import org.junit.Test

class AudioSegmentTest {
    private val clip = Clip("id", "recording", "file.m4a", 10_000)
    @Test fun validatesTrimInsideClip() {
        assertTrue(AudioSegment(clip, 1_000, 9_000).valid())
        assertFalse(AudioSegment(clip, 8_000, 8_000).valid())
        assertFalse(AudioSegment(clip, -1, 1_000).valid())
        assertFalse(AudioSegment(clip, 0, 11_000).valid())
    }
}
