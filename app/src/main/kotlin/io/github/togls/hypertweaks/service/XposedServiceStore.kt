package io.github.togls.hypertweaks.service

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.github.libxposed.service.XposedService

object XposedServiceStore {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val currentServiceState = mutableStateOf<XposedService?>(null)

    val service: State<XposedService?>
        get() = currentServiceState

    fun update(service: XposedService?) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            currentServiceState.value = service
            return
        }

        mainHandler.post {
            currentServiceState.value = service
        }
    }
}