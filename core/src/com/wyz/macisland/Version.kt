package com.wyz.macisland.core

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String> = emptyList(),
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0
        if (prerelease.isEmpty()) return 1
        if (other.prerelease.isEmpty()) return -1
        for (index in 0 until maxOf(prerelease.size, other.prerelease.size)) {
            val left = prerelease.getOrNull(index)
            val right = other.prerelease.getOrNull(index)
            if (left == null) return -1
            if (right == null) return 1
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            val result = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (result != 0) return result
        }
        return 0
    }

    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        if (prerelease.isNotEmpty()) append("-").append(prerelease.joinToString("."))
    }

    companion object {
        private val pattern = Regex(
            "^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$",
        )

        fun parse(value: String): SemanticVersion? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            return SemanticVersion(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: return null,
                patch = match.groupValues[3].toIntOrNull() ?: return null,
                prerelease = match.groupValues[4]
                    .takeIf { it.isNotBlank() }
                    ?.split('.')
                    .orEmpty(),
            )
        }
    }
}

fun isStableVersion(value: String): Boolean = SemanticVersion.parse(value)?.prerelease?.isEmpty() == true
fun isNewerVersion(candidate: String, current: String): Boolean {
    val left = SemanticVersion.parse(candidate) ?: return false
    val right = SemanticVersion.parse(current) ?: return false
    return left > right
}
