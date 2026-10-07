package com.wyz.lidsleepx.app.helper

import com.wyz.lidsleepx.core.APP_VERSION
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object HelperInstaller {
    fun install(executablePath: String, logger: (String) -> Unit = {}): Boolean {
        val script = installScript(executablePath, currentUid(), APP_VERSION)
        logger("Installing privileged helper")
        return runAppleScriptWithAdministrator(script)
    }

    internal fun installScript(
        executablePath: String,
        uid: String,
        version: String = APP_VERSION,
    ): String = buildString {
        val directory = escape(HelperProtocol.HELPER_DIRECTORY)
        val allowedUid = escape(HelperProtocol.ALLOWED_UID_PATH)
        val versionPath = escape(HelperProtocol.VERSION_PATH)
        val appPath = escape(HelperProtocol.APP_PATH_FILE)
        val plistPath = escape(HelperProtocol.PLIST_PATH)
        append("set -e\n")
        append("install -d -m 0755 '$directory'\n")
        append("chown root:wheel '$directory'\n")
        append("chmod 0755 '$directory'\n")
        append("printf '%s\\n' '${escape(uid)}' > '$allowedUid'\n")
        append("printf '%s\\n' '${escape(version)}' > '$versionPath'\n")
        append("printf '%s\\n' '${escape(executablePath)}' > '$appPath'\n")
        append("chown root:wheel '$allowedUid' '$versionPath' '$appPath'\n")
        append("chmod 0644 '$allowedUid' '$versionPath' '$appPath'\n")
        append("cat > '$plistPath' <<'PLIST'\n")
        append(plistText(executablePath)).append('\n')
        append("PLIST\n")
        append("chown root:wheel '$plistPath'\n")
        append("chmod 0644 '$plistPath'\n")
        append("/bin/launchctl bootout system '$plistPath' 2>/dev/null || true\n")
        append("/bin/launchctl bootstrap system '$plistPath'\n")
    }

    fun uninstall(logger: (String) -> Unit = {}): Boolean {
        val script = buildString {
            append("set -e\n")
            append("/usr/bin/pmset -a disablesleep 0 || true\n")
            append("/bin/launchctl bootout system '${escape(HelperProtocol.PLIST_PATH)}' 2>/dev/null || true\n")
            append("rm -f '${escape(HelperProtocol.PLIST_PATH)}'\n")
            append("rm -rf '${escape(HelperProtocol.HELPER_DIRECTORY)}'\n")
        }
        logger("Uninstalling privileged helper")
        return runAppleScriptWithAdministrator(script)
    }

    fun plistText(executablePath: String): String = """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        |<plist version="1.0">
        |<dict>
        |    <key>Label</key>
        |    <string>com.wyz.lidsleepx.helper</string>
        |    <key>ProgramArguments</key>
        |    <array>
        |        <string>${xmlEscape(executablePath)}</string>
        |        <string>--helper</string>
        |    </array>
        |    <key>RunAtLoad</key>
        |    <true/>
        |    <key>KeepAlive</key>
        |    <true/>
        |    <key>StandardOutPath</key>
        |    <string>${xmlEscape(HelperProtocol.LOG_PATH)}</string>
        |    <key>StandardErrorPath</key>
        |    <string>${xmlEscape(HelperProtocol.LOG_PATH)}</string>
        |</dict>
        |</plist>
    """.trimMargin()

    private fun runAppleScriptWithAdministrator(shellScript: String): Boolean {
        val process = ProcessBuilder(
            "/usr/bin/osascript",
            "-e",
            "do shell script \"${appleScriptEscape(shellScript)}\" with administrator privileges",
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        if (exit != 0) {
            System.err.println(output)
            return false
        }
        return true
    }

    private fun currentUid(): String =
        ProcessBuilder("/usr/bin/id", "-u").start().inputStream.bufferedReader().readText().trim()

    private fun escape(value: String): String = value.replace("'", "'\\''")
    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun appleScriptEscape(value: String): String = buildString {
        for (character in value) {
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> {}
                else -> append(character)
            }
        }
    }
}
