package com.wyz.lidsleepx.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionAndConfigTest {
    @Test
    fun `semantic version comparison handles releases and prereleases`() {
        assertTrue(isNewerVersion("1.2.0", "1.1.9"))
        assertTrue(isNewerVersion("1.0.1", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0-beta.1", "1.0.0"))
        assertFalse(isStableVersion("1.0.0-beta.1"))
        assertTrue(isStableVersion("v1.0.0"))
    }

    @Test
    fun `config defaults are written and loaded`() {
        val directory = Files.createTempDirectory("lidsleepx-config")
        val path = directory.resolve("config.json")
        val store = ConfigStore(path)
        val defaults = store.load()
        assertTrue(defaults.enabled)
        assertEquals(6, defaults.lowBatteryCapacity)
        assertEquals(10, defaults.lowTimeRemainingMinutes)
        assertEquals(null, defaults.hibernateMode)
        assertTrue(Files.exists(path))
    }

    @Test
    fun `invalid config values fall back and unknown fields survive save`() {
        val directory = Files.createTempDirectory("lidsleepx-config")
        val path = directory.resolve("config.json")
        Files.writeString(
            path,
            """{"lowBatteryCapacity":999,"lowTimeRemaining":"bad","updateCheckIntervalHours":0,"unknown":{"keep":true}}""",
        )
        val store = ConfigStore(path)
        val loaded = store.load().copy(enabled = false)
        assertEquals(100, loaded.lowBatteryCapacity)
        assertEquals(10, loaded.lowTimeRemainingMinutes)
        assertEquals(1, loaded.updateCheckIntervalHours)
        store.save(loaded)
        val text = Files.readString(path)
        assertTrue(text.contains("\"unknown\""))
        assertTrue(text.contains("\"keep\""))
    }

    @Test
    fun `config keeps supported hibernate modes and rejects other values`() {
        assertEquals(25, AppConfig(hibernateMode = 25).normalized().hibernateMode)
        assertEquals(null, AppConfig(hibernateMode = 7).normalized().hibernateMode)
    }

    @Test
    fun `clear removes the configuration file`() {
        val directory = Files.createTempDirectory("lidsleepx-config")
        val path = directory.resolve("config.json")
        val store = ConfigStore(path)
        store.save(AppConfig())
        assertTrue(Files.exists(path))
        assertTrue(store.clear())
        assertFalse(Files.exists(path))
        assertTrue(store.load().enabled)
    }

    @Test
    fun `update policy ignores prereleases and respects interval`() {
        val config = AppConfig(lastUpdateCheckEpochSeconds = 1_000, updateCheckIntervalHours = 24)
        assertFalse(UpdatePolicy.shouldCheck(config, 1_000 + 23 * 3_600))
        assertTrue(UpdatePolicy.shouldCheck(config, 1_000 + 24 * 3_600))
        val prerelease = UpdateInfo("2.0.0-beta.1", "beta", "https://example.com")
        val stable = UpdateInfo("2.0.0", "stable", "https://example.com")
        assertEquals(null, UpdatePolicy.selectUpdate(prerelease, "1.0.0"))
        assertEquals(stable, UpdatePolicy.selectUpdate(stable, "1.0.0"))
    }
}
