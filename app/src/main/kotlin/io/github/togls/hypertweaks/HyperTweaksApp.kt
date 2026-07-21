package io.github.togls.hypertweaks

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.togls.hypertweaks.logging.app.AppLogRuntime
import io.github.togls.hypertweaks.service.HookLogRecoveryRequester
import io.github.togls.hypertweaks.service.XposedServiceStore

class HyperTweaksApp : Application(), XposedServiceHelper.OnServiceListener {

    override fun onCreate() {
        super.onCreate()
        AppLogRuntime.initialize(this)
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        XposedServiceStore.update(service)
        AppLogRuntime.runWhenDatabaseReady { requestHookLogRecovery(service) }
    }

    override fun onServiceDied(service: XposedService) {
        XposedServiceStore.update(null)
    }

    private fun requestHookLogRecovery(service: XposedService) {
        HookLogRecoveryRequester.request(service)
            .onSuccess { generation ->
                AppLogRuntime.logger.info(
                    event = "provider.recovery.requested",
                    fields = mapOf("recovery_generation" to generation.toString()),
                )
            }
            .onFailure { error ->
                AppLogRuntime.logger.warn(
                    event = "provider.recovery.request.failed",
                    message = "Unable to notify Hook processes that the log bridge is ready",
                    throwable = error,
                )
            }
    }
}
