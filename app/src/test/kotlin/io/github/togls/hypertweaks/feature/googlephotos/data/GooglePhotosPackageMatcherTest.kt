package io.github.togls.hypertweaks.feature.googlephotos.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePhotosPackageMatcherTest {

    @Test
    fun `matches should only accept Google Photos`() {
        assertTrue(
            GooglePhotosPackageMatcher.matches(
                GooglePhotosPackageMatcher.GooglePhotosPackage,
            ),
        )
        assertFalse(GooglePhotosPackageMatcher.matches("com.google.android.gms"))
        assertFalse(GooglePhotosPackageMatcher.matches("com.google.android.gsf"))
        assertFalse(GooglePhotosPackageMatcher.matches("com.google.android.apps.maps"))
    }
}
