package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal class GooglePhotosInitialPreviewSelectionHook(
    context: HookContext,
    private val logger: GooglePhotosLocationLogger,
    private val sessionTracker: GooglePhotosMapSessionTracker,
) {
    private val engine = context.engine
    private val boundsUpdateDepth = ThreadLocal.withInitial { 0 }
    private val policy = InitialPreviewSelectionPolicy()
    private lateinit var binding: InitialPreviewSelectionBinding

    fun install(classLoader: ClassLoader) {
        val mediaClass = classLoader.loadClass(MediaClassName)
        val selectionClass = classLoader.loadClass(SelectionClassName)
        val previewControllerClass = classLoader.loadClass(PreviewControllerClassName)
        binding = InitialPreviewSelectionBindingResolver(mediaClass).resolve(
            selectionClass,
            previewControllerClass,
        )
        installBoundsUpdateInterceptor()
        installSelectionInterceptor()
    }

    private fun installBoundsUpdateInterceptor() {
        binding.boundsUpdateMethod.isAccessible = true
        engine.hook(binding.boundsUpdateMethod) { chain ->
                boundsUpdateDepth.set(currentBoundsUpdateDepth() + 1)
                try {
                    chain.proceed()
                } finally {
                    val remainingDepth = currentBoundsUpdateDepth() - 1
                    if (remainingDepth == 0) boundsUpdateDepth.remove()
                    else boundsUpdateDepth.set(remainingDepth)
                }
            }
    }

    private fun installSelectionInterceptor() {
        binding.selectionMethod.isAccessible = true
        binding.currentSelectionField.isAccessible = true
        engine.hook(binding.selectionMethod, ::interceptSelectionChange)
    }

    private fun interceptSelectionChange(chain: HookChain): Any? {
        val session = sessionTracker.currentSession() ?: return chain.proceed()
        val shouldPreserve = policy.shouldPreserve(
            sessionId = session.sessionId,
            singlePhotoEntry = session.hostActivity.intent?.hasExtra(InitialMediaExtra) == true,
            boundsUpdateActive = currentBoundsUpdateDepth() > 0,
            clearingSelection = chain.args.singleOrNull() == null,
            currentSelectionPresent = readCurrentSelection(chain.thisObject),
        )
        if (!shouldPreserve) return chain.proceed()
        logger.initialPreviewSelectionPreserved(session.toProbeLogSnapshot())
        return null
    }

    private fun readCurrentSelection(receiver: Any?): Boolean {
        if (receiver == null) return false
        return runCatching { binding.currentSelectionField.get(receiver) != null }
            .onFailure { error -> logger.warning("initial_preview_selection_read_current", error) }
            .getOrDefault(false)
    }

    private fun currentBoundsUpdateDepth(): Int = boundsUpdateDepth.get() ?: 0

    private companion object {
        private const val SelectionClassName = "atxa"
        private const val MediaClassName = "bsdv"
        private const val PreviewControllerClassName = "ahdq"
        private const val InitialMediaExtra = "extra_initial_media"
    }
}

internal data class InitialPreviewSelectionBinding(
    val selectionMethod: Method,
    val currentSelectionField: Field,
    val boundsUpdateMethod: Method,
)

internal class InitialPreviewSelectionBindingResolver(
    private val mediaClass: Class<*>,
) {
    fun resolve(
        selectionClass: Class<*>,
        previewControllerClass: Class<*>,
    ): InitialPreviewSelectionBinding {
        val selectionMethod = selectionClass.getDeclaredMethod(SelectionMethodName, mediaClass)
        check(selectionMethod.returnType == Void.TYPE) {
            "Initial preview selection method must return void"
        }
        val currentSelectionField = selectionClass.getDeclaredField(CurrentSelectionFieldName)
        check(currentSelectionField.type == mediaClass && !Modifier.isStatic(currentSelectionField.modifiers)) {
            "Initial preview current-selection field is incompatible"
        }
        val boundsUpdateMethod = previewControllerClass.getDeclaredMethod(BoundsUpdateMethodName)
        check(boundsUpdateMethod.returnType == Void.TYPE) {
            "Initial preview bounds-update method must return void"
        }
        return InitialPreviewSelectionBinding(
            selectionMethod,
            currentSelectionField,
            boundsUpdateMethod,
        )
    }

    private companion object {
        private const val SelectionMethodName = "b"
        private const val CurrentSelectionFieldName = "d"
        private const val BoundsUpdateMethodName = "v"
    }
}

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
        if (
            sessionId == null ||
            !singlePhotoEntry ||
            !boundsUpdateActive ||
            !clearingSelection ||
            !currentSelectionPresent
        ) {
            return false
        }
        if (preservedSessionId == sessionId) return false
        preservedSessionId = sessionId
        return true
    }
}
