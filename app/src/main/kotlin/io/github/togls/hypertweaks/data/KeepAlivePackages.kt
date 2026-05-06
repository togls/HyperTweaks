package io.github.togls.hypertweaks.data

object KeepAlivePackages {

    private val packageNameRegex = Regex(
        pattern = "^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$",
    )

    fun parse(raw: String): Set<String> {
        return raw
            .split('\n', ',', ';', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { packageNameRegex.matches(it) }
            .toSortedSet()
    }

    fun format(packages: Set<String>): String {
        return packages
            .toSortedSet()
            .joinToString(separator = "\n")
    }
}