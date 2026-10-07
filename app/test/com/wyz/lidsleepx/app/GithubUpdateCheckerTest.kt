package com.wyz.lidsleepx.app

import com.wyz.lidsleepx.app.platform.GithubUpdateChecker
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GithubUpdateCheckerTest {
    private val release = """
        {
          "tag_name": "v0.2.0",
          "name": "LidSleepX 0.2.0",
          "html_url": "https://github.com/wises231/kmp-LidSleepX/releases/tag/v0.2.0",
          "published_at": "2026-01-01T00:00:00Z"
        }
    """.trimIndent()

    @Test
    fun `latest release parses stable metadata`() {
        val checker = GithubUpdateChecker(connectionFactory = { FakeConnection(200, release) })
        val info = checker.latestRelease()
        assertEquals("0.2.0", info?.version)
        assertEquals("LidSleepX 0.2.0", info?.title)
        assertEquals("https://github.com/wises231/kmp-LidSleepX/releases/tag/v0.2.0", info?.releaseUrl)
        assertEquals("2026-01-01T00:00:00Z", info?.publishedAt)
    }

    @Test
    fun `rate limited response returns null`() {
        val checker = GithubUpdateChecker(connectionFactory = { FakeConnection(403, "") })
        assertNull(checker.latestRelease())
    }

    @Test
    fun `network failure returns null`() {
        val checker = GithubUpdateChecker(connectionFactory = { error("offline") })
        assertNull(checker.latestRelease())
    }

    @Test
    fun `malformed body returns null`() {
        val checker = GithubUpdateChecker()
        assertNull(checker.parseRelease("not json"))
        assertNull(checker.parseRelease("{\"name\":\"missing tag\"}"))
    }
}

private class FakeConnection(private val status: Int, private val body: String) :
    HttpURLConnection(URI("https://api.github.com/repos/wises231/kmp-LidSleepX/releases/latest").toURL()) {
    override fun connect() {}
    override fun disconnect() {}
    override fun usingProxy(): Boolean = false
    override fun getResponseCode(): Int = status
    override fun getInputStream(): InputStream = body.byteInputStream()
}
