package io.github.togls.hypertweaks.feature.googlephotos.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePhotosInitialPreviewSelectionHookTest {
    @Test
    fun resolvesSelectionAndBoundsUpdateBindings() {
        val binding = InitialPreviewSelectionBindingResolver(
            mediaClass = FakeMedia::class.java,
        ).resolve(
            selectionClass = FakeSelection::class.java,
            previewControllerClass = FakePreviewController::class.java,
        )

        assertEquals("b", binding.selectionMethod.name)
        assertEquals("d", binding.currentSelectionField.name)
        assertEquals("v", binding.boundsUpdateMethod.name)
    }

    @Test
    fun preservesOnlyFirstInitialSelectionClearDuringBoundsUpdate() {
        val policy = InitialPreviewSelectionPolicy()

        assertTrue(policy.shouldPreserve(7L, true, true, true, true))
        assertFalse(policy.shouldPreserve(7L, true, true, true, true))
    }

    @Test
    fun keepsUnrelatedSelectionChangesUntouched() {
        val policy = InitialPreviewSelectionPolicy()

        assertFalse(policy.shouldPreserve(null, true, true, true, true))
        assertFalse(policy.shouldPreserve(8L, false, true, true, true))
        assertFalse(policy.shouldPreserve(8L, true, false, true, true))
        assertFalse(policy.shouldPreserve(8L, true, true, false, true))
        assertFalse(policy.shouldPreserve(8L, true, true, true, false))
    }

    private class FakeSelection {
        @JvmField
        var d: FakeMedia? = null

        fun b(@Suppress("UNUSED_PARAMETER") media: FakeMedia?) = Unit

        fun b(@Suppress("UNUSED_PARAMETER") value: String?) = Unit
    }

    private class FakeMedia

    private class FakePreviewController {
        fun v() = Unit
        fun v(@Suppress("UNUSED_PARAMETER") value: String?) = Unit
    }
}
