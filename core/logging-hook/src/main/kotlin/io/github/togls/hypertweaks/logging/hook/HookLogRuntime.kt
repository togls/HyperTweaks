package io.github.togls.hypertweaks.logging.hook

import android.content.SharedPreferences
import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.api.Logger
import io.github.togls.hypertweaks.logging.api.LoggerFactory
import io.github.togls.hypertweaks.logging.api.LogMode
import io.github.togls.hypertweaks.logging.api.LogSink
import io.github.togls.hypertweaks.logging.api.LogSource
import io.github.togls.hypertweaks.logging.hook.sink.AndroidFallbackSink
import io.github.togls.hypertweaks.logging.hook.sink.HookLogBridgeSink

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
            xposedSink: LogSink,
            onListenerFailure: (Throwable) -> Unit,
            preferencesProvider: () -> SharedPreferences,
            modeKey: String,
            versionKey: String,
            recoveryKey: String,
            transport: HookBatchTransport,
            context: LogContext = LogContext(),
        ): HookLogBootstrap {
            return runCatching {
                create(
                    xposedSink,
                    onListenerFailure,
                    preferencesProvider,
                    modeKey,
                    versionKey,
                    recoveryKey,
                    transport,
                    context,
                )
            }.fold(
                onSuccess = { runtime -> HookLogBootstrap(runtime.rootLogger, runtime) },
                onFailure = { error -> createFallback(xposedSink, context, error) },
            )
        }

        fun create(
            xposedSink: LogSink,
            onListenerFailure: (Throwable) -> Unit,
            preferencesProvider: () -> SharedPreferences,
            modeKey: String,
            versionKey: String,
            recoveryKey: String,
            transport: HookBatchTransport,
            context: LogContext = LogContext(),
        ): HookLogRuntime {
            val dispatcher = HookBatchDispatcher(HookEventBuffer(), transport)
            val configSource = HookLogConfigSource(
                preferencesProvider = preferencesProvider,
                modeKey = modeKey,
                versionKey = versionKey,
                recoveryKey = recoveryKey,
                onRecoveryRequested = dispatcher::requestFlush,
                onListenerFailure = onListenerFailure,
            )
            return try {
                dispatcher.start()
                val configResult = configSource.start()
                val logger = HookLogger.create(
                    configSource = configSource,
                    xposedSink = xposedSink,
                    fallbackSink = AndroidFallbackSink(),
                    bridgeSink = HookLogBridgeSink(dispatcher),
                    context = context,
                )
                logConfigurationResult(logger, configResult)
                HookLogRuntime(logger, configSource, dispatcher)
            } catch (error: Throwable) {
                configSource.close()
                dispatcher.close()
                throw error
            }
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
            xposedSink: LogSink,
            context: LogContext,
            error: Throwable,
        ): HookLogBootstrap {
            val sink = HookDispatchSink(
                xposedSink = xposedSink,
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
