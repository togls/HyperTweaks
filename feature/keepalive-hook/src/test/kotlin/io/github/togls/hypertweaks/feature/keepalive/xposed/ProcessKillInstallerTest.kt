package io.github.togls.hypertweaks.feature.keepalive.xposed

import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookSettingsSnapshot
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode
import io.github.togls.hypertweaks.feature.keepalive.policy.KeepAlivePolicy
import io.github.togls.hypertweaks.feature.keepalive.policy.ProcessKillGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Executable

class ProcessKillInstallerTest {
    @Test
    fun `full mode blocks configured package without calling original`() {
        val fixture = createFixture(KeepAliveMode.Full)
        val chain = RecordingHookChain(
            executable = fixture.method,
            thisObject = fixture.target,
            args = listOf("com.example.app"),
            originalResult = true,
        )

        val result = fixture.engine.invoke(fixture.method, chain)

        assertEquals(false, result)
        assertEquals(0, chain.proceedCount)
        assertTrue(fixture.logger.events.any { event -> event.event == "hook.callback.transformed" })
    }

    @Test
    fun `audit mode records and preserves original result`() {
        val fixture = createFixture(KeepAliveMode.Audit)
        val chain = RecordingHookChain(
            executable = fixture.method,
            thisObject = fixture.target,
            args = listOf("com.example.app"),
            originalResult = true,
        )

        val result = fixture.engine.invoke(fixture.method, chain)

        assertEquals(true, result)
        assertEquals(1, chain.proceedCount)
        assertTrue(fixture.logger.events.any { event ->
            event.event == "hook.callback.bypassed" && event.fields["reason"] == "audit_mode"
        })
    }

    @Test
    fun `argument failure calls original exactly once`() {
        val fixture = createFixture(KeepAliveMode.Full)
        val chain = ThrowingArgumentsChain(fixture.method, fixture.target)

        val result = fixture.engine.invoke(fixture.method, chain)

        assertEquals(true, result)
        assertEquals(1, chain.proceedCount)
        assertTrue(fixture.logger.events.any { event ->
            event.event == "hook.callback.failed" &&
                event.fields["subtarget"] == ProcessKillGroup.AmsBackground.persistedName
        })
    }

    @Test
    fun `empty configuration defers hook installation`() {
        val engine = RecordingHookEngine()
        val installer = ProcessKillInstaller(
            engine = engine,
            logger = RecordingLogger(),
            policy = policy(KeepAliveMode.Full, packages = ""),
        )

        val result = installer.install(javaClass.classLoader!!)

        assertTrue(result is HookInstallationReport.Deferred)
        assertTrue(engine.interceptors.isEmpty())
    }

    @Test
    fun `single hook installation failure is reported without throwing`() {
        val method = KillFixture::class.java.getDeclaredMethod(
            "killBackgroundProcesses",
            String::class.java,
        )
        val engine = RecordingHookEngine(failingExecutables = setOf(method))
        val installer = ProcessKillInstaller(
            engine = engine,
            logger = RecordingLogger(),
            policy = policy(KeepAliveMode.Full),
        )

        val result = installer.installResolvedTargets(
            listOf(
                ProcessKillTarget(
                    method = method,
                    group = ProcessKillGroup.AmsBackground,
                    packageFromReceiver = false,
                ),
            ),
        )

        assertEquals(0, result.installedTargets.size)
        assertEquals(1, result.failedTargets.size)
        assertTrue(engine.interceptors.isEmpty())
    }

    private fun createFixture(mode: KeepAliveMode): InstallerFixture {
        val engine = RecordingHookEngine()
        val logger = RecordingLogger()
        val target = KillFixture()
        val method = KillFixture::class.java.getDeclaredMethod(
            "killBackgroundProcesses",
            String::class.java,
        )
        val installer = ProcessKillInstaller(
            engine = engine,
            logger = logger,
            policy = policy(mode),
        )
        installer.installResolvedTargets(
            listOf(
                ProcessKillTarget(
                    method = method,
                    group = ProcessKillGroup.AmsBackground,
                    packageFromReceiver = false,
                ),
            ),
        )
        return InstallerFixture(engine, logger, target, method)
    }

    private fun policy(
        mode: KeepAliveMode,
        packages: String = "com.example.app",
    ): KeepAlivePolicy {
        return KeepAlivePolicy(
            HookSettingsSnapshot(
                keepAliveMode = mode.value,
                keepAlivePackages = packages,
            ),
        )
    }

    private data class InstallerFixture(
        val engine: RecordingHookEngine,
        val logger: RecordingLogger,
        val target: KillFixture,
        val method: java.lang.reflect.Method,
    )

    private class ThrowingArgumentsChain(
        override val executable: Executable,
        override val thisObject: Any?,
    ) : HookChain {
        override val args: List<Any?>
            get() = error("unexpected argument shape")

        var proceedCount: Int = 0

        override fun proceed(): Any? {
            proceedCount += 1
            return true
        }

        override fun proceed(arguments: Array<out Any?>): Any? = proceed()
        override fun proceedWith(thisObject: Any): Any? = proceed()
        override fun proceedWith(thisObject: Any, arguments: Array<out Any?>): Any? = proceed()
    }

    @Suppress("UNUSED_PARAMETER")
    private class KillFixture {
        fun killBackgroundProcesses(packageName: String): Boolean = true
    }
}
