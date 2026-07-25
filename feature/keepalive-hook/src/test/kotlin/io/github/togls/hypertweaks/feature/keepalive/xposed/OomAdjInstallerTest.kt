package io.github.togls.hypertweaks.feature.keepalive.xposed

import io.github.togls.hypertweaks.core.xposed.HookSettingsSnapshot
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode
import io.github.togls.hypertweaks.feature.keepalive.policy.KeepAlivePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Executable

class OomAdjInstallerTest {
    @Test
    fun `oom only tracks process and clamps high adj`() {
        val fixture = createFixture(KeepAliveMode.OomOnly)
        fixture.invokeSetPid()
        val oomChain = fixture.invokeSetOomAdj(900)

        assertEquals(1, fixture.processAccess.appliedCount)
        assertEquals(1, fixture.processAccess.writeCount)
        assertEquals(OomAdjInstaller.ProtectedOomAdj, oomChain.replacementArguments?.get(2))
        assertTrue(fixture.logger.events.any { event ->
            event.event == "hook.callback.transformed" &&
                event.fields["subtarget"] == "oom_adj.set_oom_adj"
        })
    }

    @Test
    fun `audit mode observes oom callbacks without mutation`() {
        val fixture = createFixture(KeepAliveMode.Audit)
        fixture.invokeSetPid()
        val oomChain = fixture.invokeSetOomAdj(900)

        assertEquals(0, fixture.processAccess.appliedCount)
        assertEquals(0, fixture.processAccess.writeCount)
        assertNull(oomChain.replacementArguments)
        assertEquals(1, oomChain.proceedCount)
        assertTrue(fixture.logger.events.any { event ->
            event.event == "hook.callback.bypassed" && event.fields["reason"] == "audit_mode"
        })
    }

    @Test
    fun `invalid oom arguments preserve original call`() {
        val fixture = createFixture(KeepAliveMode.OomOnly)
        val chain = RecordingHookChain(
            executable = fixture.setOomAdjMethod,
            thisObject = fixture.target,
            args = listOf("bad_pid", 10_000, "bad_adj"),
            originalResult = "original",
        )

        val result = fixture.engine.invoke(fixture.setOomAdjMethod, chain)

        assertEquals("original", result)
        assertEquals(1, chain.proceedCount)
        assertNull(chain.replacementArguments)
    }

    @Test
    fun `empty configuration defers oom hook installation`() {
        val installer = OomAdjInstaller(
            engine = RecordingHookEngine(),
            logger = RecordingLogger(),
            policy = policy(KeepAliveMode.OomOnly, packages = ""),
        )

        val result = installer.install(javaClass.classLoader!!)

        assertTrue(result is HookInstallationReport.Deferred)
    }

    @Test
    fun `set pid post callback failure preserves original result`() {
        val fixture = createFixture(KeepAliveMode.OomOnly)
        val chain = ThrowingPostProcessArgumentsChain(
            executable = fixture.setPidMethod,
            thisObject = fixture.target,
        )

        val result = fixture.engine.invoke(fixture.setPidMethod, chain)

        assertEquals("original", result)
        assertEquals(1, chain.proceedCount)
        assertTrue(fixture.logger.events.any { event ->
            event.event == "hook.callback.failed" &&
                event.fields["subtarget"] == "oom_adj.set_pid"
        })
    }

    private fun createFixture(mode: KeepAliveMode): OomFixture {
        val engine = RecordingHookEngine()
        val logger = RecordingLogger()
        val processAccess = FakeOomAdjProcessAccess(logger)
        val policy = policy(mode)
        val setPidMethod = ProcessRecordFixture::class.java.getDeclaredMethod(
            "setPid",
            Int::class.javaPrimitiveType,
        )
        val setOomAdjMethod = ProcessListFixture::class.java.getDeclaredMethod(
            "setOomAdj",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        val installer = OomAdjInstaller(
            engine = engine,
            logger = logger,
            policy = policy,
            processAccess = processAccess,
        )
        installer.installResolvedTargets(
            OomAdjResolution(
                setPidMethods = listOf(setPidMethod),
                setOomAdjMethods = listOf(setOomAdjMethod),
                failures = emptyMap(),
            ),
        )
        return OomFixture(
            engine = engine,
            logger = logger,
            processAccess = processAccess,
            target = ProcessRecordFixture(),
            setPidMethod = setPidMethod,
            setOomAdjMethod = setOomAdjMethod,
        )
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

    private data class OomFixture(
        val engine: RecordingHookEngine,
        val logger: RecordingLogger,
        val processAccess: FakeOomAdjProcessAccess,
        val target: ProcessRecordFixture,
        val setPidMethod: java.lang.reflect.Method,
        val setOomAdjMethod: java.lang.reflect.Method,
    ) {
        fun invokeSetPid() {
            val chain = RecordingHookChain(
                executable = setPidMethod,
                thisObject = target,
                args = listOf(ProcessId),
                originalResult = Unit,
            )
            engine.invoke(setPidMethod, chain)
            assertEquals(1, chain.proceedCount)
        }

        fun invokeSetOomAdj(adj: Int): RecordingHookChain {
            val chain = RecordingHookChain(
                executable = setOomAdjMethod,
                thisObject = null,
                args = listOf(ProcessId, 10_000, adj),
                originalResult = Unit,
            )
            engine.invoke(setOomAdjMethod, chain)
            return chain
        }
    }

    private class FakeOomAdjProcessAccess(
        logger: RecordingLogger,
    ) : OomAdjProcessAccess(logger) {
        var appliedCount: Int = 0
        var writeCount: Int = 0

        override fun applyProtectedState(processRecord: Any, protectedAdj: Int) {
            appliedCount += 1
        }

        override fun writeOomScoreAdj(pid: Int, protectedAdj: Int) {
            writeCount += 1
        }

        override fun isSameProcess(process: ProtectedProcess): Boolean = true
    }

    private class ThrowingPostProcessArgumentsChain(
        override val executable: Executable,
        override val thisObject: Any?,
    ) : HookChain {
        override val args: List<Any?>
            get() = error("simulated post-process argument failure")

        var proceedCount: Int = 0

        override fun proceed(): Any? {
            proceedCount += 1
            return "original"
        }

        override fun proceed(arguments: Array<out Any?>): Any? = proceed()
        override fun proceedWith(thisObject: Any): Any? = proceed()
        override fun proceedWith(thisObject: Any, arguments: Array<out Any?>): Any? = proceed()
    }

    @Suppress("UNUSED_PARAMETER")
    private class ProcessRecordFixture {
        val processName: String = "com.example.app"
        var mPid: Int = 0

        fun setPid(pid: Int) {
            mPid = pid
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private class ProcessListFixture {
        fun setOomAdj(pid: Int, uid: Int, adj: Int) = Unit
    }

    private companion object {
        private const val ProcessId = 321
    }
}
