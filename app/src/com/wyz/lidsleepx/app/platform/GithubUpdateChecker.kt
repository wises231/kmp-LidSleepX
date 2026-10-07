package com.wyz.lidsleepx.app.platform

import com.wyz.lidsleepx.core.UpdateChecker
import com.wyz.lidsleepx.core.UpdateInfo
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GithubUpdateChecker(
    private val endpoint: String = "https://api.github.com/repos/wises231/kmp-LidSleepX/releases/latest",
    private val connectionFactory: (String) -> HttpURLConnection = { value ->
        URI(value).toURL().openConnection() as HttpURLConnection
    },
) : UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }

    override fun latestRelease(): UpdateInfo? = runCatching {
        val connection = connectionFactory(endpoint)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "LidSleepX/0.1.0")
            if (connection.responseCode !in 200..299) return@runCatching null
            parseRelease(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    internal fun parseRelease(body: String): UpdateInfo? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
        val url = root["html_url"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
        UpdateInfo(
            version = tag.removePrefix("v").removePrefix("V"),
            title = root["name"]?.jsonPrimitive?.contentOrNull ?: tag,
            releaseUrl = url,
            publishedAt = root["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }.getOrNull()
}
