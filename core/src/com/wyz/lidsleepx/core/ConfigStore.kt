package com.wyz.lidsleepx.core

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class ConfigStore(
    val path: Path = defaultConfigPath(),
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun load(): AppConfig {
        if (!Files.exists(path)) {
            val config = AppConfig()
            save(config)
            return config
        }
        return runCatching {
            val root = json.parseToJsonElement(Files.readString(path, StandardCharsets.UTF_8)) as? JsonObject
                ?: return@runCatching AppConfig()
            decode(root)
        }.getOrElse { AppConfig() }
    }

    fun save(config: AppConfig) {
        val normalized = config.normalized()
        Files.createDirectories(path.parent)
        val existing = runCatching {
            json.parseToJsonElement(Files.readString(path, StandardCharsets.UTF_8)) as? JsonObject
        }.getOrNull() ?: JsonObject(emptyMap())
        val merged = JsonObject(existing.toMutableMap().apply {
            putAll(json.encodeToJsonElement(AppConfig.serializer(), normalized) as JsonObject)
        })
        val temporary = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(temporary, json.encodeToString(JsonObject.serializer(), merged), StandardCharsets.UTF_8)
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun clear(): Boolean = runCatching {
        Files.deleteIfExists(path)
        Files.deleteIfExists(path.resolveSibling(path.fileName.toString() + ".tmp"))
    }.isSuccess

    private fun decode(root: JsonObject): AppConfig {
        val defaults = AppConfig()
        fun bool(name: String, fallback: Boolean): Boolean =
            root[name]?.jsonPrimitive?.booleanOrNull ?: fallback
        fun int(name: String, fallback: Int): Int =
            root[name]?.jsonPrimitive?.intOrNull ?: fallback
        fun long(name: String, fallback: Long): Long =
            root[name]?.jsonPrimitive?.longOrNull ?: fallback
        fun string(name: String, fallback: String): String =
            root[name]?.jsonPrimitive?.contentOrNull ?: fallback
        fun nullableInt(name: String): Int? =
            root[name]?.jsonPrimitive?.intOrNull

        return AppConfig(
            schemaVersion = int("schemaVersion", defaults.schemaVersion),
            language = string("language", defaults.language),
            enabled = bool("enabled", defaults.enabled),
            launchAtLogin = bool("launchAtLogin", defaults.launchAtLogin),
            lowBatteryCapacitySleep = bool("lowBatteryCapacitySleep", defaults.lowBatteryCapacitySleep),
            lowBatteryCapacity = int("lowBatteryCapacity", defaults.lowBatteryCapacity),
            lowTimeRemainingMinutes = int("lowTimeRemaining", defaults.lowTimeRemainingMinutes),
            disableIdleSleepInCharging = bool("disableIdleSleepInCharging", defaults.disableIdleSleepInCharging),
            disableLidSleepInCharging = bool("disableLidSleepInCharging", defaults.disableLidSleepInCharging),
            disableLidSleepOnBattery = bool("disableLidSleepOnBattery", defaults.disableLidSleepOnBattery),
            darkWakeAwarenessEnabled = bool("darkWakeAwarenessEnabled", defaults.darkWakeAwarenessEnabled),
            lidSleepImmediateOnClose = bool("lidSleepImmediateOnClose", defaults.lidSleepImmediateOnClose),
            notificationsEnabled = bool("notificationsEnabled", defaults.notificationsEnabled),
            updateCheckEnabled = bool("updateCheckEnabled", defaults.updateCheckEnabled),
            updateCheckIntervalHours = int("updateCheckIntervalHours", defaults.updateCheckIntervalHours),
            lastUpdateCheckEpochSeconds = long("lastUpdateCheckEpochSeconds", defaults.lastUpdateCheckEpochSeconds),
            hibernateMode = nullableInt("hibernateMode")?.takeIf { it in SUPPORTED_HIBERNATE_MODES },
            firstRun = bool("firstRun", defaults.firstRun),
        ).normalized()
    }

    companion object {
        fun defaultConfigPath(): Path {
            val home = System.getProperty("user.home")
            return Path.of(home, "Library", "Application Support", APP_ID, "config.json")
        }
    }
}
