package io.github.togls.hypertweaks.xposed.util

import android.util.Log
import io.github.libxposed.api.XposedModule

object HookLog {
    private const val TAG = "HyperTweaks"

    fun i(
        module: XposedModule,
        message: String,
    ) {
        module.log(Log.INFO, TAG, message)
    }

    fun w(
        module: XposedModule,
        message: String,
        error: Throwable? = null,
    ) {
        if (error == null) {
            module.log(Log.WARN, TAG, message)
            return
        }

        module.log(Log.WARN, TAG, message, error)
    }

    fun e(
        module: XposedModule,
        message: String,
        error: Throwable? = null,
    ) {
        if (error == null) {
            module.log(Log.ERROR, TAG, message)
            return
        }

        module.log(Log.ERROR, TAG, message, error)
    }
}