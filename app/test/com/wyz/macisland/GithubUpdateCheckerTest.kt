package com.wyz.macisland.app

import com.wyz.macisland.app.platform.GithubUpdateChecker
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GithubUpdateCheckerTest {
    private val release = """
        <html>
        <body>
        <a href="/wises231/kmp-MacIsland/releases/tag/v0.2.0">MacIsland 0.2.0</a>
        <a href="/wises231/kmp-MacIsland/releases/tag/v0.1.0">MacIsland 0.1.0</a>
        <a href="/wises231/kmp-MacIsland/releases/tag/v0.3.0-beta.1">Beta</a>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun `latest release parses stable metadata`() {
        val checker = GithubUpdateChecker(connectionFactory = { FakeConnection(200, release) })
        val info = checker.latestRelease()
        assertEquals("0.2.0", info?.version)
        assertEquals("MacIsland 0.2.0", info?.title)
        assertEquals("https://github.com/wises231/kmp-MacIsland/releases/tag/v0.2.0", info?.releaseUrl)
    }

    @Test
    fun `latest release ignores prerelease tags and chooses the highest stable version`() {
        val body = """
            <a href="https://github.com/wises231/kmp-MacIsland/releases/tag/v0.1.9">old</a>
            <a href="/wises231/kmp-MacIsland/releases/tag/v0.4.0-rc.1">candidate</a>
            <a href="/wises231/kmp-MacIsland/releases/tag/v0.3.0">new</a>
        """.trimIndent()
        val info = GithubUpdateChecker().parseRelease(body)
        assertEquals("0.3.0", info?.version)
        assertEquals("https://github.com/wises231/kmp-MacIsland/releases/tag/v0.3.0", info?.releaseUrl)
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
        assertNull(checker.parseRelease("not a release page"))
        assertNull(checker.parseRelease("<a href=\"/releases/tag/not-a-version\">bad</a>"))
    }
}

private class FakeConnection(private val status: Int, private val body: String) :
    HttpURLConnection(URI("https://github.com/wises231/kmp-MacIsland/releases").toURL()) {
    override fun connect() {}
    override fun disconnect() {}
    override fun usingProxy(): Boolean = false
    override fun getResponseCode(): Int = status
    override fun getInputStream(): InputStream = body.byteInputStream()
}
