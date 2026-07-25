package io.github.togls.hypertweaks.feature.googlephotos.xposed

import android.app.Application
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.core.xposed.HookInstallResult
import io.github.togls.hypertweaks.core.xposed.rethrowIfFatal
import io.github.togls.hypertweaks.feature.googlephotos.install.GooglePhotosHookInstallCoordinator
import io.github.togls.hypertweaks.feature.googlephotos.install.GooglePhotosHookInstallResult
import io.github.togls.hypertweaks.feature.googlephotos.install.GooglePhotosHookInstallStep
import io.github.togls.hypertweaks.feature.googlephotos.install.GooglePhotosInstallTarget
import io.github.togls.hypertweaks.feature.googlephotos.install.LifecycleHookInstaller
import io.github.togls.hypertweaks.feature.googlephotos.logging.GooglePhotosDiagnosticsPolicy
import io.github.togls.hypertweaks.feature.googlephotos.resolver.GooglePhotosTarget
import io.github.togls.hypertweaks.feature.googlephotos.resolver.GooglePhotosTargetResolver
import io.github.togls.hypertweaks.feature.googlephotos.resolver.ResolveDiagnostics
import io.github.togls.hypertweaks.feature.googlephotos.resolver.GooglePhotosClassNames
import io.github.togls.hypertweaks.feature.googlephotos.session.GooglePhotosMapSessionTracker
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class GooglePhotosLocationHook(
    context: HookContext,
    private val diagnosticsPolicy: GooglePhotosDiagnosticsPolicy,
    private val processNameProvider: () -> String = { Application.getProcessName() },
    private val installHooksOverride:
        ((ClassLoader) -> GooglePhotosHookInstallResult)? = null,
) {
    private val logger = GooglePhotosLocationLogger(context.log, diagnosticsPolicy)
    private val installed = AtomicBoolean(false)
    private val successfulInstall = AtomicReference<HookInstallResult.Installed?>()
    private val sessionTracker = GooglePhotosMapSessionTracker(logger)
    private val renderHook = GooglePhotosMapRenderHook(context, logger, sessionTracker)
    private val markerAnimationHook =
        GooglePhotosPreviewMarkerAnimationHook(context, logger, sessionTracker)
    private val initialPreviewSelectionHook =
        GooglePhotosInitialPreviewSelectionHook(context, logger, sessionTracker)
    private val mapLocationHook = GooglePhotosMapLocationHook(context, logger, sessionTracker)
    private val lifecycleInstaller =
        LifecycleHookInstaller(context, logger, sessionTracker, mapLocationHook)
    private val cameraUpdateHook =
        GooglePhotosCameraUpdateHook(context, logger, sessionTracker, diagnosticsPolicy)
    private val s2QueryHook =
        GooglePhotosS2QueryHook(context, logger, sessionTracker, diagnosticsPolicy)
    private val heatmapIndexHook = GooglePhotosHeatmapIndexHook(context, logger, sessionTracker)
    private val mapViewHook = GooglePhotosMapViewHook(context, logger, sessionTracker)

    fun install(classLoader: ClassLoader): HookInstallResult {
        if (processNameProvider() != GooglePhotosClassNames.PackageName) {
            return HookInstallResult.Unsupported("Google Photos main process is required")
        }
        successfulInstall.get()?.let { return it }
        if (!installed.compareAndSet(false, true)) {
            return successfulInstall.get()
                ?: HookInstallResult.Unsupported("Google Photos installation is already in progress")
        }

        return runInstall(classLoader)
    }

    private fun runInstall(classLoader: ClassLoader): HookInstallResult {
        val result = try {
            installAndAggregate(classLoader)
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            HookInstallResult.Failed(error)
        }
        if (result is HookInstallResult.Installed) {
            successfulInstall.set(result)
        } else {
            installed.set(false)
        }
        return result
    }

    private fun installAndAggregate(classLoader: ClassLoader): HookInstallResult {
        logger.installBegin()
        val targetResult = installHooksOverride?.invoke(classLoader) ?: installHooks(classLoader)
        logger.installCompleted(targetResult)
        return targetResult.toHookInstallResult()
    }

    private fun installHooks(classLoader: ClassLoader): GooglePhotosHookInstallResult {
        val resolver = GooglePhotosTargetResolver(
            classLoader,
            ResolveDiagnostics(logger::resolveDiagnostic),
        )
        val coordinator = GooglePhotosHookInstallCoordinator(
            onBegin = logger::installTargetBegin,
            onSuccess = logger::installTargetSuccess,
            onSkipped = logger::installTargetSkipped,
            onFailure = logger::installTargetFailure,
        )
        return coordinator.install(
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.LIFECYCLE) {
                lifecycleInstaller.install(
                    resolver.resolve(GooglePhotosTarget.ACTIVITY).targetClass,
                )
            },
            GooglePhotosHookInstallStep(
                target = GooglePhotosInstallTarget.MAP_VIEW,
                enabled = diagnosticsPolicy.highFrequencyProbesEnabled,
            ) {
                mapViewHook.install(resolver)
            },
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.MARKER_API) {
                renderHook.install(resolver)
            },
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.MARKER_ANIMATION) {
                markerAnimationHook.install(resolver)
            },
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.INITIAL_PREVIEW_SELECTION) {
                initialPreviewSelectionHook.install(resolver)
            },
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.MAP_LOCATION) {
                mapLocationHook.install(resolver)
            },
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.CAMERA_UPDATE) {
                cameraUpdateHook.install(resolver)
            },
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.HEATMAP_INDEX) {
                heatmapIndexHook.install(resolver)
            },
            GooglePhotosHookInstallStep(GooglePhotosInstallTarget.S2_QUERY) {
                s2QueryHook.install(resolver)
            },
        )
    }

}
