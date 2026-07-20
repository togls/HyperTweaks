package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.core.xposed.util.HookLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class GooglePhotosLocationLogger(
    private val log: HookLog,
) {
    private val errorCounts = ConcurrentHashMap<String, AtomicInteger>()

    fun hookInstalled() {
        log.i("GooglePhotosLocation: hook installed")
    }

    fun renderHookInstalled(strategy: String) {
        log.i(
            message = "GooglePhotosLocation: render hook installed",
            "strategy" to strategy,
        )
    }

    fun scopeBound(source: MapEntrySource) {
        log.i(
            message = "GooglePhotosMapScope: bound",
            "source" to source,
        )
    }

    fun scopeActivated() {
        log.i("GooglePhotosMapScope: activated")
    }

    fun scopeDeactivated() {
        log.i("GooglePhotosMapScope: deactivated")
    }

    fun conversionApplied(
        target: String,
        convertedCount: Int,
    ) {
        log.i(
            message = "GooglePhotosLocation: conversion applied",
            "target" to target,
            "count" to convertedCount,
        )
    }

    fun warning(
        operation: String,
        error: Throwable? = null,
    ) {
        val errorType = error?.javaClass?.name ?: "unknown"
        val key = "$operation:$errorType"
        val count = errorCounts.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
        if (count > MaximumLogsPerErrorType) {
            return
        }

        log.w(
            message = "GooglePhotosLocation: operation failed",
            error = error,
            "operation" to operation,
            "errorType" to errorType,
        )
    }

    private companion object {
        private const val MaximumLogsPerErrorType = 3
    }
}
