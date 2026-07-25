package io.github.togls.hypertweaks.feature.keepalive.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OomAdjResolverTest {
    @Test
    fun `set pid resolver only accepts single int signature`() {
        val validMethod = OomFixture::class.java.getDeclaredMethod(
            "setPid",
            Int::class.javaPrimitiveType,
        )
        val invalidMethod = OomFixture::class.java.getDeclaredMethod(
            "setPid",
            String::class.java,
        )

        with(OomAdjResolver) {
            assertTrue(validMethod.isSupportedSetPid())
            assertFalse(invalidMethod.isSupportedSetPid())
        }
    }

    @Test
    fun `set oom adj resolver requires exactly three ints`() {
        val validMethod = OomFixture::class.java.getDeclaredMethod(
            "setOomAdj",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        val invalidMethod = OomFixture::class.java.getDeclaredMethod(
            "setOomAdj",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )

        with(OomAdjResolver) {
            assertTrue(validMethod.isSupportedSetOomAdj())
            assertFalse(invalidMethod.isSupportedSetOomAdj())
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private class OomFixture {
        fun setPid(pid: Int) = Unit
        fun setPid(pid: String) = Unit
        fun setOomAdj(pid: Int, uid: Int, adj: Int) = Unit
        fun setOomAdj(pid: Int, adj: Int) = Unit
    }
}
