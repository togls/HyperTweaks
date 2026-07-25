package io.github.togls.hypertweaks.feature.googlephotos.policy

internal class InitialPreviewSelectionPolicy {
    private var preservedSessionId: Long? = null

    @Synchronized
    fun shouldPreserve(
        sessionId: Long?,
        singlePhotoEntry: Boolean,
        boundsUpdateActive: Boolean,
        clearingSelection: Boolean,
        currentSelectionPresent: Boolean,
    ): Boolean {
        if (!eligible(
                sessionId,
                singlePhotoEntry,
                boundsUpdateActive,
                clearingSelection,
                currentSelectionPresent,
            )
        ) {
            return false
        }
        if (preservedSessionId == sessionId) return false
        preservedSessionId = sessionId
        return true
    }

    private fun eligible(
        sessionId: Long?,
        singlePhotoEntry: Boolean,
        boundsUpdateActive: Boolean,
        clearingSelection: Boolean,
        currentSelectionPresent: Boolean,
    ): Boolean {
        return sessionId != null &&
            singlePhotoEntry &&
            boundsUpdateActive &&
            clearingSelection &&
            currentSelectionPresent
    }
}
