package io.github.togls.hypertweaks.feature.keepalive.xposed

import android.content.pm.ApplicationInfo
import io.github.togls.hypertweaks.feature.keepalive.data.KeepAlivePackages
import io.github.togls.hypertweaks.feature.keepalive.policy.CriticalPackageGuard
import io.github.togls.hypertweaks.logging.api.Logger
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap

internal class ProcessIdentityResolver(
    private val logger: Logger,
) {
    fun fromArguments(
        arguments: List<Any?>,
        configuredPackages: Set<String>,
    ): String? {
        return arguments.asSequence()
            .flatMap { argument -> extractStrings(argument).asSequence() }
            .mapNotNull { candidate -> match(candidate, configuredPackages) }
            .firstOrNull()
    }

    fun fromProcessRecord(
        processRecord: Any?,
        configuredPackages: Set<String>,
    ): String? {
        if (processRecord == null || configuredPackages.isEmpty()) return null
        return processRecordCandidates(processRecord)
            .mapNotNull { candidate -> match(candidate, configuredPackages) }
            .firstOrNull()
    }

    fun processName(processRecord: Any): String? {
        return processNameCandidates(processRecord).firstOrNull()
    }

    fun pid(processRecord: Any): Int? {
        return listOf("mPid", "pid").firstNotNullOfOrNull { fieldName ->
            readFieldValue(processRecord, fieldName) as? Int
        }
    }

    private fun processRecordCandidates(processRecord: Any): Sequence<String> {
        return sequence {
            yieldAll(processNameCandidates(processRecord))
            yieldAll(applicationInfoCandidates(processRecord))
            yieldAll(packageListCandidates(processRecord))
        }
    }

    private fun processNameCandidates(processRecord: Any): List<String> {
        return listOf("processName", "mProcessName").mapNotNull { fieldName ->
            readFieldValue(processRecord, fieldName) as? String
        }
    }

    private fun applicationInfoCandidates(processRecord: Any): List<String> {
        return listOf("info", "mInfo")
            .mapNotNull { fieldName -> readFieldValue(processRecord, fieldName) as? ApplicationInfo }
            .mapNotNull(ApplicationInfo::packageName)
    }

    private fun packageListCandidates(processRecord: Any): List<String> {
        val packageList = listOf("pkgList", "mPkgList").firstNotNullOfOrNull { fieldName ->
            readFieldValue(processRecord, fieldName)
        } ?: return emptyList()
        return buildList {
            addAll(extractStrings(packageList))
            addAll(extractStrings(invokeNoArgMethod(packageList, "getPackageList")))
            addAll(extractStrings(invokeNoArgMethod(packageList, "getPackages")))
        }
    }

    private fun match(candidate: String, configuredPackages: Set<String>): String? {
        if (CriticalPackageGuard.isCritical(candidate)) return null
        val normalized = KeepAlivePackages.normalizeCandidate(candidate) ?: return null
        return normalized.takeIf(configuredPackages::contains)
    }

    private fun extractStrings(value: Any?): List<String> {
        return extractStrings(
            value = value,
            depth = 0,
            visited = Collections.newSetFromMap(IdentityHashMap()),
        )
    }

    private fun extractStrings(
        value: Any?,
        depth: Int,
        visited: MutableSet<Any>,
    ): List<String> {
        if (value == null || depth > MaxExtractDepth || !visited.add(value)) return emptyList()
        return when (value) {
            is String -> listOf(value)
            is Array<*> -> value.flatMap { item -> extractStrings(item, depth + 1, visited) }
            is Iterable<*> -> value.flatMap { item -> extractStrings(item, depth + 1, visited) }
            is Map<*, *> -> extractMapStrings(value, depth, visited)
            is ApplicationInfo -> listOfNotNull(value.packageName)
            else -> emptyList()
        }
    }

    private fun extractMapStrings(
        value: Map<*, *>,
        depth: Int,
        visited: MutableSet<Any>,
    ): List<String> {
        val keys = value.keys.flatMap { key -> extractStrings(key, depth + 1, visited) }
        val values = value.values.flatMap { item -> extractStrings(item, depth + 1, visited) }
        return keys + values
    }

    private fun readFieldValue(receiver: Any, fieldName: String): Any? {
        val field = receiver.javaClass.findField(fieldName) ?: return null
        return try {
            field.get(receiver)
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            logger.warn(
                event = "keepalive.identity.field_read.failed",
                throwable = error,
                fields = mapOf("field" to fieldName, "class" to receiver.javaClass.name),
            )
            null
        }
    }

    private fun invokeNoArgMethod(receiver: Any, methodName: String): Any? {
        val method = receiver.javaClass.findNoArgMethod(methodName) ?: return null
        return try {
            method.invoke(receiver)
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            logger.warn(
                event = "keepalive.identity.method_call.failed",
                throwable = error,
                fields = mapOf("method" to methodName, "class" to receiver.javaClass.name),
            )
            null
        }
    }

    private fun Class<*>.findField(fieldName: String): Field? {
        var currentClass: Class<*>? = this
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(fieldName).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                currentClass = currentClass.superclass
            } catch (error: Throwable) {
                error.rethrowIfFatal()
                logReflectionLookupFailure("field", fieldName, error)
                return null
            }
        }
        return null
    }

    private fun Class<*>.findNoArgMethod(methodName: String): Method? {
        var currentClass: Class<*>? = this
        while (currentClass != null) {
            val method = currentClass.declaredMethods.firstOrNull { candidate ->
                candidate.name == methodName && candidate.parameterTypes.isEmpty()
            }
            if (method != null) return makeAccessible(method)
            currentClass = currentClass.superclass
        }
        return null
    }

    private fun makeAccessible(method: Method): Method? {
        return try {
            method.apply { isAccessible = true }
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            logReflectionLookupFailure("method", method.name, error)
            null
        }
    }

    private fun logReflectionLookupFailure(
        memberType: String,
        memberName: String,
        error: Throwable,
    ) {
        logger.warn(
            event = "keepalive.identity.lookup.failed",
            throwable = error,
            fields = mapOf("member_type" to memberType, "member" to memberName),
        )
    }

    private companion object {
        private const val MaxExtractDepth = 3
    }
}
