package io.github.togls.hypertweaks.feature.keepalive.data

import org.junit.Assert.assertEquals
import org.junit.Test

class KeepAlivePackagesTest {

    @Test
    fun `parse should split by common separators`() {
        val raw = """
            org.mozilla.firefox
            com.example.app,com.example.service;com.test.demo com.foo.bar
        """.trimIndent()

        val result = KeepAlivePackages.parse(raw)

        assertEquals(
            setOf(
                "com.example.app",
                "com.example.service",
                "com.foo.bar",
                "com.test.demo",
                "org.mozilla.firefox",
            ),
            result,
        )
    }

    @Test
    fun `parse should remove duplicated packages and sort values`() {
        val raw = """
            org.mozilla.firefox
            com.example.app
            org.mozilla.firefox
        """.trimIndent()

        val result = KeepAlivePackages.parse(raw)

        assertEquals(
            setOf(
                "com.example.app",
                "org.mozilla.firefox",
            ),
            result,
        )
    }

    @Test
    fun `parseWithInvalid should return invalid values`() {
        val raw = """
            org.mozilla.firefox
            1invalid.package
            invalid
            com.example.app
            com..broken
        """.trimIndent()

        val result = KeepAlivePackages.parseWithInvalid(raw)

        assertEquals(
            setOf(
                "com.example.app",
                "org.mozilla.firefox",
            ),
            result.packages,
        )

        assertEquals(
            listOf(
                "1invalid.package",
                "invalid",
                "com..broken",
            ),
            result.invalidValues,
        )
    }

    @Test
    fun `format should sort packages and join by newline`() {
        val result = KeepAlivePackages.format(
            setOf(
                "org.mozilla.firefox",
                "com.example.app",
                "com.foo.bar",
            ),
        )

        assertEquals(
            """
                com.example.app
                com.foo.bar
                org.mozilla.firefox
            """.trimIndent(),
            result,
        )
    }
}