package io.github.togls.hypertweaks.logging.hook

import android.content.SharedPreferences
import io.github.libxposed.api.XposedModule
import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.api.Logger
import io.github.togls.hypertweaks.logging.api.LoggerFactory
import io.github.togls.hypertweaks.logging.api.LogMode
import io.github.togls.hypertweaks.logging.api.LogSink
import io.github.togls.hypertweaks.logging.api.LogSource
import io.github.togls.hypertweaks.logging.hook.sink.AndroidFallbackSink
import io.github.togls.hypertweaks.logging.hook.sink.HookLogBridgeSink
import io.github.togls.hypertweaks.logging.hook.sink.XposedLogSink

class HookLogRuntime private constructor(
    val rootLogger: Logger,
    private val configSource: HookLogConfigSource,
    private val dispatcher: HookBatchDispatcher,
) : AutoCloseable {
    override fun close() {
        configSource.close()
        dispatcher.close()
    }

    companion object {
        fun createSafe(
            module: XposedModule,
            preferencesProvider: () -> SharedPreferences,
            modeKey: String,
            versionKey: String,
            transport: HookBatchTransport,
            context: LogContext = LogContext(),
        ): HookLogBootstrap {
            return runCatching {
                create(module, preferencesProvider, modeKey, versionKey, transport, context)
            }.fold(
                onSuccess = { runtime -> HookLogBootstrap(runtime.rootLogger, runtime) },
                onFailure = { error -> createFallback(module, context, error) },
            )
        }

        fun create(
            module: XposedModule,
            preferencesProvider: () -> SharedPreferences,
            modeKey: String,
            versionKey: String,
            transport: HookBatchTransport,
            context: LogContext = LogContext(),
        ): HookLogRuntime {
            val configSource = HookLogConfigSource(preferencesProvider, modeKey, versionKey)
            val configResult = configSource.start()
            val dispatcher = HookBatchDispatcher(HookEventBuffer(), transport).apply { start() }
            val logger = HookLogger.create(
                configSource = configSource,
                xposedSink = XposedLogSink(module),
                fallbackSink = AndroidFallbackSink(),
                bridgeSink = HookLogBridgeSink(dispatcher),
                context = context,
            )
            logConfigurationResult(logger, configResult)
            return HookLogRuntime(logger, configSource, dispatcher)
        }

        private fun logConfigurationResult(
            logger: Logger,
            result: Result<HookLogConfig>,
        ) {
            result.onSuccess { config ->
                logger.info(
                    event = "config.load.succeeded",
                    fields = mapOf("mode" to config.mode.persistedValue),
                )
            }.onFailure { error ->
                logger.warn(
                    event = "config.load.failed",
                    message = "Falling back to BASIC logging",
                    throwable = error,
                )
            }
        }

        private fun createFallback(
            module: XposedModule,
            context: LogContext,
            error: Throwable,
        ): HookLogBootstrap {
            val sink = HookDispatchSink(
                xposedSink = XposedLogSink(module),
                fallbackSink = AndroidFallbackSink(),
                bridgeSink = LogSink { },
            )
            val logger = LoggerFactory.create(
                source = LogSource.HOOK,
                modeProvider = { LogMode.BASIC },
                sink = sink,
                context = context,
            )
            logger.error("logging.runtime.failed", "Using local fallback logger", error)
            return HookLogBootstrap(logger, null)
        }
    }
}

data class HookLogBootstrap(
    val rootLogger: Logger,
    val runtime: HookLogRuntime?,
)
