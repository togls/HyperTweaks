package io.github.togls.hypertweaks

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.togls.hypertweaks.logging.app.AppLogRuntime
import io.github.togls.hypertweaks.service.XposedServiceStore

class HyperTweaksApp : Application(), XposedServiceHelper.OnServiceListener {

    override fun onCreate() {
        super.onCreate()
        AppLogRuntime.initialize(this)
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        XposedServiceStore.update(service)
    }

    override fun onServiceDied(service: XposedService) {
        XposedServiceStore.update(null)
    }
}
