package com.wyz.lidsleepx.app.platform

import com.wyz.lidsleepx.core.APP_VERSION
import com.wyz.lidsleepx.core.SemanticVersion
import com.wyz.lidsleepx.core.UpdateChecker
import com.wyz.lidsleepx.core.UpdateInfo
import java.net.HttpURLConnection
import java.net.URI

class GithubUpdateChecker(
    private val endpoint: String = "https://github.com/wises231/kmp-LidSleepX/releases",
    private val connectionFactory: (String) -> HttpURLConnection = { value ->
        URI(value).toURL().openConnection() as HttpURLConnection
    },
) : UpdateChecker {
    override fun latestRelease(): UpdateInfo? = runCatching {
        val connection = connectionFactory(endpoint)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "text/html")
            connection.setRequestProperty("User-Agent", "LidSleepX/$APP_VERSION")
            if (connection.responseCode !in 200..299) return@runCatching null
            parseRelease(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    internal fun parseRelease(body: String): UpdateInfo? = runCatching {
        val release = RELEASE_LINK.findAll(body)
            .mapNotNull { match ->
                val path = match.groupValues[1]
                val rawTag = match.groupValues[2]
                val version = SemanticVersion.parse(rawTag) ?: return@mapNotNull null
                if (version.prerelease.isNotEmpty()) return@mapNotNull null
                StableRelease(
                    version = version,
                    releaseUrl = if (path.startsWith("http")) path else "https://github.com$path",
                )
            }
            .maxByOrNull { it.version }
            ?: return@runCatching null
        UpdateInfo(
            version = release.version.toString(),
            title = "LidSleepX ${release.version}",
            releaseUrl = release.releaseUrl,
        )
    }.getOrNull()

    private data class StableRelease(
        val version: SemanticVersion,
        val releaseUrl: String,
    )

    companion object {
        private val RELEASE_LINK = Regex(
            """href\s*=\s*["']([^"']*?/releases/tag/([^"'/?#]+))["']""",
            RegexOption.IGNORE_CASE,
        )
    }
}
