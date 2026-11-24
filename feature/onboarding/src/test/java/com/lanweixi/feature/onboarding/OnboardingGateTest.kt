package com.lanweixi.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P201：首启判定的三种情况。
 */
class OnboardingGateTest {

    @Test
    fun `already done means straight to the main screen`() {
        assertEquals(
            OnboardingGate.Decision.AlreadyDone,
            OnboardingGate.decide(done = true, hasApiKey = false),
        )
    }

    @Test
    fun `existing key marks an upgraded user and does not bother them`() {
        assertEquals(
            OnboardingGate.Decision.SkipButMarkDone,
            OnboardingGate.decide(done = false, hasApiKey = true),
        )
    }

    @Test
    fun `fresh install with no key shows the onboarding`() {
        assertEquals(
            OnboardingGate.Decision.ShowOnboarding,
            OnboardingGate.decide(done = false, hasApiKey = false),
        )
    }
}
