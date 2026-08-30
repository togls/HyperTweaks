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

    @Test
    fun `android 17 expands gesture inset to caption bar height`() {
        assertEquals(
            135,
            resolveNavigationBarBottomInset(
                drawsNavBar = true,
                originalBottom = 56,
                captionBarHeight = 135,
            ),
        )
    }

    @Test
    fun `navigation bar inset policy preserves safe framework values`() {
        assertEquals(
            180,
            resolveNavigationBarBottomInset(
                drawsNavBar = true,
                originalBottom = 180,
                captionBarHeight = 135,
            ),
        )
        assertEquals(
            56,
            resolveNavigationBarBottomInset(
                drawsNavBar = false,
                originalBottom = 56,
                captionBarHeight = 135,
            ),
        )
        assertEquals(
            56,
            resolveNavigationBarBottomInset(
                drawsNavBar = true,
                originalBottom = 56,
                captionBarHeight = null,
            ),
        )
        assertNull(
            resolveNavigationBarBottomInset(
                drawsNavBar = true,
                originalBottom = null,
                captionBarHeight = 135,
            ),
        )
    }
}
