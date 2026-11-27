package com.netwatch.domain.calculator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostSwitchSettlerTest {
    @Test
    fun discardsOneSampleAfterSwitchThenResumes() {
        val settler = PostSwitchSettler(extraSamples = 1)
        assertFalse(settler.consumePending())
        settler.onDefaultDataSwitch()
        assertTrue(settler.consumePending())
        assertFalse(settler.consumePending())
    }
}
