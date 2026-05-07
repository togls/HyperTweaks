package io.github.togls.hypertweaks.core.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImePackageMatcherTest {

    @Test
    fun `matches should return true for supported IME packages`() {
        assertTrue(ImePackageMatcher.matches("com.google.android.inputmethod.latin"))
        assertTrue(ImePackageMatcher.matches("com.baidu.input_mi"))
        assertTrue(ImePackageMatcher.matches("com.sohu.inputmethod.sogou.xiaomi"))
        assertTrue(ImePackageMatcher.matches("com.iflytek.inputmethod.miui"))
        assertTrue(ImePackageMatcher.matches("com.tencent.wetype"))
        assertTrue(ImePackageMatcher.matches("keepass2android.keepass2android"))
    }

    @Test
    fun `matches should return false for non IME packages`() {
        assertFalse(ImePackageMatcher.matches("org.mozilla.firefox"))
        assertFalse(ImePackageMatcher.matches("com.android.systemui"))
        assertFalse(ImePackageMatcher.matches("io.github.togls.hypertweaks"))
    }
}