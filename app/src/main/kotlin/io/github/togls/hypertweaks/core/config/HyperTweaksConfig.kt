package io.github.togls.hypertweaks.core.config

import io.github.togls.hypertweaks.feature.ime.data.NavBarLayoutConfig
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode
import io.github.togls.hypertweaks.logging.api.LogMode

data class HyperTweaksConfig(
    val logMode: LogMode = LogMode.Default,
    val features: FeatureToggles = FeatureToggles(),
    val ime: ImeConfig = ImeConfig(),
    val keepAlive: KeepAliveConfig = KeepAliveConfig(),
)

data class ImeConfig(
    val navBarLayout: NavBarLayoutConfig = NavBarLayoutConfig(),
)

data class KeepAliveConfig(
    val mode: KeepAliveMode = KeepAliveMode.Default,
    val packages: Set<String> = emptySet(),
)
