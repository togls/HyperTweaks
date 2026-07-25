package io.github.togls.hypertweaks.core.config

data class FeatureToggles(
    val systemServerFeaturesEnabled: Boolean = true,
    val imeEnabled: Boolean = false,
    val googlePhotosLocationEnabled: Boolean = false,
    val keepAliveEnabled: Boolean = false,
)
