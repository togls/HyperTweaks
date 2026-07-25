package io.github.togls.hypertweaks.feature.ime.policy

internal object ImeVersionPolicy {
    const val MinimumSupportedApi = 34
    const val Android16Api = 36

    fun supportsInputMethodPackage(sdkInt: Int): Boolean {
        return sdkInt >= MinimumSupportedApi
    }
}
