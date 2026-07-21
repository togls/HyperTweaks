package io.github.togls.hypertweaks.logging.hook

import io.github.togls.hypertweaks.logging.api.LogMode

data class HookLogConfig(
    val mode: LogMode = LogMode.Default,
    val version: Long = 0L,
)
