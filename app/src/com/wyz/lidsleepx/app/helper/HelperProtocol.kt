package com.wyz.lidsleepx.app.helper

import com.wyz.lidsleepx.core.APP_VERSION
import com.wyz.lidsleepx.core.HelperReply
import com.wyz.lidsleepx.core.HelperRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object HelperProtocol {
    const val PROTOCOL_VERSION = "1"
    const val SOCKET_PATH = "/Library/Application Support/LidSleepX/helper.sock"
    const val HELPER_DIRECTORY = "/Library/Application Support/LidSleepX"
    const val ALLOWED_UID_PATH = "$HELPER_DIRECTORY/allowed_uid"
    const val VERSION_PATH = "$HELPER_DIRECTORY/version"
    const val APP_PATH_FILE = "$HELPER_DIRECTORY/LidSleepX.app"
    const val LOG_PATH = "$HELPER_DIRECTORY/helper.log"
    const val PLIST_PATH = "/Library/LaunchDaemons/com.wyz.lidsleepx.helper.plist"
    val allowedCommands = setOf("version", "setDisableSleep")
    private val json = Json { ignoreUnknownKeys = true }

    fun decodeRequest(line: String): HelperRequest? = runCatching {
        json.decodeFromString<HelperRequest>(line)
    }.getOrNull()

    fun encodeRequest(request: HelperRequest): String = json.encodeToString(request)
    fun encodeReply(reply: HelperReply): String = json.encodeToString(reply)

    fun decodeReply(line: String): HelperReply? = runCatching {
        json.decodeFromString<HelperReply>(line)
    }.getOrNull()

    fun success(requestId: String, result: Map<String, String> = emptyMap()) =
        HelperReply(PROTOCOL_VERSION, requestId, true, result = result)

    fun failure(requestId: String, message: String) =
        HelperReply(PROTOCOL_VERSION, requestId, false, error = message)

    fun validate(request: HelperRequest): String? {
        if (request.version != PROTOCOL_VERSION) return "Unsupported protocol version"
        if (request.requestId.isBlank()) return "Missing requestId"
        if (request.command !in allowedCommands) return "Command is not allowed"
        if (request.command == "setDisableSleep") {
            val value = request.payload["disabled"] ?: return "Missing disabled value"
            if (value !in setOf("true", "false")) return "Invalid disabled value"
        }
        return null
    }

    fun versionReply(requestId: String) = success(requestId, mapOf("version" to APP_VERSION))
}
