package io.github.togls.hypertweaks.xposed

import io.github.libxposed.api.XposedModule
import io.github.togls.hypertweaks.xposed.entry.BuildConfig
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XposedMetadataTest {
    @Test
    fun metadataMatchesLibxposedApiVersion() {
        val properties = Properties().apply {
            load(resource("META-INF/xposed/module.prop"))
        }
        val expectedVersion = BuildConfig.LIBXPOSED_API_VERSION.toString()

        assertEquals(expectedVersion, properties.getProperty("minApiVersion"))
        assertEquals(expectedVersion, properties.getProperty("targetApiVersion"))
        assertEquals("false", properties.getProperty("staticScope"))
    }

    @Test
    fun javaInitListHasExactlyOneValidEntry() {
        val entries = resource("META-INF/xposed/java_init.list")
            .bufferedReader()
            .useLines { lines -> lines.filter(String::isNotBlank).toList() }

        assertEquals(listOf("io.github.togls.hypertweaks.xposed.HyperTweaksModule"), entries)
        val entryClass = Class.forName(entries.single(), false, javaClass.classLoader)
        assertTrue(XposedModule::class.java.isAssignableFrom(entryClass))
    }

    @Test
    fun scopeListMatchesFeatureCatalog() {
        val scopeEntries = resource("META-INF/xposed/scope.list")
            .bufferedReader()
            .useLines { lines -> lines.map(String::trim).filter(String::isNotBlank).toSet() }

        assertEquals(HyperTweaksHookCatalog.create().scopeEntries(), scopeEntries)
        assertTrue("org.mozilla.firefox" !in scopeEntries)
        assertTrue("com.android.systemui" !in scopeEntries)
    }

    private fun resource(path: String) = checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
        "Missing test resource: $path"
    }
}
