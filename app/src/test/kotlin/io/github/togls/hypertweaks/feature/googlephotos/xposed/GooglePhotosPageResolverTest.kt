package io.github.togls.hypertweaks.feature.googlephotos.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GooglePhotosPageResolverTest {

    @Test
    fun `resolves known Activity classes to stable page states`() {
        assertEquals(
            GooglePhotosPage.Home,
            GooglePhotosPageResolver.fromActivity(GooglePhotosClassNames.HomeActivity),
        )
        assertEquals(
            GooglePhotosPage.Collections,
            GooglePhotosPageResolver.fromActivity(GooglePhotosClassNames.CollectionsActivity),
        )
        assertEquals(
            GooglePhotosPage.MapExplore,
            GooglePhotosPageResolver.fromActivity(GooglePhotosClassNames.MapExploreActivity),
        )
    }

    @Test
    fun `does not infer a page from unknown classes`() {
        assertNull(
            GooglePhotosPageResolver.fromActivity(
                "com.google.android.apps.photos.unknown.UnknownActivity",
            ),
        )
        assertNull(
            GooglePhotosPageResolver.fromFragment(
                "com.google.android.apps.photos.unknown.UnknownFragment",
            ),
        )
    }
}
