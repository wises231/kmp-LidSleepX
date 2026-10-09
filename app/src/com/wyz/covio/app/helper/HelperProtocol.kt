package com.wyz.covio.app.helper

import com.wyz.covio.core.APP_VERSION
import com.wyz.covio.core.HelperReply
import com.wyz.covio.core.HelperRequest
import com.wyz.covio.core.SUPPORTED_HIBERNATE_MODES
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object HelperProtocol {
    const val PROTOCOL_VERSION = "1"
    const val SOCKET_PATH = "/Library/Application Support/Covio/helper.sock"
    const val HELPER_DIRECTORY = "/Library/Application Support/Covio"
    const val ALLOWED_UID_PATH = "$HELPER_DIRECTORY/allowed_uid"
    const val VERSION_PATH = "$HELPER_DIRECTORY/version"
    const val APP_PATH_FILE = "$HELPER_DIRECTORY/Covio.app"
    const val LOG_PATH = "$HELPER_DIRECTORY/helper.log"
    const val PLIST_PATH = "/Library/LaunchDaemons/com.wyz.covio.helper.plist"
    val allowedCommands = setOf("version", "setDisableSleep", "setHibernateMode")
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
        if (request.command == "setHibernateMode") {
            val mode = request.payload["mode"]?.toIntOrNull() ?: return "Missing hibernate mode"
            if (mode !in SUPPORTED_HIBERNATE_MODES) return "Invalid hibernate mode"
        }
        return null
    }

    fun versionReply(requestId: String) = success(requestId, mapOf("version" to APP_VERSION))
}
