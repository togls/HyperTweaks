package io.github.togls.hypertweaks.core.config

import io.github.togls.hypertweaks.feature.ime.data.NavBarLayoutConfig
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAliveMode

data class HyperTweaksConfig(
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