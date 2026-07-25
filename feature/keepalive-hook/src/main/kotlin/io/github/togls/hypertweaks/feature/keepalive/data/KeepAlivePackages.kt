package io.github.togls.hypertweaks.feature.keepalive.data

import io.github.togls.hypertweaks.feature.keepalive.policy.CriticalPackageGuard
import java.util.Locale

object KeepAlivePackages {
    private val packageNameRegex = Regex(
        pattern = "^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$",
    )

    fun parse(raw: String): Set<String> {
        return parseWithInvalid(raw).packages
    }

    fun parseWithInvalid(raw: String): ParseResult {
        val values = splitValues(raw)
        val normalizedValues = values.map { value -> value to normalizeCandidate(value) }
        val validPackages = normalizedValues
            .mapNotNull { (_, normalized) -> normalized }
            .filterNot(CriticalPackageGuard::isCritical)
            .toSortedSet()
        val invalidValues = normalizedValues
            .filter { (_, normalized) ->
                normalized == null || CriticalPackageGuard.isCritical(normalized)
            }
            .map { (original, _) -> original }
            .distinct()

        return ParseResult(
            packages = validPackages,
            invalidValues = invalidValues,
        )
    }

    fun format(packages: Set<String>): String {
        return packages
            .mapNotNull(::normalizeCandidate)
            .filterNot(CriticalPackageGuard::isCritical)
            .toSortedSet()
            .joinToString(separator = "\n")
    }

    fun normalizeCandidate(value: String?): String? {
        val normalized = value
            ?.trim()
            ?.substringBefore(':')
            ?.lowercase(Locale.ROOT)
            ?.takeIf(String::isNotEmpty)
            ?: return null
        return normalized.takeIf(packageNameRegex::matches)
    }

    private fun splitValues(raw: String): List<String> {
        return raw.split('\n', ',', ';', ' ', '\t')
            .map(String::trim)
            .filter(String::isNotBlank)
    }

    data class ParseResult(
        val packages: Set<String>,
        val invalidValues: List<String>,
    )
}
