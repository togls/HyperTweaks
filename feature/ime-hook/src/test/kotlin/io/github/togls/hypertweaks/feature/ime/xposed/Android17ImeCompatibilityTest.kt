package io.github.togls.hypertweaks.feature.ime.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Android17ImeCompatibilityTest {
    @Test
    fun `unsupported ime uses framework render capability`() {
        assertFalse(resolveHideImeRenderReplacement(isImeSupported = false, canImeRender = true)!!)
        assertTrue(resolveHideImeRenderReplacement(isImeSupported = false, canImeRender = false)!!)
    }

    @Test
    fun `supported or unresolved ime preserves framework result`() {
        assertNull(resolveHideImeRenderReplacement(isImeSupported = true, canImeRender = true))
        assertNull(resolveHideImeRenderReplacement(isImeSupported = null, canImeRender = true))
        assertNull(resolveHideImeRenderReplacement(isImeSupported = false, canImeRender = null))
    }

    @Test
    fun `android 17 prefers controller class without impl suffix`() {
        assertEquals(
            "android.inputmethodservice.NavigationBarController",
            navigationBarControllerClassNames(37).first(),
        )
    }

    @Test
    fun `older android prefers legacy controller implementation`() {
        assertEquals(
            "android.inputmethodservice.NavigationBarController\$Impl",
            navigationBarControllerClassNames(36).first(),
        )
    }
}
