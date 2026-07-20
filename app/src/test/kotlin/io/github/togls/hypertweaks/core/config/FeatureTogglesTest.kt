package io.github.togls.hypertweaks.core.config

import io.github.togls.hypertweaks.core.xposed.HookFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FeatureTogglesTest {

    @Test
    fun `Google Photos location toggle should be disabled by default`() {
        assertFalse(FeatureToggles().googlePhotosLocationEnabled)
    }

    @Test
    fun `Google Photos hook feature should use its dedicated preference key`() {
        assertEquals(
            RemotePreferenceKeys.GooglePhotosLocationEnabled,
            HookFeature.GooglePhotosLocation.preferenceKey,
        )
    }
}
