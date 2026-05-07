package io.github.togls.hypertweaks.feature.keepalive.data

object KeepAlivePackages {
    private val packageNameRegex = Regex(
        pattern = "^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$",
    )

    fun parse(raw: String): Set<String> {
        return parseWithInvalid(raw).packages
    }

    fun parseWithInvalid(raw: String): ParseResult {
        val values =
            raw.split('\n', ',', ';', ' ', '\t').map { it.trim() }.filter { it.isNotBlank() }

        val validPackages = values.filter { packageNameRegex.matches(it) }.toSortedSet()

        val invalidValues = values.filterNot { packageNameRegex.matches(it) }.distinct()

        return ParseResult(
            packages = validPackages,
            invalidValues = invalidValues,
        )
    }

    fun format(packages: Set<String>): String {
        return packages.toSortedSet().joinToString(separator = "\n")
    }

    data class ParseResult(
        val packages: Set<String>,
        val invalidValues: List<String>,
    )
}